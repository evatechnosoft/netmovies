# NetMovies — Ajanda: "bu hafta ne var, ne zaman".
#
# İki ayrı soru tek listede birleşir:
#   - Diziler: yayınlanacak BÖLÜMÜN tarihi. `discover/tv` yalnız diziyi bulur,
#     döndürdüğü `first_air_date` dizinin BAŞLANGIÇ tarihidir — ajandaya onu
#     yazmak yanlış olur. Bu yüzden her aday için detay çekilip
#     `next_episode_to_air` okunur; tarihi aralıkta olmayan aday düşer.
#   - Filmler: `movie/upcoming` (TR) zaten vizyon tarihiyle geliyor.
#
# Takip listesi ayrı bir uç (`/following`) ve kendi takvimini taşıyor; burada
# tekrarlanmaz.
#
#   GET /api/v1/agenda?view=week|month
#
# TMDB_API_KEY yoksa boş liste döner (hata değil): ajanda bir süs, sayfanın
# geri kalanı çalışmaya devam etsin.

from __future__ import annotations

import asyncio
import datetime
import os
import time

import httpx

from Core import Request
from .    import api_v1_router, api_v1_global_message
from ..Libs.ajanda_grup import gunlere_bol

_API_KEY   = os.getenv("TMDB_API_KEY", "").strip()
_DISCOVER  = "https://api.themoviedb.org/3/discover/tv"
_TV_DETAIL = "https://api.themoviedb.org/3/tv/{id}"
_UPCOMING  = "https://api.themoviedb.org/3/movie/upcoming"
_IMG_BASE  = "https://image.tmdb.org/t/p/w500"

# Takvim gün içinde değişmez; TMDB kotası ve açılış süresi için günde bir tazelenir.
_CACHE_TTL = 24 * 60 * 60
_cache: dict[str, tuple[float, list[dict]]] = {}

# Detay çekilecek dizi adayı sayısı: `discover` popülerlik sırasıyla döndüğü için
# ilk sayfa yeter, ama her aday bir istek demek — üst sınır konur.
_ADAY_SINIRI = 20

_client = httpx.AsyncClient(
    timeout = httpx.Timeout(connect=5.0, read=8.0, write=5.0, pool=5.0),
    limits  = httpx.Limits(max_connections=10, max_keepalive_connections=5),
)


def _aralik(view: str) -> tuple[datetime.date, datetime.date]:
    bugun = datetime.date.today()
    return bugun, bugun + datetime.timedelta(days=30 if view == "month" else 7)


async def _json(url: str, params: dict) -> dict:
    try:
        yanit = await _client.get(url, params={**params, "api_key": _API_KEY})
        return yanit.json() if yanit.status_code == 200 else {}
    except Exception:
        return {}


async def _dizi_bolumu(dizi_id: int, bas: datetime.date, son: datetime.date) -> dict | None:
    """Dizinin sıradaki bölümü aralıktaysa ajanda satırı üretir."""
    detay = await _json(_TV_DETAIL.format(id=dizi_id), {"language": "tr-TR"})
    bolum = detay.get("next_episode_to_air") or {}
    tarih = bolum.get("air_date")
    if not tarih:
        return None

    try:
        gun = datetime.date.fromisoformat(tarih)
    except ValueError:
        return None
    if not (bas <= gun <= son):
        return None

    poster = detay.get("poster_path")
    return {
        "tur"     : "dizi",
        "baslik"  : detay.get("name") or "",
        "tarih"   : tarih,
        "poster"  : f"{_IMG_BASE}{poster}" if poster else "",
        "bolum"   : f"{bolum.get('season_number', '?')}. sezon {bolum.get('episode_number', '?')}. bölüm",
        "ozet"    : (bolum.get("overview") or detay.get("overview") or "")[:300],
        "puan"    : round(float(detay.get("vote_average") or 0), 1),
    }


async def _diziler(bas: datetime.date, son: datetime.date) -> list[dict]:
    liste = await _json(_DISCOVER, {
        "language"           : "tr-TR",
        "with_origin_country": "TR",
        "air_date.gte"       : str(bas),
        "air_date.lte"       : str(son),
        "sort_by"            : "popularity.desc",
    })
    adaylar = [x.get("id") for x in (liste.get("results") or [])[:_ADAY_SINIRI] if x.get("id")]
    satirlar = await asyncio.gather(*(_dizi_bolumu(i, bas, son) for i in adaylar), return_exceptions=True)
    return [s for s in satirlar if isinstance(s, dict)]


async def _filmler(bas: datetime.date, son: datetime.date) -> list[dict]:
    liste = await _json(_UPCOMING, {"language": "tr-TR", "region": "TR"})

    satirlar: list[dict] = []
    for film in liste.get("results") or []:
        tarih = film.get("release_date")
        if not tarih:
            continue
        try:
            gun = datetime.date.fromisoformat(tarih)
        except ValueError:
            continue
        if not (bas <= gun <= son):
            continue

        poster = film.get("poster_path")
        satirlar.append({
            "tur"    : "film",
            "baslik" : film.get("title") or "",
            "tarih"  : tarih,
            "poster" : f"{_IMG_BASE}{poster}" if poster else "",
            "bolum"  : "Vizyon",
            "ozet"   : (film.get("overview") or "")[:300],
            "puan"   : round(float(film.get("vote_average") or 0), 1),
        })
    return satirlar


async def agenda_verisi(view: str) -> dict:
    """Ajanda gövdesi. Hem `/api/v1/agenda` hem `/ajanda` sayfası bunu çağırır —
    sayfanın kendi kendine HTTP turu atmasına gerek yok."""
    view = "month" if str(view).lower() == "month" else "week"

    if not _API_KEY:
        return {"view": view, "toplam": 0, "gunler": [], "hata": "TMDB_API_KEY yok"}

    onbellek = _cache.get(view)
    if onbellek and onbellek[0] > time.monotonic():
        satirlar = onbellek[1]
    else:
        bas, son = _aralik(view)
        diziler, filmler = await asyncio.gather(_diziler(bas, son), _filmler(bas, son))
        satirlar = sorted(diziler + filmler, key=lambda s: (s["tarih"], s["baslik"]))
        _cache[view] = (time.monotonic() + _CACHE_TTL, satirlar)

    return {"view": view, "toplam": len(satirlar), "gunler": gunlere_bol(satirlar)}


@api_v1_router.get("/agenda")
async def agenda(request: Request):
    return {**api_v1_global_message, "result": await agenda_verisi((request.state.veri or {}).get("view", "week"))}
