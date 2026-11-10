# DDizi parçalı bölümün birleşik HLS playlist'i (bkz. Plugins/__ddizi_parcali.py).
# Yalnız stream proxy'si çağırır (iç ağ).

from Core   import Request, Response, JSONResponse
from .      import api_v1_router
from ..Libs import plugin_manager


@api_v1_router.get("/ddizi_birlesik")
async def ddizi_birlesik(request: Request):
    url, tur = request.query_params.get("u"), request.query_params.get("t")
    if not url or tur not in ("m", "v", "a"):
        return JSONResponse(status_code=400, content={"hata": "u ve t=m|v|a gerekli"})
    try:
        metin = await plugin_manager.select_plugin("DDizi").birlesik(url, tur)
    except (ValueError, KeyError) as hata:
        return JSONResponse(status_code=404, content={"hata": str(hata)})
    return Response(content=metin, media_type="application/vnd.apple.mpegurl")
