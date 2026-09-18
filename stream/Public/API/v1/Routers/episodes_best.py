# NetMovies — bir dizinin EN ZENGİN bölüm listesi.
#
# NEDEN: bölüm listesi, kartın geldiği sağlayıcınındır. O sağlayıcı eksik liste
# veriyorsa kullanıcı yeni bölümü hiç göremiyor. Ölçüm (18 Eylül):
#   Dead City → HDFilmCehennemi 7 · DiziMom 20 · Dizilla 16
#   Reacher   → HDFilmCehennemi 28 (son S4B4) · DiziMom 32 (S4B8) · Dizilla 32
# Dean üç kez bildirdi: "sezon 2'de 1 bölüm", "son bölüm S4B4 ama bu hafta S4B8".
#
# Bu uç OYNATICI AÇILIŞINDA çağrılmaz — tüm sağlayıcıları taramak ilk oynatmayı
# saniyelerce geciktirir. İstemci yalnız Bölümler sekmesi açılınca sorar.

from __future__ import annotations

import asyncio
from urllib.parse import unquote_plus

from Core import Request
from .    import api_v1_router, api_v1_global_message
from ..Libs import fuck_dmca, get_client_headers, source_score

from Public.Home.Libs import admin_config

from .search_all import _KAYNAK_TIMEOUT, _alakali, _varyantlar


def _cift_sayisi(episodes: list) -> int:
    """Listenin zenginliği: BENZERSİZ (sezon, bölüm) çifti sayısı.

    Ham uzunluk yanıltıcı: bazı sağlayıcılar aynı bölümü iki kez veriyor
    (kenar çubuğu sızıntısı, bkz. hafıza `episode-list-slug-leak`).
    """
    return len({
        (e.get("season"), e.get("episode"))
        for e in episodes or []
        if isinstance(e, dict) and e.get("episode") is not None
    })


def daha_zengin(cift: int, mevcut: int, puan: float, mevcut_puan: float) -> bool:
    """Aday liste mevcutun yerini alır mı?

    Daha çok bölüm her zaman kazanır. Eşitlikte kanıtlanmış sağlayıcı kazanır:
    iki liste aynı bölümleri veriyorsa oynayanı seçmek bekleme süresini kısaltır.
    Boş listeler hiçbir zaman kazanmaz.
    """
    if cift > mevcut:
        return True
    return cift == mevcut and cift > 0 and puan > mevcut_puan


async def _bolumler(plugin: str, ham_url: str, client_headers: dict) -> list:
    try:
        detay = await asyncio.wait_for(
            fuck_dmca(
                "/load_item",
                params         = {"plugin": plugin, "encoded_url": ham_url},
                client_headers = client_headers,
            ),
            timeout = _KAYNAK_TIMEOUT,
        )
    except Exception:
        return []
    return (detay or {}).get("episodes") or []


@api_v1_router.get("/episodes_best")
async def episodes_best(request: Request):
    veri   = request.state.veri or {}
    baslik = str(veri.get("title") or "").strip()
    plugin = str(veri.get("plugin") or "").strip()
    # Adres HAM tutulur: `fuck_dmca` parametreyi httpx ile kodluyor, kodlu
    # gönderilirse motor `%253A` görüp 500 dönüyor.
    adres  = unquote_plus(str(veri.get("encoded_url") or ""))

    basliklar = get_client_headers(request)
    # Mevcut kartın listesi taban: hiçbir aday daha zengin değilse bu döner.
    en_iyi = {
        "plugin"       : plugin,
        "encoded_url"  : adres,
        "episodes"     : await _bolumler(plugin, adres, basliklar) if plugin and adres else [],
    }
    en_iyi_cift = _cift_sayisi(en_iyi["episodes"])

    if not baslik:
        return {**api_v1_global_message, "result": {**en_iyi, "kaynak_sayisi": 1}}

    cfg   = admin_config.load_config()
    gizli = set(cfg["hidden_providers"]) | set(cfg["adult_providers"])
    adlar = [ad for ad in (await fuck_dmca("/get_plugin_names", client_headers=basliklar) or [])
             if ad not in gizli and ad != plugin]

    varyantlar = _varyantlar(baslik)

    async def aday(ad: str) -> list:
        for varyant in varyantlar:
            try:
                sonuc = await asyncio.wait_for(
                    fuck_dmca("/search", params={"plugin": ad, "query": varyant}, client_headers=basliklar),
                    timeout = _KAYNAK_TIMEOUT,
                )
            except Exception:
                return []
            ogeler = [{**o, "plugin": ad} for o in (sonuc or []) if isinstance(o, dict)]
            if ogeler:
                return ogeler
        return []

    gruplar = await asyncio.gather(*(aday(ad) for ad in adlar), return_exceptions=True)
    ogeler  = [o for g in gruplar if isinstance(g, list) for o in g]
    ogeler  = _alakali(admin_config.filter_aggregate_items(ogeler, cfg), baslik)

    # Sağlayıcı başına TEK aday: aynı kaynağın beş sonucunu tek tek açmak
    # taramayı katlar, ilki zaten en alakalısı (`_alakali` eşleşenleri öne alır).
    ilk_aday: dict[str, str] = {}
    for oge in ogeler:
        ad = oge.get("plugin") or ""
        if ad and ad not in ilk_aday:
            ilk_aday[ad] = unquote_plus(str(oge.get("url") or ""))

    listeler = await asyncio.gather(
        *(_bolumler(ad, url, basliklar) for ad, url in ilk_aday.items()),
        return_exceptions = True,
    )

    puanlar = source_score.puanlar()
    for (ad, url), liste in zip(ilk_aday.items(), listeler):
        if not isinstance(liste, list):
            continue
        cift = _cift_sayisi(liste)
        if daha_zengin(cift, en_iyi_cift, puanlar.get(ad, 0.0), puanlar.get(en_iyi["plugin"], 0.0)):
            en_iyi = {"plugin": ad, "encoded_url": url, "episodes": liste}
            en_iyi_cift = cift

    return {
        **api_v1_global_message,
        "result": {**en_iyi, "kaynak_sayisi": len(ilk_aday) + 1},
    }
