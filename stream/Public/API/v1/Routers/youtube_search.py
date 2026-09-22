# NetMovies — YouTube araması: engine'in yt-dlp'sine delege.

from Core   import Request
from .      import api_v1_router, api_v1_global_message
from ..Libs import fuck_dmca, get_client_headers


@api_v1_router.get("/youtube_search")
async def youtube_search(request: Request):
    result = await fuck_dmca(
        "/youtube-search",
        params         = request.state.veri,
        timeout        = 30.0,
        client_headers = get_client_headers(request),
    )
    return {**api_v1_global_message, "result": result}
