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
from ..Libs.ajanda_grup import aralikla, gunlere_bol

_API_KEY   = os.getenv("TMDB_API_KEY", "").strip()
_DISCOVER  = "https://api.themoviedb.org/3/discover/tv"
_TV_DETAIL = "https://api.themoviedb.org/3/tv/{id}"
_UPCOMING  = "https://api.themoviedb.org/3/movie/upcoming"
_DISCOVER_MOVIE = "https://api.themoviedb.org/3/discover/movie"
_IMG_BASE  = "https://image.tmdb.org/t/p/w500"

# Takvim gün içinde değişmez; TMDB kotası ve açılış süresi için günde bir tazelenir.
_CACHE_TTL = 24 * 60 * 60
_cache: dict[str, tuple[float, list[dict]]] = {}

# Detay çekilecek dizi adayı sayısı: `discover` popülerlik sırasıyla döndüğü için
# baştan almak yeter, ama her aday bir istek demek — üst sınır konur. Aylık
# aralık haftalıktan geniş olduğu için sınır iki sayfaya çıkarıldı.
_ADAY_SINIRI = 40

_client = httpx.AsyncClient(
    timeout = httpx.Timeout(connect=5.0, read=8.0, write=5.0, pool=5.0),
    limits  = httpx.Limits(max_connections=10, max_keepalive_connections=5),
)


# Ajanda GERİYE de bakar. Yayın günü geçen bölüm listeden düşüyordu, ama bölüm
# o gün sağlayıcıya düşmemiş olabiliyor (Dean, 16 Eylül: "gün geçince atlıyor ama
# düşmemiş olabiliyor sağlayıcıya"). Takip edilecek pencere yayın gününde kapanmaz.
# Bir haftadan uzun süredir düşmemiş bölüm ajandanın işi değil, orada sınır var.
_GECMIS_GUN = 7


def _aralik(view: str) -> tuple[datetime.date, datetime.date]:
    bugun = datetime.date.today()
    return (
        bugun - datetime.timedelta(days=_GECMIS_GUN),
        bugun + datetime.timedelta(days=30 if view == "month" else 7),
    )


async def _json(url: str, params: dict) -> dict:
    try:
        yanit = await _client.get(url, params={**params, "api_key": _API_KEY})
        return yanit.json() if yanit.status_code == 200 else {}
    except Exception:
        return {}


def _bolum_satiri(detay: dict, bolum: dict, bas: datetime.date, son: datetime.date) -> dict | None:
    """Tek bölümü ajanda satırına çevirir. Tarihi aralık dışındaysa None."""
    tarih = (bolum or {}).get("air_date")
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


async def _dizi_bolumleri(dizi_id: int, bas: datetime.date, son: datetime.date) -> list[dict]:
    """Dizinin aralığa düşen bölümleri.

    `next_episode_to_air` YALNIZ geleceği gösterir: dün yayınlanan bölüm orada
    değil, `last_episode_to_air`tadır. Geçmişe bakan pencerede ikisi de okunur —
    aynı dizinin geçen haftaki ve bu haftaki bölümü ayrı satırlar olur.
    """
    detay = await _json(_TV_DETAIL.format(id=dizi_id), {"language": "tr-TR"})
    satirlar = [
        _bolum_satiri(detay, detay.get(alan) or {}, bas, son)
        for alan in ("last_episode_to_air", "next_episode_to_air")
    ]
    # Aynı bölüm iki alanda birden görünebiliyor (yayın günü); tarih+bölüm ile tekilleştir.
    tekil: dict[tuple, dict] = {}
    for s in satirlar:
        if s:
            tekil.setdefault((s["tarih"], s["bolum"]), s)
    return list(tekil.values())


async def _diziler(bas: datetime.date, son: datetime.date) -> list[dict]:
    # `discover` sayfa başına 20 kayıt veriyor; tek sayfa aylık aralığa yetmiyordu
    # (aynı gün haftada görünen dizi ayda listeden düşüyordu). İki sayfa çekilir.
    sayfalar = await asyncio.gather(*(
        _json(_DISCOVER, {
            "language"           : "tr-TR",
            "with_origin_country": "TR",
            "air_date.gte"       : str(bas),
            "air_date.lte"       : str(son),
            "sort_by"            : "popularity.desc",
            "page"               : sayfa,
        })
        for sayfa in (1, 2)
    ))
    kayitlar = [x for liste in sayfalar for x in (liste.get("results") or [])]
    adaylar  = list(dict.fromkeys(x["id"] for x in kayitlar if x.get("id")))[:_ADAY_SINIRI]
    sonuc = await asyncio.gather(*(_dizi_bolumleri(i, bas, son) for i in adaylar), return_exceptions=True)
    return [s for liste in sonuc if isinstance(liste, list) for s in liste]


async def _filmler(bas: datetime.date, son: datetime.date) -> list[dict]:
    # `movie/upcoming` adı üstünde: YALNIZ gelecek vizyonlar. Geçmişe bakan pencerede
    # bu hafta vizyona girmiş film hiç görünmüyordu. `discover/movie` aynı veriyi
    # tarih aralığıyla verir; `with_release_type=2|3` sinema/dijital vizyonu seçer,
    # `region=TR` ile `release_date` TÜRKİYE tarihidir.
    liste = await _json(_DISCOVER_MOVIE, {
        "language"                     : "tr-TR",
        "region"                       : "TR",
        "with_release_type"            : "2|3",
        "primary_release_date.gte"     : str(bas),
        "primary_release_date.lte"     : str(son),
        "sort_by"                      : "popularity.desc",
    })

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

    # HER İKİ görünüm de AYLIK turdan beslenir, hafta ondan süzülür. Ayrı sorgu
    # atıldığında `discover` iki aralık için farklı "ilk 20 popüler" listesi
    # döndürüyordu: aynı gün haftada 6, ayda 5 satır görünüyor, ay haftanın alt
    # kümesi olmuyordu. Tek tur hem bunu keser hem TMDB isteğini yarıya indirir.
    onbellek = _cache.get("month")
    if onbellek and onbellek[0] > time.monotonic():
        satirlar = onbellek[1]
    else:
        bas, son = _aralik("month")
        diziler, filmler = await asyncio.gather(_diziler(bas, son), _filmler(bas, son))
        satirlar = sorted(diziler + filmler, key=lambda s: (s["tarih"], s["baslik"]))
        _cache["month"] = (time.monotonic() + _CACHE_TTL, satirlar)

    if view == "week":
        satirlar = aralikla(satirlar, str(_aralik("week")[1]))

    return {"view": view, "toplam": len(satirlar), "gunler": gunlere_bol(satirlar)}


@api_v1_router.get("/agenda")
async def agenda(request: Request):
    return {**api_v1_global_message, "result": await agenda_verisi((request.state.veri or {}).get("view", "week"))}
