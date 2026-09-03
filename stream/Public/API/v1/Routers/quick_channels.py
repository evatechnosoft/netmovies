# NetMovies — Canlı kanal listesi (client-facing proxy)
# Engine'in /api/v1/quick_channels ucunu istemcilere (TV client vb.) açar.
# Web ana sayfası bunu server-side çekiyordu; native istemcilerde uç yoktu.

from Core   import Request
from .      import api_v1_router, api_v1_global_message
from ..Libs import fuck_dmca, get_client_headers

@api_v1_router.get("/quick_channels")
async def quick_channels(request: Request):
    result = await fuck_dmca("/quick_channels", params=request.state.veri, client_headers=get_client_headers(request))
    return {**api_v1_global_message, "result": result}
