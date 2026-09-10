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

# Aynı aramanın puanı da aynı yanıtta geliyor; ikinci bir istek gereksiz.
# temiz-başlık(lower) -> vote_average | None
_rating_cache: dict[str, float | None] = {}

# Yayın yılı da aynı yanıtta: ana sayfa "yeni" rafını yıla göre sıralayabilsin.
# temiz-başlık(lower) -> yıl | None
_year_cache: dict[str, int | None] = {}

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
    rating: float | None = None
    year: int | None = None
    try:
        resp = await _client.get(_TMDB_SEARCH, params={
            "api_key"       : TMDB_API_KEY,
            "language"      : "tr-TR",
            "query"         : clean_title,
            "include_adult" : "false",
        })
        if resp.status_code == 200:
            for r in (resp.json().get("results") or []):
                if r.get("media_type") not in ("movie", "tv"):
                    continue
                if poster_path is None and r.get("poster_path"):
                    poster_path = r["poster_path"]
                if rating is None:
                    puan = r.get("vote_average")
                    # TMDB oy almamış içeriğe 0 yazıyor; 0 puan göstermek yanlış olur.
                    rating = round(float(puan), 1) if puan else None
                if year is None:
                    tarih = str(r.get("release_date") or r.get("first_air_date") or "")[:4]
                    year = int(tarih) if tarih.isdigit() else None
                if poster_path is not None:
                    break
    except Exception:
        poster_path = None

    if len(_cache) < _CACHE_MAX:
        _cache[key] = poster_path
        _rating_cache[key] = rating
        _year_cache[key] = year
    return poster_path


def year_for(title: str) -> int | None:
    """Başlığın TMDB yılı — YALNIZ cache'ten (puanla aynı aramadan gelir)."""
    clean = _clean_title(title)
    return _year_cache.get(clean.lower()) if clean else None


async def rating_for(title: str, fetch: bool = False) -> float | None:
    """Başlığın TMDB puanı. `fetch=False` ise YALNIZ cache'e bakar.

    Ana sayfa 300+ başlık taşıyor: hepsini istek anında aramak listeyi dakikalarca
    bekletirdi. Liste cache'ten anında döner, eksik puanlar arka planda doldurulur
    ve sonraki yenilemede görünür.
    """
    if not TMDB_API_KEY:
        return None
    clean = _clean_title(title)
    if not clean:
        return None
    key = clean.lower()
    if key in _rating_cache:
        return _rating_cache[key]
    if not fetch:
        return None
    await _resolve_poster(clean)
    return _rating_cache.get(key)


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
