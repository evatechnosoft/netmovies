# NetMovies — Takip listesi + yayın takvimi.
#
# `watch_store` zaten "takip" listesini tutuyordu ama hiçbir istemci onu takvimle
# birleştirmiyordu: kullanıcı "sonraki bölüm ne zaman" sorusunu uygulamada
# cevaplayamıyordu. Burada takip edilen her başlık TMDB'de eşleştirilip
# `next_episode_to_air` bilgisiyle döner.
#
# Türk / yabancı ayrımı TMDB `origin_country` alanından gelir (kaynak sitenin
# kategorisinden DEĞİL — aynı dizi farklı sitelerde farklı kategoride duruyor).
#
# TMDB_API_KEY yoksa liste yine döner, takvim alanları boş kalır: takip listesi
# çalışmaya devam etsin, yalnız tarih bilgisi eksilsin.

from __future__ import annotations

import asyncio
import os
import time

import httpx

from Core import Request
from .    import api_v1_router, api_v1_global_message

from Public.Home.Libs import watch_store
from Public.Home.Routers.tmdb import _clean_title   # başlık gürültüsü temizleyici (tek yer)

_API_KEY   = os.getenv("TMDB_API_KEY", "").strip()
_SEARCH    = "https://api.themoviedb.org/3/search/tv"
_DETAIL    = "https://api.themoviedb.org/3/tv/{id}"
_IMG_BASE  = "https://image.tmdb.org/t/p/w500"

# Yayın takvimi gün içinde değişmez; TMDB kotasını ve açılış süresini korumak için
# bellekte tutulur. Süre dolunca ilk istek tazeler.
_CACHE_TTL = 6 * 60 * 60
_cache: dict[str, tuple[float, dict]] = {}

_client = httpx.AsyncClient(
    timeout = httpx.Timeout(connect=5.0, read=8.0, write=5.0, pool=5.0),
    limits  = httpx.Limits(max_connections=10, max_keepalive_connections=5),
)


async def _tmdb_show(title: str) -> dict:
    """Başlıktan TMDB dizisini bulup takvim alanlarını çıkarır. Bulunamazsa boş sözlük."""
    key = _clean_title(title).lower()
    if not key or not _API_KEY:
        return {}

    hit = _cache.get(key)
    if hit and time.time() - hit[0] < _CACHE_TTL:
        return hit[1]

    info: dict = {}
    try:
        found = await _client.get(
            _SEARCH,
            params = {"api_key": _API_KEY, "query": key, "language": "tr-TR"},
        )
        results = (found.json() or {}).get("results") or []
        if results:
            show = results[0]
            detail = await _client.get(
                _DETAIL.format(id=show["id"]),
                params = {"api_key": _API_KEY, "language": "tr-TR"},
            )
            data = detail.json() or {}
            nxt  = data.get("next_episode_to_air") or {}
            last = data.get("last_episode_to_air") or {}
            countries = data.get("origin_country") or show.get("origin_country") or []
            info = {
                "tmdb_id"      : show["id"],
                "name"         : data.get("name") or show.get("name") or title,
                "poster"       : f"{_IMG_BASE}{show['poster_path']}" if show.get("poster_path") else "",
                "status"       : data.get("status") or "",
                "is_turkish"   : "TR" in countries,
                "next_date"    : nxt.get("air_date") or "",
                "next_season"  : nxt.get("season_number") or 0,
                "next_episode" : nxt.get("episode_number") or 0,
                "next_name"    : nxt.get("name") or "",
                "last_date"    : last.get("air_date") or "",
            }
    except Exception:
        info = {}

    _cache[key] = (time.time(), info)
    return info


@api_v1_router.get("/following")
async def following(request: Request):
    """Takip edilen diziler + sonraki bölüm bilgisi, Türkçe/yabancı ayrık."""
    rows = watch_store.list_user_list("takip", 200)
    if not rows:
        return {**api_v1_global_message, "result": {"turkish": [], "foreign": []}}

    infos = await asyncio.gather(*[_tmdb_show(r.get("title") or "") for r in rows])

    turkish: list[dict] = []
    foreign: list[dict] = []
    for row, info in zip(rows, infos):
        item = {
            "content_key" : row.get("content_key") or "",
            "plugin"      : row.get("plugin") or "",
            "title"       : row.get("title") or "",
            # Kaynak posteri hotlink korumalı olabiliyor; TMDB posteri varsa o tercih edilir.
            "poster"      : info.get("poster") or row.get("poster") or "",
            "content_url" : row.get("content_url") or "",
            "status"      : info.get("status") or "",
            "next_date"   : info.get("next_date") or "",
            "next_season" : info.get("next_season") or 0,
            "next_episode": info.get("next_episode") or 0,
            "next_name"   : info.get("next_name") or "",
        }
        (turkish if info.get("is_turkish") else foreign).append(item)

    # Tarihi olan üstte, en yakın gün önce; tarihsizler sona.
    def _order(x: dict):
        return (0, x["next_date"]) if x["next_date"] else (1, x["title"])

    turkish.sort(key=_order)
    foreign.sort(key=_order)
    return {**api_v1_global_message, "result": {"turkish": turkish, "foreign": foreign}}
