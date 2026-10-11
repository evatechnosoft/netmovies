# NetMovies — Çocuk ekranı içeriği (/tv?profil=cocuk).
#
# TV çerez/parola taşımaz (bkz. client_config); burada YALNIZ panelde izin verilen
# kanalların süzülmüş videoları ve onaylı çizgi filmler döner — başka raf yok.

from Core import Request
from .    import api_v1_router, api_v1_global_message

from Public.Home.Libs import admin_config, cocuk as cocuk_lib


@api_v1_router.get("/cocuk")
async def cocuk(request: Request):
    return {**api_v1_global_message, "result": {"raflar": await cocuk_lib.raflar(admin_config.load_config())}}
