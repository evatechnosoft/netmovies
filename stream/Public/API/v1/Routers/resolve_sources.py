# NetMovies — oynatma kaynağı çözümleyicisi (istemcilerin TEK ucu).
#
# Engine zinciri kurar (seçili sağlayıcı → bölüm → alternatif sağlayıcılar),
# burada dil kuralı uygulanır ve her kaynağa okunur etiket eklenir. TV, telefon
# ve web aynı listeyi, aynı sırada, aynı etiketlerle görür — istemcide kural
# tekrarı yok.

from CLI            import konsol
from Core           import Request
from .              import api_v1_router, api_v1_global_message
from ..Libs         import fuck_dmca, get_client_headers
from ..Libs.language import language_name, language_rank, order_by_language
from ..Libs.source_proxy import route_through_proxy
from ..Libs         import source_score
from ..Libs.title_rescue import alternatif_basliklar


def decorate(sources: list) -> list:
    """Dil kuralına göre sıralar ve her kaynağa `language` alanı ekler."""
    ordered = order_by_language(sources)
    for source in ordered:
        if isinstance(source, dict):
            rank = language_rank(source)
            source["language"] = {"rank": rank, "label": language_name(rank)}
    return ordered


def _puanli_sira() -> str:
    """Engine'e verilecek tarama sırası: kanıtlanmış kaynak önde.

    Engine mekanizmayı (zinciri) tutar, hafıza burada: puan ve yıldız
    stream tarafındaki kalıcı veriden okunur. Veri yoksa boş döner ve
    engine kendi elle yazılmış listesine düşer.
    """
    puanlar = source_score.puanlar()
    try:
        from .prefs import _oku as _prefs_oku
        favoriler = _prefs_oku().get("fav_providers") or []
    except Exception:
        favoriler = []
    if not puanlar and not favoriler:
        return ""
    adaylar = sorted(set(list(puanlar.keys()) + list(favoriler)))
    return ",".join(source_score.sirala(adaylar, favoriler if isinstance(favoriler, list) else []))


@api_v1_router.get("/resolve_sources")
async def resolve_sources(request: Request):
    params = dict(request.state.veri or {})
    # Alternatif tarama ağır olabilir: engine'in kendi timeout'una alan bırak.
    timeout = 25.0 if params.get("mode") == "fast" else 60.0
    # Tarama sırası: engine'in sabit listesi yerine kanıta dayalı sıra.
    # Boşsa engine varsayılanını kullanır.
    sira = _puanli_sira()
    if sira:
        params["order"] = sira

    istemci_basliklari = get_client_headers(request)
    result = await fuck_dmca(
        "/resolve_sources",
        params         = params,
        timeout        = timeout,
        client_headers = istemci_basliklari,
    )

    # Zincirin TAMAMI boş döndüyse başlık kurtarma: sağlayıcı aramaları harfi
    # harfine çalışıyor, "the odyssey" sıfır sonuç veriyor. Kaybedecek bir şey
    # kalmadığında başlığın başka yazılışları denenir. Normal akışta hiç
    # çalışmaz — maliyeti yalnız zaten başarısız olmuş çözümlemede.
    if (
        isinstance(result, dict)
        and not (result.get("sources") or [])
        and params.get("mode") != "fast"
        and params.get("title")
    ):
        for aday in await alternatif_basliklar(str(params["title"])):
            konsol.log(f"[yellow]↻ başlık kurtarma:[/] '{params['title']}' → '{aday}'")
            kurtarma = await fuck_dmca(
                "/resolve_sources",
                params         = {**params, "title": aday},
                timeout        = timeout,
                client_headers = istemci_basliklari,
            )
            if isinstance(kurtarma, dict) and (kurtarma.get("sources") or []):
                kurtarma = {**kurtarma}
                kurtarma.setdefault("diagnostics", [])
                kurtarma["diagnostics"] = list(kurtarma["diagnostics"]) + [{
                    "level": "info", "stage": "kurtarma",
                    "message": f"başlık '{params['title']}' sonuç vermedi, '{aday}' ile bulundu",
                }]
                result = kurtarma
                break

    if isinstance(result, dict):
        # `fuck_dmca` sonucu 180sn cache'liyor ve AYNI nesneyi döndürüyor. Aşağıdaki
        # `result["sources"] = ...` ataması doğrudan cache'teki kaydı değiştiriyordu:
        # ikinci çağrı proxy URL'ini bir kez daha sarıyor, üçüncüsü bir kez daha —
        # `/proxy/video?url=…/proxy/video?url=…` ve 403. Kopya üzerinde çalışılır.
        result   = {**result}
        base_url = str(request.base_url).rstrip("/")
        result["sources"] = route_through_proxy(decorate(result.get("sources") or []), base_url)
        first = result["sources"][0]["language"]["label"] if result["sources"] else "yok"
        konsol.log(
            f"[green]▶ resolve:[/] {params.get('plugin', '?')} · mod={params.get('mode', 'full')} · "
            f"{len(result['sources'])} kaynak · ilk sıra: {first}"
        )

    return {**api_v1_global_message, "result": result}
