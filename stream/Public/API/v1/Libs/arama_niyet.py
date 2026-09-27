# NetMovies — arama sorgusundan niyet (kural tabanlı, modelsiz).
#
# Dean (27 Eylül): "dublaj türkçe" deyince önce dublaj, "2026 sürümü" deyince o
# yıl, "son" deyince en yeni, "tüm seri" deyince bütün seri sıralı gelsin. Bu
# sözcükler sağlayıcı aramasına GİTMEMELİ: "resident evil 2026" HDFilmCehennemi'de
# 0 sonuç, "resident evil" 6 sonuç veriyor (canlı sunucu, 2026-09-27).
#
# Saf fonksiyonlar: model (Gemini/Gemma/Nano) aynı `Niyet` yapısını doldurabilir,
# eşleştirme ve sıralama yine buradaki kodla yapılır. Alan adları istemcideki
# `client-tv/.../data/yz/Niyet.kt` ile aynı; `dil`, `yil`, `sira` arama için eklendi.

from __future__ import annotations

import re

from dataclasses import dataclass
from datetime import date
from urllib.parse import unquote_plus

# Türkçe'ye duyarlı sadeleştirme ("İ".lower() birleşik nokta üretir).
_HARFLER = str.maketrans("İIıŞşĞğÜüÖöÇçÂâ", "iiissgguuooccaa")

DIL_DUBLAJ  = "dublaj"
DIL_ALTYAZI = "altyazi"
SIRA_SON    = "son"
SIRA_SERI   = "seri"


@dataclass(frozen=True)
class Niyet:
    baslik: str | None = None
    tur: str | None = None      # "film" | "dizi" | "canli" — arama henüz doldurmuyor
    sezon: int | None = None
    bolum: int | None = None
    gun: str | None = None
    kanal: str | None = None
    dil: str | None = None      # DIL_DUBLAJ | DIL_ALTYAZI
    yil: int | None = None
    sira: str | None = None     # SIRA_SON | SIRA_SERI


def sade(metin: str | None) -> str:
    return str(metin or "").translate(_HARFLER).lower()


# Sözcük sınırıyla, sadeleştirilmiş metinde aranır; sıra önemli (uzun olan önce).
_HER_YERDE: tuple[tuple[str, str], ...] = (
    (r"(turkce|tr) ?alt ?yazi(li)?|alt ?yazi(li)?", DIL_ALTYAZI),
    (r"(turkce|tr) ?dublaj(li)?|dublaj(li)?", DIL_DUBLAJ),
    # Yalnız "türkçe"/"tr": Türkçe izlemek = önce dublaj, sonra altyazı.
    (r"turkce|tr", DIL_DUBLAJ),
    (r"(tum|butun) ?(seri(si)?|filmler(i)?)", SIRA_SERI),
    (r"en ?yeni(si)?|en ?son(u|uncusu)?|sonuncu(su)?", SIRA_SON),
)
# Tek sözcüklük sıra işaretleri başlıkta da geçer ("Yeni Gelin", "Son Bölüm"):
# yalnız sorgunun SONUNDA işaret sayılır ("resident evil yenisi").
_SONDA: dict[str, str] = {
    "son": SIRA_SON, "yeni": SIRA_SON, "yenisi": SIRA_SON,
    "seri": SIRA_SERI, "serisi": SIRA_SERI, "hepsi": SIRA_SERI,
}
_YIL = re.compile(r"\(?\b((?:19|20)\d{2})\b\)?(\s+(surum(u)?|versiyon(u)?|yapimi|filmi))?")


def ayristir(sorgu: str) -> Niyet:
    """Sorgudan işaretleri ayıklar; kalan metin `baslik` olur.

    Sorgu tümüyle işaretse ("yeni", "2024") başlık olarak korunur — arama boş
    metinle yapılamaz.
    """
    ham = " ".join(str(sorgu or "").split())
    if not ham:
        return Niyet()

    # Konumlar sadeleştirilmiş metinde bulunur, kesme ham metinde yapılır —
    # translate harf sayısını korur, indeksler örtüşür.
    sadesi = sade(ham)
    silinecek: list[tuple[int, int]] = []
    dil: str | None = None
    sira: str | None = None
    yil: int | None = None

    for m in _YIL.finditer(sadesi):
        if int(m.group(1)) <= date.today().year + 1:
            yil = int(m.group(1))
            silinecek.append(m.span())

    for desen, deger in _HER_YERDE:
        for m in re.finditer(rf"(?<!\w)(?:{desen})(?!\w)", sadesi):
            if any(b < m.end() and m.start() < s for b, s in silinecek):
                continue
            if deger in (DIL_DUBLAJ, DIL_ALTYAZI):
                dil = dil or deger
            else:
                sira = sira or deger
            silinecek.append(m.span())

    kalan = ham
    for bas, son in sorted(silinecek, reverse=True):
        kalan = kalan[:bas] + " " + kalan[son:]
    sozcukler = kalan.split()
    while len(sozcukler) > 1 and sade(sozcukler[-1]) in _SONDA:
        sira = sira or _SONDA[sade(sozcukler.pop())]
    baslik = " ".join(sozcukler).strip(" -:,")

    if not baslik:
        return Niyet(baslik=ham)
    return Niyet(baslik=baslik, dil=dil, yil=yil, sira=sira)


