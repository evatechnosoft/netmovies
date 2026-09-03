# NetMovies — oynatma kaynağı çözümleyicisi (istemcilerin TEK ucu).
#
# Engine zinciri kurar (seçili sağlayıcı → bölüm → alternatif sağlayıcılar),
# burada dil kuralı uygulanır ve her kaynağa okunur etiket eklenir. TV, telefon
# ve web aynı listeyi, aynı sırada, aynı etiketlerle görür — istemcide kural
# tekrarı yok.

from json           import dumps
from urllib.parse   import quote

from CLI            import konsol
from Core           import Request
from .              import api_v1_router, api_v1_global_message
from ..Libs         import fuck_dmca, get_client_headers
from ..Libs.language import language_name, language_rank, order_by_language
from Public.Proxy.Libs.proxy_token import issue_proxy_token


def decorate(sources: list) -> list:
    """Dil kuralına göre sıralar ve her kaynağa `language` alanı ekler."""
    ordered = order_by_language(sources)
    for source in ordered:
        if isinstance(source, dict):
            rank = language_rank(source)
            source["language"] = {"rank": rank, "label": language_name(rank)}
    return ordered


def route_through_proxy(sources: list, base_url: str) -> list:
    """Ek başlık isteyen kaynakları sunucu proxy'sine bağlar.

    Bazı oynatıcılar tek kullanımlık imza başlığı (X-Sp) istiyor; imza her istekte
    yeniden üretilmek zorunda olduğu için istemci doğrudan çalamaz. Ayrıca TV'nin
    ağı kaynak CDN'ine erişemeyebiliyor (ERR_CONNECTION_REFUSED). Proxy'den geçen
    akışta ikisi de sunucunun sorunu olur — istemci yalnız URL'i çalar.

    Ek başlık istemeyen kaynaklara dokunulmaz: gereksiz yere ev bağlantısı
    üzerinden trafik taşınmasın.
    """
    proxied = []
    for source in sources:
        if not isinstance(source, dict):
            continue

        extra = source.get("extra_headers") or {}
        url   = str(source.get("url") or "")
        if not extra or not url:
            proxied.append(source)
            continue

        token  = issue_proxy_token([url])
        params = [
            f"url={quote(url, safe='')}",
            # Segmentler normalde bant tasarrufu için doğrudan CDN'den çekilir; bu
            # kaynağın CDN'i Referer istiyor (Referer'sız 403) ve istemci onu
            # gönderemez. force_proxy ile segmentler de sunucudan geçer.
            "force_proxy=1",
            f"referer={quote(str(source.get('referer') or ''), safe='')}",
            f"user_agent={quote(str(source.get('user_agent') or ''), safe='')}",
            f"extra_headers={quote(dumps(extra, separators=(',', ':')), safe='')}",
            f"proxy_token={quote(token, safe='')}",
        ]
        source = {**source, "url": f"{base_url}/proxy/video?{'&'.join(params)}", "proxied": True}
        # İmza malzemesi istemciye gitmesin: artık sunucunun işi.
        source.pop("extra_headers", None)
        proxied.append(source)

    return proxied


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
        base_url = str(request.base_url).rstrip("/")
        result["sources"] = route_through_proxy(decorate(result.get("sources") or []), base_url)
        first = result["sources"][0]["language"]["label"] if result["sources"] else "yok"
        konsol.log(
            f"[green]▶ resolve:[/] {params.get('plugin', '?')} · mod={params.get('mode', 'full')} · "
            f"{len(result['sources'])} kaynak · ilk sıra: {first}"
        )

    return {**api_v1_global_message, "result": result}
