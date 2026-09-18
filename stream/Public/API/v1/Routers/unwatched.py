# NetMovies — "İzlemediklerim": takip edilen dizilerin yayınlanmış ama izlenmemiş
# bölümleri, TEK listede.
#
# NEDEN AJANDA YETMİYOR: `/agenda` TMDB'nin POPÜLER yayın takvimidir — kullanıcının
# listesiyle ilgisi yok ve gün gün gruplanır. Dean'in sorduğu şey başka: "izlemediğim
# bölümleri özellikle takip edebilmeliyim" (18 Eylül). Gün kırılımı burada gürültü:
# iki hafta önce çıkmış ama izlenmemiş bölüm, dünkü kadar önemlidir.
#
# Kaynak: takip/izlenecek/planlandı listeleri + favoriler (kullanıcının kendi
# seçimi) × TMDB bölüm takvimi × watch_history (nereye kadar izlendi).

from __future__ import annotations

import asyncio
import datetime
import os
import re

import httpx

from Core import Request
from .    import api_v1_router, api_v1_global_message

from Public.Home.Libs import watch_store

_API_KEY   = os.getenv("TMDB_API_KEY", "").strip()
_SEARCH_TV = "https://api.themoviedb.org/3/search/tv"
_TV_DETAIL = "https://api.themoviedb.org/3/tv/{id}"
_SEASON    = "https://api.themoviedb.org/3/tv/{id}/season/{season}"
_IMG_BASE  = "https://image.tmdb.org/t/p/w500"

# Bir dizi için en fazla kaç sezon taranır: eski sezonlar zaten izlenmiş kabul
# edilir, ilgi alanı son sezonlardır. Her sezon bir TMDB isteği.
_SON_SEZON_SAYISI = 2
# Kaç gün geriye bakılır. "Bu ay" hissi için 45 gün: aylık sınır ayın 1'inde
# dünkü bölümü listeden düşürürdü.
_GERI_GUN = 45
_ES_ZAMANLI = 6

_client = httpx.AsyncClient(
    timeout = httpx.Timeout(connect=5.0, read=8.0, write=5.0, pool=5.0),
    limits  = httpx.Limits(max_connections=10, max_keepalive_connections=5),
)


def ref_coz(metin: str) -> tuple[int, int] | None:
    """"S4B8" / "S4 E8" → (4, 8). TV "S4B8", web "S4 E8" yazıyor; eski kayıtlar
    düz indeks taşıyor ve bölüm numarasına çevrilemez — o zaman None."""
    m = re.search(r"[Ss](\d+)\s*[BbEe](\d+)", str(metin or ""))
    return (int(m.group(1)), int(m.group(2))) if m else None


def _izlenen_bolum(baslik: str) -> tuple[int, int] | None:
    """Kayıttaki son izlenen (sezon, bölüm). Kayıt yoksa ya da bölüm numarası
    taşımıyorsa None — o zaman dizinin tamamı 'izlenmemiş' sayılır."""
    kayit = watch_store.get_progress(watch_store.normalize_key(baslik, "serie"))
    return ref_coz((kayit or {}).get("episode")) if kayit else None


def _takip_edilen_basliklar() -> list[str]:
    basliklar: list[str] = []
    kaynaklar = (
        lambda: watch_store.list_user_list("takip", 200),
        lambda: watch_store.list_user_list("izlenecek", 200),
        lambda: watch_store.list_user_list("planlandi", 200),
        lambda: watch_store.list_favorites(),
    )
    for oku in kaynaklar:
        try:
            for satir in oku() or []:
                baslik = (satir or {}).get("title") or ""
                # Film de favoride olabilir; TMDB dizi aramasında karşılığı
                # çıkmazsa zaten elenir, ayrıca tür filtresi gerekmiyor.
                if baslik and baslik not in basliklar:
                    basliklar.append(baslik)
        except Exception:
            continue
    return basliklar


async def _json(url: str, params: dict) -> dict:
    try:
        yanit = await _client.get(url, params={**params, "api_key": _API_KEY})
        return yanit.json() if yanit.status_code == 200 else {}
    except Exception:
        return {}


