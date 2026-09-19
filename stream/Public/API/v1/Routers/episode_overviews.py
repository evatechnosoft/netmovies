# NetMovies — bölüm özetleri (TMDB).
#
# Sağlayıcı eklentileri bölüm için yalnız numara ve ad veriyor; özet hiçbir
# kaynakta yok. Oynatıcıdaki bölüm seçicinin sol önizlemesi bu yüzden dizi
# açıklamasını tekrarlıyordu — hangi bölüm seçilirse seçilsin aynı metin.
#
# TMDB sezon ucu (`/tv/{id}/season/{n}`) bölüm başına Türkçe özet veriyor
# (ölçüm: Neagley S1, tr-TR, 8/8 dolu). Türkçe boş kalırsa İngilizcesi dolduruyor.
#
# TMDB_API_KEY yoksa boş sözlük döner: önizleme eski hâline (dizi açıklaması)
# düşer, hata göstermez.

from __future__ import annotations

import os
import time

import httpx

from Core import Request
from .    import api_v1_router, api_v1_global_message

from Public.Home.Routers.tmdb import _clean_title

_API_KEY   = os.getenv("TMDB_API_KEY", "").strip()
_SEARCH_TV = "https://api.themoviedb.org/3/search/tv"
_SEASON    = "https://api.themoviedb.org/3/tv/{id}/season/{season}"
_IMG_BASE  = "https://image.tmdb.org/t/p/w500"

# Bölüm özeti yayınlandıktan sonra değişmez; sezon başına bir istek yeter.
_CACHE_TTL = 12 * 60 * 60
_cache: dict[str, tuple[float, dict]] = {}

_client = httpx.AsyncClient(
    timeout = httpx.Timeout(connect=5.0, read=8.0, write=5.0, pool=5.0),
    limits  = httpx.Limits(max_connections=10, max_keepalive_connections=5),
)


async def _sezon(baslik: str, sezon: int) -> dict:
    """{bölüm_no: {"title", "overview", "still"}}. Bulunamazsa boş."""
    ad = _clean_title(baslik).lower()
    if not ad or not _API_KEY:
        return {}

    anahtar = f"{ad}#{sezon}"
    kayit = _cache.get(anahtar)
    if kayit and time.time() - kayit[0] < _CACHE_TTL:
        return kayit[1]

    cikti: dict = {}
    try:
        bulunan = await _client.get(
            _SEARCH_TV,
            params = {"api_key": _API_KEY, "query": ad, "language": "tr-TR"},
        )
        sonuclar = (bulunan.json() or {}).get("results") or []
        if sonuclar:
            dizi_id = sonuclar[0]["id"]
            tr = await _client.get(
                _SEASON.format(id=dizi_id, season=sezon),
                params = {"api_key": _API_KEY, "language": "tr-TR"},
            )
            bolumler = (tr.json() or {}).get("episodes") or []

            # Türkçe özetlerin hepsi boşsa İngilizceye düşülür: bazı diziler
            # TMDB'de yalnız İngilizce çevrilmiş oluyor.
            if bolumler and not any((b.get("overview") or "").strip() for b in bolumler):
                en = await _client.get(
                    _SEASON.format(id=dizi_id, season=sezon),
                    params = {"api_key": _API_KEY, "language": "en-US"},
                )
                bolumler = (en.json() or {}).get("episodes") or bolumler

            for b in bolumler:
                no = b.get("episode_number")
                if not no:
                    continue
                cikti[str(no)] = {
                    "title"    : b.get("name") or "",
                    "overview" : (b.get("overview") or "").strip(),
                    "still"    : f"{_IMG_BASE}{b['still_path']}" if b.get("still_path") else "",
                }
    except Exception:
        cikti = {}

    _cache[anahtar] = (time.time(), cikti)
    return cikti


@api_v1_router.get("/episode_overviews")
async def episode_overviews(request: Request):
    """Bir sezonun bölüm özetleri. `title` + `season` ile çağrılır."""
    veri = request.state.veri or {}
    baslik = str(veri.get("title") or "").strip()
    try:
        sezon = int(veri.get("season") or 1)
    except (TypeError, ValueError):
        sezon = 1

    if not baslik:
        return {**api_v1_global_message, "result": {"season": sezon, "episodes": {}}}

    return {
        **api_v1_global_message,
        "result": {"season": sezon, "episodes": await _sezon(baslik, sezon)},
    }
