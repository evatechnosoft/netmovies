# Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

import asyncio

from urllib.parse import unquote_plus

from CLI    import konsol
from Core   import Request
from .      import api_v1_router, api_v1_global_message
from ..Libs import fuck_dmca, get_client_headers, source_score

from Public.Home.Libs import admin_config

# Ön-ısıtma görevleri: referans tutulmazsa GC çalışan Task'ı iptal eder.
_isitma_gorevleri: set[asyncio.Task] = set()

# Kurtarma aramasında kaynak başına tavan — biri asılırsa detay ekranı beklemesin.
_KURTARMA_TIMEOUT = 8.0


async def _isit(params: dict, client_headers: dict):
    """Detay ekranı açılır açılmaz çözümlemeyi arka planda başlatır.

    Kullanıcı "oynat"a bastığında beklediği 1.8–7.7 sn'lik çözümleme burada,
    o daha afişe bakarken yapılıyor; sonuç fuck_dmca cache'ine düşer (180 sn).
    """
    try:
        await fuck_dmca("/resolve_sources", params=params, timeout=25.0, client_headers=client_headers)
    except Exception as hata:
        konsol.log(f"[yellow]ön-ısıtma atlandı:[/] {type(hata).__name__}")


def _kullanilabilir(result, tip: str = "") -> bool:
    """Detay gerçekten açılabilir mi?

    Sağlayıcı ölü domain'de 500 döndüğünde `result` None gelir. Dizi sayfası
    yerine BÖLÜM sayfası kaydedilmişse (DiziPal listeleri `/2-sezon/1-bolum`
    adresini saklıyor) istek başarılı olur ama bölüm listesi boş döner — iki
    durumda da kullanıcı "bulunamadı" görüyor.
    """
    if not isinstance(result, dict):
        return False
    if result.get("episodes"):
        return True
    # Dizi olduğu KAYITTAN biliniyorsa bölümsüz detay çürüktür: DiziPal listeleri
    # `/2-sezon/1-bolum` adresini saklıyor, o adres bölüm listesi olmayan (çoğu
    # zaman bambaşka içeriğe ait) tek bir sayfa döndürüyor.
    if tip == "serie":
        return False
    # Film sayfası: bölüm listesi yok ama oynatılacak bir adres var.
    return bool(result.get("url")) and not result.get("is_series")


async def _kurtar(baslik: str, dislanan: str, tip: str, client_headers: dict):
    """Kayıtlı adres çürüdüğünde aynı içeriği BAŞLIKTAN yeniden bulur.

    İzlenecek/takip listeleri sağlayıcı + adres anlık görüntüsü tutuyor. Sağlayıcı
    domain değiştirince (SezonlukDizi öldü, HDFilmCehennemi .now -> .land) kayıt
    kalıcı olarak açılmaz hale geliyordu. Puanı yüksek sağlayıcıdan başlayarak
    aranır, ilk açılabilen detay döner.
    """
    if not baslik:
        return None

    cfg   = admin_config.load_config()
    gizli = set(cfg["hidden_providers"]) | set(cfg["adult_providers"]) | {dislanan}
    adlar = await fuck_dmca("/get_plugin_names", client_headers=client_headers)
    adlar = [ad for ad in (adlar or []) if ad not in gizli]

    puanlar = source_score.puanlar()
    adlar.sort(key=lambda ad: -puanlar.get(ad, 0.0))

    async def ara(ad: str):
        try:
            return await asyncio.wait_for(
                fuck_dmca("/search", params={"plugin": ad, "query": baslik}, client_headers=client_headers),
                timeout = _KURTARMA_TIMEOUT,
            )
        except Exception:
            return []

    gruplar = await asyncio.gather(*(ara(ad) for ad in adlar), return_exceptions=True)

    # Alakasız eşleşme kurtarma değildir: "R.J. Decker" araması bir kaynakta
    # "Abi" dizisini döndürüyordu. Aramayı yok sayan kaynakları aynı süzgeç eler.
    from .search_all import _alakali  # geç içe aktarma: döngüsel import'u önler

    for ad, grup in zip(adlar, gruplar):
        adaylar = _alakali(
            [{**o, "plugin": ad} for o in (grup if isinstance(grup, list) else []) if isinstance(o, dict)],
            baslik,
        )
        for oge in adaylar:
            if not oge.get("url"):
                continue
            try:
                detay = await asyncio.wait_for(
                    fuck_dmca(
                        "/load_item",
                        # Adres HAM gider: arama sonucu quote_plus KODLU gelir, httpx bir kez
                        # daha kodlar ve motor %253A görüp 500 döner.
                        params         = {"plugin": ad, "encoded_url": unquote_plus(str(oge["url"]))},
                        client_headers = client_headers,
                    ),
                    timeout = _KURTARMA_TIMEOUT,
                )
            except Exception:
                continue
            if _kullanilabilir(detay, tip):
                konsol.log(f"[green]load_item kurtarıldı:[/] {baslik} · {dislanan} -> {ad}")
                return detay
    return None


@api_v1_router.get("/load_item")
async def load_item(request:Request):
    veri           = dict(request.state.veri or {})
    client_headers = get_client_headers(request)
    baslik         = str(veri.pop("title", "") or "").strip()
    tip            = str(veri.pop("type", "") or "").strip()
    # Sağlayıcı hatası burada YUTULUR: `fuck_dmca` ölü domain'de ProviderRequestError
    # fırlatıyor, o da hata zarfına dönüşüp istemciye "bulunamadı" olarak düşüyordu —
    # kurtarma hiç çalışamıyordu. Hata artık "kullanılamaz detay" demek.
    try:
        result = await fuck_dmca("/load_item", params=veri, client_headers=client_headers)
    except Exception as hata:
        if not baslik:
            raise
        konsol.log(f"[yellow]load_item düştü, kurtarmaya geçiliyor:[/] {type(hata).__name__}")
        result = None

    if baslik and not _kullanilabilir(result, tip):
        kurtarma = await _kurtar(baslik, str(veri.get("plugin") or ""), tip, client_headers)
        if kurtarma is not None:
            result = kurtarma

    # Dizide hangi bölümün açılacağı belli değil; film sayfası (bölüm listesi yok)
    # doğrudan oynatılacağı için ısıtmaya değer.
    if isinstance(result, dict) and not result.get("episodes") and veri.get("encoded_url"):
        gorev = asyncio.create_task(_isit(
            {"plugin": veri.get("plugin"), "encoded_url": veri["encoded_url"], "mode": "fast"},
            client_headers,
        ))
        _isitma_gorevleri.add(gorev)
        gorev.add_done_callback(_isitma_gorevleri.discard)

    return {**api_v1_global_message, "result": result}
