# NetMovies — Birleşik "Yeni Çıkanlar" (client-facing proxy)
# Engine'in /api/v1/aggregate_new'ini istemcilere (TV client vb.) açar.
# Web home bunu server-side çekiyordu; native client'lar için de gerek var.

from Core   import Request
from .      import api_v1_router, api_v1_global_message
from ..Libs import fuck_dmca, get_client_headers

import asyncio

from Public.Home.Libs   import admin_config
from Public.Home.Routers.tmdb import rating_for

# Aynı anda kaç TMDB araması. Sınırsız bırakılırsa 300+ eşzamanlı istek TMDB
# tarafından kısılır ve hiçbir puan gelmez.
_TMDB_ES_ZAMANLI = 8
_doldurma: set[str] = set()


async def _puanlari_doldur(basliklar: list[str]) -> None:
    """Eksik puanları arka planda TMDB'den çeker; sonraki listede görünürler."""
    kilit = asyncio.Semaphore(_TMDB_ES_ZAMANLI)

    async def tek(baslik: str) -> None:
        async with kilit:
            await rating_for(baslik, fetch=True)

    try:
        await asyncio.gather(*(tek(b) for b in basliklar), return_exceptions=True)
    finally:
        _doldurma.difference_update(basliklar)

@api_v1_router.get("/aggregate_new")
async def aggregate_new(request: Request):
    # Soğuk agregasyon yavaş olabilir (movie ~40s: bir kaynak ağır scrape). Timeout
    # cömert; ilk çağrı sonrası fuck_dmca cache'i (aşağıda _CACHE_TTL) anında döndürür.
    result = await fuck_dmca(
        "/aggregate_new",
        params         = request.state.veri,
        timeout        = 45.0,
        client_headers = get_client_headers(request),
    )

    # Yönetim paneli ayarları BURADA da uygulanmalı: gizli kaynak/kategori ve puan
    # eşiği yalnız web ana sayfasında süzülüyordu, native istemciler ham listeyi
    # alıyordu — panelde bir kaynağı kapatmak TV'de hiçbir şey değiştirmiyordu.
    items = (result or {}).get("items")
    if isinstance(items, list):
        suzulmus = admin_config.filter_aggregate_items(items)

        # Puan: yalnız cache'ten. Eksikler arka planda doldurulur — istek anında
        # 300+ TMDB araması yapılsaydı ana sayfa dakikalarca açılmazdı.
        eksik: list[str] = []
        for item in suzulmus:
            baslik = item.get("title") or ""
            puan   = await rating_for(baslik)
            if puan is not None:
                item["rating"] = puan
            elif baslik and baslik not in _doldurma:
                eksik.append(baslik)

        if eksik:
            _doldurma.update(eksik)
            asyncio.create_task(_puanlari_doldur(eksik))

        result = {**result, "items": suzulmus, "count": len(suzulmus)}

    return {**api_v1_global_message, "result": result}