# ── Yapım yılı ───────────────────────────────────────────────────────────────

_BASLIK_YILI = re.compile(r"\(((?:19|20)\d{2})\)")
_ADRES_YILI  = re.compile(r"[-_/]((?:19|20)\d{2})(?=[-_/.]|$)")
_DIZI_ADRESI  = re.compile(r"/(dizi|diziler|series?)/")
_YUKLEME_YILI = re.compile(r"/uploads/((?:19|20)\d{2})/\d{2}/")


def yapim_yili(oge: dict, tmdb_yili: int | None = None, tmdb_turu: str | None = None) -> int | None:
    """Kartın yapım yılı. Öncelik: başlıktaki "(2016)", adres kısa adındaki
    "-2016-izle", sonra TMDB.

    TMDB başlıkla aranıyor: aynı adı taşıyan 2022 dizisine 2026 filminin yılını
    veriyor ("Resident Evil"). Sağlayıcının kendi adresi daha güvenilir.
    Poster adresindeki "/2022/06/" yükleme tarihidir, yapım yılı değil — yalnız
    TMDB yılına tavan olarak kullanılır.
    """
    baslik = str(oge.get("title") or "")
    m = _BASLIK_YILI.search(baslik)
    if m:
        return int(m.group(1))

    tavan = date.today().year + 1
    adres = unquote_plus(str(oge.get("url") or "")).rstrip("/")
    kisa  = adres.rsplit("/", 1)[-1]
    for aday in reversed(_ADRES_YILI.findall(f"/{kisa}")):
        # "blade-runner-2049", "1917": sayı başlığın parçasıysa yıl değildir.
        if int(aday) <= tavan and aday not in baslik:
            return int(aday)

    # TMDB başka türü eşleştirdiyse yılı o içeriğin değil: dizi sitesindeki
    # "Resident Evil" (2022 dizisi) TMDB'de 2026 filmine düşüyordu.
    tur = "tv" if _DIZI_ADRESI.search(adres) else "movie" if "/film/" in adres else None
    if tur and tmdb_turu and tur != tmdb_turu:
        return None

    # Poster "/uploads/2022/06/" yüklendiyse içerik 2022'den yeni olamaz: TMDB
    # aynı adlı 2026 filmini eşleştirmiş demektir (DiziMom "Resident Evil" dizisi).
    yukleme = _YUKLEME_YILI.search(str(oge.get("poster") or ""))
    if tmdb_yili and yukleme and int(yukleme.group(1)) < tmdb_yili:
        return int(yukleme.group(1))
    return tmdb_yili


# ── Tekilleştirme ────────────────────────────────────────────────────────────

def baslik_anahtarlari(baslik: str) -> list[str]:
    """Birleştirme anahtarları: tam başlık ve "TR - EN" parçaları, noktalamasız.

    "Ölümcül Deney 2: Kıyamet - Resident Evil: Apocalypse" (HDFilmCehennemi) ile
    "Ölümcül Deney 2: Kıyamet" (JetFilmizle) aynı karttır.
    """
    def norm(metin: str) -> str:
        return " ".join(re.sub(r"[^\w]+", " ", sade(metin)).split())

    parcalar = [baslik] + re.split(r"\s[-–—]\s", baslik)
    return list(dict.fromkeys(k for k in (norm(p) for p in parcalar) if k))


def _ayni_yapim(anahtar: str, yil_a: int | None, yil_b: int | None) -> bool:
    if yil_a == yil_b:
        return True
    # Yılı bir tarafta bilinmiyorsa yalnız uzun, ayırt edici anahtar birleştirir:
    # "resident evil the final chapter" tek içeriktir, "resident evil" film de
    # olabilir dizi de.
    # ponytail: sözcük sayısı sezgisi; TMDB kimliği gelirse onunla anahtarla.
    return (yil_a is None or yil_b is None) and len(anahtar.split()) >= 3


