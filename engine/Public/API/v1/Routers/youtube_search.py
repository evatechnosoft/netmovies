# NetMovies — YouTube araması (yt-dlp `ytsearch`).

from Core import Request, JSONResponse
from .    import api_v1_router, api_v1_global_message

from ..Libs.ytdlp_service import ytdlp_search


@api_v1_router.get("/youtube-search")
async def youtube_search(request: Request):
    istek = request.state.veri or {}
    query = str(istek.get("query", "")).strip()
    if not query:
        return JSONResponse(status_code=400, content={"hata": "query parametresi gerekli"})

    limit = int(istek.get("limit", 20) or 20)
    return {**api_v1_global_message, "result": await ytdlp_search(query, limit)}
