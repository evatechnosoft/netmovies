# NetMovies — hazır kaydın dosyaları (yerel diskten, internetsiz).
#
# /proxy altında: oynatıcı auth taşımaz ve TV istemcisi yalnız /proxy/ yollarını
# o anki sunucuya yönlendirir (Network.playerClient).

from fastapi           import Response
from fastapi.responses import FileResponse
from .                 import proxy_router
from Public.API.v1.Libs import kayit

_TUR = {
    ".m3u8" : "application/vnd.apple.mpegurl",
    ".ts"   : "video/mp2t",
    ".m4s"  : "video/iso.segment",
    ".mp4"  : "video/mp4",
    ".bin"  : "application/octet-stream",
}


@proxy_router.get("/kayit/{kid}/{yol:path}")
async def kayit_dosyasi(kid: str, yol: str):
    hedef = kayit.dosya(kid, yol)
    if hedef is None:
        return Response(status_code=404)
    kayit.izlendi_isaretle(kid, yol)
    return FileResponse(hedef, media_type=_TUR.get(hedef.suffix, "application/octet-stream"),
                        headers={"Access-Control-Allow-Origin": "*"})
