# Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.
#
# Kaynak rafları. Puan zenginleştirmesi yalnız `aggregate_new`'de vardı: Gözat
# ekranı bu uçtan beslendiği için kartlarda yıldız hiç görünmüyordu (Dean:
# "gözatta yıldızlar yok") — kart kodu hazırdı, veri gelmiyordu.
#
# Puan CACHE'TEN okunur; eksikler arka planda doldurulur ve sonraki açılışta
# görünür. İstek anında TMDB araması yapılsaydı raf dakikalarca açılmazdı.

import asyncio

from Core   import Request
from .      import api_v1_router, api_v1_global_message
from ..Libs import fuck_dmca, get_client_headers

from Public.Home.Routers.tmdb import rating_for

# Aynı anda kaç TMDB araması — `aggregate_new` ile aynı ölçü.
_ES_ZAMANLI = 4
_doldurma: set[str] = set()


async def _puanlari_doldur(basliklar: list[str]) -> None:
    kilit = asyncio.Semaphore(_ES_ZAMANLI)

    async def tek(baslik: str) -> None:
        async with kilit:
            await rating_for(baslik, fetch=True)

    try:
        await asyncio.gather(*(tek(b) for b in basliklar), return_exceptions=True)
    finally:
        _doldurma.difference_update(basliklar)


@api_v1_router.get("/get_main_page")
async def get_main_page(request: Request):
    result = await fuck_dmca("/get_main_page", params=request.state.veri, client_headers=get_client_headers(request))

    if isinstance(result, list):
        eksik: list[str] = []
        for item in result:
            if not isinstance(item, dict):
                continue
            baslik = item.get("title") or ""
            puan   = await rating_for(baslik)
            if puan is not None:
                item["rating"] = puan
            elif baslik and baslik not in _doldurma:
                eksik.append(baslik)

        if eksik:
            _doldurma.update(eksik)
            asyncio.create_task(_puanlari_doldur(eksik))

    return {**api_v1_global_message, "result": result}