def tekillestir(ogeler: list[dict]) -> list[dict]:
    """Aynı içeriği tek karta indirir; sağlayıcılar `providers` altına iner.

    Anahtar: normalize başlık (veya "TR - EN" parçalarından biri) + yıl. Yılı
    ikisinde de bilinen kartlar yalnız aynı yıldaysa birleşir — 2002 filmi 2022
    dizisini yutmasın. Başlıksız öğe poster adresiyle, o da yoksa adresiyle
    anahtarlanır.

    Girdi sıralı olmalı (sağlayıcı puanı): ilk gelen temsilci olur, kart onu açar.
    """
    kartlar: list[dict] = []
    dizin: dict[str, list[dict]] = {}

    for oge in ogeler:
        yil = oge.get("year")
        anahtarlar = baslik_anahtarlari(str(oge.get("title") or ""))
        if not anahtarlar:
            anahtarlar = [f"@{oge.get('poster') or oge.get('url') or id(oge)}"]

        kart = next(
            (k for a in anahtarlar for k in dizin.get(a, []) if _ayni_yapim(a, k.get("year"), yil)),
            None,
        )
        saglayici = {"plugin": oge.get("plugin") or "", "url": oge.get("url") or ""}
        if kart is None:
            kart = {**oge, "providers": [saglayici]}
            kartlar.append(kart)
        else:
            if not any(s["plugin"] == saglayici["plugin"] for s in kart["providers"]):
                kart["providers"].append(saglayici)
            # Poster/dil/yıl ilk gelende boş olabiliyor; grupta dolu olan kazanır.
            for alan in ("poster", "lang", "year"):
                if not kart.get(alan) and oge.get(alan):
                    kart[alan] = oge[alan]
        for a in anahtarlar:
            if kart not in dizin.setdefault(a, []):
                dizin[a].append(kart)

    return kartlar


# ── Sıralama ─────────────────────────────────────────────────────────────────

_DIL_SIRASI = {
    DIL_DUBLAJ : {"DUB": 0, "ALT": 1},
    DIL_ALTYAZI: {"ALT": 0},
}


def _dil_derecesi(kart: dict, dil: str | None) -> int:
    if not dil:
        return 0
    tablo = _DIL_SIRASI[dil]
    rozetler = list(kart.get("lang") or [])
    # Başlıktaki etiket de rozet sayılır ("… Türkçe Dublaj izle").
    baslik = sade(kart.get("title"))
    if "dublaj" in baslik:
        rozetler.append("DUB")
    if "altyazi" in baslik:
        rozetler.append("ALT")
    return min((tablo.get(r, len(tablo)) for r in rozetler), default=len(tablo))


def sirala(kartlar: list[dict], niyet: Niyet) -> list[dict]:
    """Niyete göre kararlı sıralama; eşitlikte gelen sıra (alaka + puan) korunur.

    - Aile önce: başlıkta sorgunun bütün sözcükleri geçen kartlar ("Kiracı - The
      Resident" "resident evil" ailesi değildir).
    - `yil`: o yılın yapımı başa.
    - `sira=seri`: aile yıla göre ARTAN (2002, 2004, … en yeni).
    - Diğer her durum (varsayılan dahil): aile yıla göre AZALAN — en yeni başta.
    - `dil`: dublaj istendiyse DUB, sonra ALT; seride yıl sırasını bozmaz.
    Yılı bilinmeyen kart kendi ailesinin sonuna düşer.
    """
    sozcukler = [k for k in sade(niyet.baslik).split() if len(k) > 1]

    def aileden(kart: dict) -> bool:
        baslik = sade(kart.get("title"))
        return bool(sozcukler) and all(s in baslik for s in sozcukler)

    def anahtar(kart: dict) -> tuple:
        yil = kart.get("year")
        yilsiz = yil is None
        yil_yonu = (yil or 0) if niyet.sira == SIRA_SERI else -(yil or 0)
        dil = _dil_derecesi(kart, niyet.dil)
        yil_isabet = 0 if (niyet.yil is None or yil == niyet.yil) else 1
        if niyet.sira == SIRA_SERI:
            return (not aileden(kart), yil_isabet, yilsiz, yil_yonu, dil)
        return (not aileden(kart), yil_isabet, dil, yilsiz, yil_yonu)

    return sorted(kartlar, key=anahtar)
