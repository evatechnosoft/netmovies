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


def decorate(sources: list) -> list:
    """Dil kuralına göre sıralar ve her kaynağa `language` alanı ekler."""
    ordered = order_by_language(sources)
    for source in ordered:
        if isinstance(source, dict):
            rank = language_rank(source)
            source["language"] = {"rank": rank, "label": language_name(rank)}
    return ordered


@api_v1_router.get("/resolve_sources")
async def resolve_sources(request: Request):
    params = dict(request.state.veri or {})
    # Alternatif tarama ağır olabilir: engine'in kendi timeout'una alan bırak.
    timeout = 25.0 if params.get("mode") == "fast" else 60.0

    result = await fuck_dmca(
        "/resolve_sources",
        params         = params,
        timeout        = timeout,
        client_headers = get_client_headers(request),
    )

    if isinstance(result, dict):
        result["sources"] = decorate(result.get("sources") or [])
        first = result["sources"][0]["language"]["label"] if result["sources"] else "yok"
        konsol.log(
            f"[green]▶ resolve:[/] {params.get('plugin', '?')} · mod={params.get('mode', 'full')} · "
            f"{len(result['sources'])} kaynak · ilk sıra: {first}"
        )

    return {**api_v1_global_message, "result": result}