async def _dizinin_bolumleri(baslik: str, bas: datetime.date, bugun: datetime.date) -> list[dict]:
    arama = await _json(_SEARCH_TV, {"query": baslik, "language": "tr-TR"})
    ilk = next(iter(arama.get("results") or []), None)
    if not ilk:
        return []

    detay = await _json(_TV_DETAIL.format(id=ilk["id"]), {"language": "tr-TR"})
    sezonlar = [
        s.get("season_number") for s in (detay.get("seasons") or [])
        if isinstance(s.get("season_number"), int) and s["season_number"] > 0
    ]
    if not sezonlar:
        return []

    izlenen = _izlenen_bolum(baslik)
    poster  = detay.get("poster_path")
    satirlar: list[dict] = []

    for sezon_no in sorted(sezonlar)[-_SON_SEZON_SAYISI:]:
        sezon = await _json(_SEASON.format(id=ilk["id"], season=sezon_no), {"language": "tr-TR"})
        for bolum in sezon.get("episodes") or []:
            tarih = bolum.get("air_date")
            if not tarih:
                continue
            try:
                gun = datetime.date.fromisoformat(tarih)
            except ValueError:
                continue
            # Yayınlanmamış bölüm "izlenmemiş" değildir; çok eskisi de ajandanın işi değil.
            if not (bas <= gun <= bugun):
                continue
            bolum_no = bolum.get("episode_number")
            if not isinstance(bolum_no, int):
                continue
            # Kayıt varsa ondan SONRAKİLER izlenmemiştir. Kayıt yoksa hepsi.
            if izlenen and (sezon_no, bolum_no) <= izlenen:
                continue
            # Şema AJANDA ile birebir: istemci aynı modeli ve aynı çizimi
            # kullanıyor, yeni ekran yazılmıyor.
            satirlar.append({
                "tur"    : "dizi",
                "baslik" : detay.get("name") or baslik,
                "tarih"  : tarih,
                "poster" : f"{_IMG_BASE}{poster}" if poster else "",
                "bolum"  : f"S{sezon_no}B{bolum_no}" + (f" · {bolum.get('name')}" if bolum.get("name") else ""),
                "ozet"   : (bolum.get("overview") or "")[:300],
                "puan"   : round(float(detay.get("vote_average") or 0), 1),
            })
    return satirlar


@api_v1_router.get("/unwatched")
async def unwatched(request: Request):
    """Takip edilen dizilerin yayınlanmış ama izlenmemiş bölümleri, tek liste.

    Gün gruplaması YOK: en yeniden eskiye tek akış. İstemci istediği başlığı
    ("Bu ay") kendi yazar — sunucu tarih verir, sunum istemcinin işidir.
    """
    if not _API_KEY:
        return {**api_v1_global_message, "result": {"view": "unwatched", "toplam": 0, "gunler": [], "hata": "TMDB_API_KEY yok"}}

    bugun = datetime.date.today()
    bas   = bugun - datetime.timedelta(days=_GERI_GUN)
    basliklar = _takip_edilen_basliklar()
    if not basliklar:
        return {**api_v1_global_message, "result": {"view": "unwatched", "toplam": 0, "gunler": []}}

    kilit = asyncio.Semaphore(_ES_ZAMANLI)

    async def tek(baslik: str) -> list[dict]:
        async with kilit:
            try:
                return await _dizinin_bolumleri(baslik, bas, bugun)
            except Exception:
                # Tek dizinin TMDB'de bulunamaması listeyi düşürmemeli.
                return []

    gruplar = await asyncio.gather(*(tek(b) for b in basliklar), return_exceptions=True)
    satirlar = [s for g in gruplar if isinstance(g, list) for s in g]
    satirlar.sort(key=lambda s: (s["tarih"], s["baslik"]), reverse=True)

    # TEK grup: gün kırılımı burada gürültü — iki hafta önce çıkmış ama izlenmemiş
    # bölüm dünkü kadar önemli. İstemci `tarih`i ISO olarak ayrıştıramazsa başlığı
    # olduğu gibi yazıyor (AgendaScreen.GunBasligi), başlık o yüzden metin.
    gunler = [{"tarih": "İzlemediklerin", "ogeler": satirlar}] if satirlar else []
    return {**api_v1_global_message, "result": {"view": "unwatched", "toplam": len(satirlar), "gunler": gunler}}
