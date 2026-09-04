# Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

from fastapi        import Request, Response
from .              import proxy_router
from ..Libs.helpers import prepare_request_headers, process_subtitle_content, CORS_HEADERS, shared_client, url_is_public
from ..Libs.proxy_token import validate_proxy_token

@proxy_router.get("/subtitle")
async def subtitle_proxy(request: Request, url: str, proxy_token: str = None, referer: str = None, user_agent: str = None):
    """Altyazı proxy endpoint'i"""
    if not proxy_token or not validate_proxy_token(proxy_token, url):
        return Response(status_code=403, content="Geçersiz veya süresi dolmuş proxy token")
    if not await url_is_public(url):
        return Response(status_code=403, content="Hedef adres proxy'lenemez")
    try:
        decoded_url     = url
        request_headers = prepare_request_headers(request, decoded_url, referer, user_agent)

        response = await shared_client.get(decoded_url, headers=request_headers)

        if response.status_code >= 400:
            return Response(
                content     = f"Altyazı hatası: {response.status_code}",
                status_code = response.status_code
            )

        processed_content = process_subtitle_content(
            response.content,
            response.headers.get("content-type", ""),
            decoded_url
        )

        return Response(
            content     = processed_content,
            status_code = 200,
            headers     = {"Content-Type": "text/vtt; charset=utf-8", **CORS_HEADERS},
            media_type  = "text/vtt"
        )

    except Exception:
        return Response(content="Altyazı alınamadı", status_code=502)
