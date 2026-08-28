# NetMovies — Birleşik "Yeni Çıkanlar" (client-facing proxy)
# Engine'in /api/v1/aggregate_new'ini istemcilere (TV client vb.) açar.
# Web home bunu server-side çekiyordu; native client'lar için de gerek var.

from Core   import Request
from .      import api_v1_router, api_v1_global_message
from ..Libs import fuck_dmca, get_client_headers

@api_v1_router.get("/aggregate_new")
async def aggregate_new(request: Request):
    result = await fuck_dmca(
        "/aggregate_new",
        params         = request.state.veri,
        timeout        = 15.0,
        client_headers = get_client_headers(request),
    )
    return {**api_v1_global_message, "result": result}
