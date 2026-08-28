# NetMovies — TMDB poster fallback.
# Kaynak sitelerin posterleri hotlink-korumalı / lazy-load (data-src) olduğu için
# sık boş/kırık geliyor. Bu endpoint, item boş/bozuk poster verdiğinde başlıktan
# TMDB'de arama yapıp image.tmdb.org'a 302 yönlendirir (hotlink-korumasız CDN,
# bant genişliği bize yük olmaz). Bellek-içi pozitif+negatif cache ile tekrar
# aramalar önlenir. TMDB_API_KEY yoksa sessizce 404 (mevcut placeholder gösterilir).

import os, re
from fastapi           import Response
from fastapi.responses import RedirectResponse
from .                 import home_router
import httpx

TMDB_API_KEY = os.getenv("TMDB_API_KEY", "").strip()
_IMG_BASE    = "https://image.tmdb.org/t/p/w500"
_TMDB_SEARCH = "https://api.themoviedb.org/3/search/multi"

# temiz-başlık(lower) -> poster_path | None (None = negatif cache)
_cache: dict[str, str | None] = {}
_CACHE_MAX = 5000

_client = httpx.AsyncClient(
    timeout = httpx.Timeout(connect=5.0, read=8.0, write=5.0, pool=5.0),
    limits  = httpx.Limits(max_connections=20, max_keepalive_connections=10),
)

# Başlık gürültüsü: "... izle", kalite, dublaj/altyazı, sezon/bölüm işaretleri, yıl parantezi.
_NOISE = re.compile(
    r"\(\d{4}\)"                                            # (2024)
    r"|\[[^\]]*\]"                                          # [ ... ]
    r"|\d+\s*\.?\s*(?:sezon|b[öo]l[üu]m)"                   # 3. Sezon / 12. Bölüm
    r"|\b(?:izle|full\s*hd|hd|4k|1080p?|720p?|480p?"
    r"|t[üu]rk[çc]e|dublaj|alt\s*yaz[ıi]l[ıi]?|altyaz[ıi]"
    r"|sezon|sezonu|b[öo]l[üu]m|final)\b",
    re.IGNORECASE,
)


def _clean_title(title: str) -> str:
    t = _NOISE.sub(" ", title or "")
    t = re.sub(r"[·|–—]+", " ", t)
    t = re.sub(r"\s+", " ", t).strip(" -·:")
    return t


async def _resolve_poster(clean_title: str) -> str | None:
    key = clean_title.lower()
    if key in _cache:
        return _cache[key]

    poster_path: str | None = None
    try:
        resp = await _client.get(_TMDB_SEARCH, params={
            "api_key"       : TMDB_API_KEY,
            "language"      : "tr-TR",
            "query"         : clean_title,
            "include_adult" : "false",
        })
        if resp.status_code == 200:
            for r in (resp.json().get("results") or []):
                if r.get("media_type") in ("movie", "tv") and r.get("poster_path"):
                    poster_path = r["poster_path"]
                    break
    except Exception:
        poster_path = None

    if len(_cache) < _CACHE_MAX:
        _cache[key] = poster_path
    return poster_path


@home_router.get("/tmdb-poster")
async def tmdb_poster(title: str = "", year: str = "", type: str = ""):
    """Başlıktan TMDB posterini bulup image.tmdb.org'a 302 yönlendirir."""
    if not TMDB_API_KEY:
        return Response(status_code=404, content="TMDB_API_KEY yok")

    clean = _clean_title(title)
    if not clean:
        return Response(status_code=404, content="Geçersiz başlık")

    poster_path = await _resolve_poster(clean)
    if not poster_path:
        return Response(status_code=404, content="Poster bulunamadı")

    return RedirectResponse(
        url         = f"{_IMG_BASE}{poster_path}",
        status_code = 302,
        headers     = {"Cache-Control": "public, max-age=604800"},  # 7 gün
    )
