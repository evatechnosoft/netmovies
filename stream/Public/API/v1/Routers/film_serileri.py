# NetMovies — Film serileri / franchise (TMDB koleksiyonları).
#
# Dean: "Blade Runner izlediyse Blade Runner serisinin filmleri sırayla
# görünsün." Gözat'ta ayrı sekme olacak, kart tıklanınca normal arama yoluyla
# açılacak (yeni oynatma mantığı yok).
#
# Kaynak adayları:
#   - İzleme geçmişindeki filmler (watch_store) — başlıktan `/search/movie`.
#   - TMDB `/movie/popular` ilk 2 sayfa.
# Her aday için `/movie/{id}` → `belongs_to_collection`; benzersiz koleksiyon
# id'leri için `/collection/{id}` (tr-TR) çekilip parçaları çıkış tarihine göre
# sıralanır. En az 2 parçalı koleksiyonlar tutulur, izleme geçmişinden gelenler
# listenin başına.
#
# TMDB_API_KEY yoksa / herhangi bir hata olursa boş liste döner (200) — sayfa
# çalışmaya devam etsin.

from __future__ import annotations

import asyncio
import os
import time

import httpx

from Core import Request
from .    import api_v1_router, api_v1_global_message

from Public.Home.Libs import watch_store
from Public.Home.Routers.tmdb import _clean_title

_API_KEY        = os.getenv("TMDB_API_KEY", "").strip()
_POPULAR        = "https://api.themoviedb.org/3/movie/popular"
_SEARCH_MOVIE   = "https://api.themoviedb.org/3/search/movie"
_MOVIE_DETAIL   = "https://api.themoviedb.org/3/movie/{id}"
_COLLECTION     = "https://api.themoviedb.org/3/collection/{id}"
_IMG_BASE       = "https://image.tmdb.org/t/p/w500"

# Koleksiyonlar sık değişmez (yeni film çıktıkça); 12 saat yeter.
_CACHE_TTL = 12 * 60 * 60
_cache: dict[str, tuple[float, list[dict]]] = {}

# TMDB'ye aynı anda çok istek açmamak için sınır.
_SEMAFOR = asyncio.Semaphore(5)

_client = httpx.AsyncClient(
    timeout = httpx.Timeout(connect=5.0, read=8.0, write=5.0, pool=5.0),
    limits  = httpx.Limits(max_connections=10, max_keepalive_connections=5),
)


async def _json(url: str, params: dict) -> dict:
    async with _SEMAFOR:
        try:
            yanit = await _client.get(url, params={**params, "api_key": _API_KEY})
            return yanit.json() if yanit.status_code == 200 else {}
        except Exception:
            return {}


async def _izleme_gecmisi_film_basliklari() -> list[str]:
    """İzleme geçmişindeki (devam eden + bitmiş) film başlıkları, tekilleştirilmiş."""
    satirlar = watch_store.list_continue_watching(limit=50) + watch_store.list_watched(limit=50)
    basliklar: list[str] = []
    gorulen: set[str] = set()
    for satir in satirlar:
        if (satir.get("media_type") or "") != "movie":
            continue
        baslik = _clean_title(satir.get("title") or "")
        if baslik and baslik.lower() not in gorulen:
            gorulen.add(baslik.lower())
            basliklar.append(baslik)
    return basliklar


async def _baslik_tmdb_id(baslik: str) -> int | None:
    sonuc = await _json(_SEARCH_MOVIE, {"query": baslik, "language": "tr-TR"})
    sonuclar = sonuc.get("results") or []
    return sonuclar[0]["id"] if sonuclar else None


async def _populer_film_idleri() -> list[int]:
    sayfalar = await asyncio.gather(*(
        _json(_POPULAR, {"language": "tr-TR", "page": sayfa}) for sayfa in (1, 2)
    ))
    return [x["id"] for liste in sayfalar for x in (liste.get("results") or []) if x.get("id")]


async def _koleksiyon_id(film_id: int) -> int | None:
    detay = await _json(_MOVIE_DETAIL.format(id=film_id), {"language": "tr-TR"})
    koleksiyon = detay.get("belongs_to_collection") or {}
    return koleksiyon.get("id")


def _parca(p: dict) -> dict:
    poster = p.get("poster_path")
    yil = str(p.get("release_date") or "")[:4]
    return {
        "baslik" : p.get("title") or "",
        "yil"    : int(yil) if yil.isdigit() else 0,
        "poster" : f"{_IMG_BASE}{poster}" if poster else "",
        # Sıralama için saklanır, yanıta taşınmaz.
        "_tarih" : p.get("release_date") or "",
    }


async def _koleksiyon_detay(koleksiyon_id: int) -> dict | None:
    veri = await _json(_COLLECTION.format(id=koleksiyon_id), {"language": "tr-TR"})
    if not veri:
        return None
    parcalar = sorted((_json_parts(veri)), key=lambda p: p["_tarih"])
    if len(parcalar) < 2:
        return None
    for p in parcalar:
        p.pop("_tarih", None)
    poster = veri.get("poster_path")
    return {
        "id"      : veri.get("id") or koleksiyon_id,
        "ad"      : veri.get("name") or "",
        "poster"  : f"{_IMG_BASE}{poster}" if poster else "",
        "parcalar": parcalar,
    }


def _json_parts(veri: dict) -> list[dict]:
    return [_parca(p) for p in (veri.get("parts") or [])]


async def _film_serileri_verisi() -> list[dict]:
    if not _API_KEY:
        return []

    gecmis_basliklar = await _izleme_gecmisi_film_basliklari()
    gecmis_idler_raw, populer_idler = await asyncio.gather(
        asyncio.gather(*(_baslik_tmdb_id(b) for b in gecmis_basliklar)),
        _populer_film_idleri(),
    )
    gecmis_idler = [i for i in gecmis_idler_raw if i]

    # Geçmiş adayları önce: koleksiyon keşfinde ve sırada önceliklidir.
    film_idleri = list(dict.fromkeys([*gecmis_idler, *populer_idler]))
    gecmis_id_seti = set(gecmis_idler)

    koleksiyon_idleri = await asyncio.gather(*(_koleksiyon_id(fid) for fid in film_idleri))

    # Benzersiz koleksiyon id'leri, ilk görüldüğü sırayla; geçmişten gelen film
    # hangi koleksiyonu tetiklediyse o koleksiyon "geçmişten" sayılır.
    sira: list[int] = []
    gecmisten: set[int] = set()
    for fid, kid in zip(film_idleri, koleksiyon_idleri):
        if not kid:
            continue
        if kid not in sira:
            sira.append(kid)
        if fid in gecmis_id_seti:
            gecmisten.add(kid)

    detaylar = await asyncio.gather(*(_koleksiyon_detay(kid) for kid in sira))
    koleksiyonlar = [d for d in detaylar if d]

    # İzleme geçmişinden gelenler başta, aralarında keşif sırası korunur.
    koleksiyonlar.sort(key=lambda k: 0 if k["id"] in gecmisten else 1)
    return koleksiyonlar


@api_v1_router.get("/film_serileri")
async def film_serileri(request: Request):
    """Film serileri (franchise): izleme geçmişi + popüler filmlerden TMDB koleksiyonları."""
    onbellek = _cache.get("veri")
    if onbellek and onbellek[0] > time.monotonic():
        sonuc = onbellek[1]
    else:
        sonuc = await _film_serileri_verisi()
        _cache["veri"] = (time.monotonic() + _CACHE_TTL, sonuc)
    return {**api_v1_global_message, "result": sonuc}
