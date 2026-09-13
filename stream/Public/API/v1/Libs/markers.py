# NetMovies — Bölüm işaretleri (açılış / jenerik) altyazıdan çıkarımı
#
# NEDEN ALTYAZI: kaynaklar (DiziPal, SezonlukDizi, HDFilmCehennemi…) HLS akışında
# chapter metadata vermiyor; ses parmak izi ise her bölümü indirip ffmpeg'e sokmak
# demek (imajlarda ffmpeg kapalı — engine/Dockerfile:20). Altyazı dosyası zaten
# birkaç yüz KB metin ve iki bilgiyi bedava taşıyor:
#   · açılış şarkısı → ♪ / # / [müzik] işaretli, başa yakın ardışık cue bloğu
#   · jenerik        → son diyalog cue'sundan sonraki uzun sessizlik
#
# Çıkarım TAHMİNDİR: emin olunamayan işaret None döner. Yanlış yerde "atla"
# düğmesi göstermektense hiç göstermemek yeğdir.

from __future__ import annotations

import re

# Cue zaman satırı: "00:01:23.456 --> 00:01:25.000" (VTT) veya virgüllü (SRT).
_TIME_LINE = re.compile(
    r"(\d{1,2}):(\d{2}):(\d{2})[.,](\d{1,3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[.,](\d{1,3})"
)
# Altyazıda "burada diyalog değil müzik var" demenin yaygın yolları.
_MUSIC = re.compile(r"[♪♫]|^\s*#|\[\s*(m[üu]zik|music|şarkı|sarki|theme|intro)\s*\]", re.IGNORECASE | re.MULTILINE)

# Eşikler — hepsi "gerçek diziler böyle" gözlemi, sihir değil.
INTRO_ARAMA_ORANI = 0.30    # açılış ilk %30'da aranır (uzun bölümde de baştadır)
INTRO_MIN_SN      = 15.0    # 15 sn'den kısa müzik bloğu açılış değil, sahne müziğidir
INTRO_MAX_SN      = 210.0   # 3.5 dk'dan uzunsa bu açılış değil, müzikli bölümdür
INTRO_BOSLUK_SN   = 12.0    # iki müzik cue'su arası bu kadar açıksa blok bitmiştir
JENERIK_MIN_SN    = 45.0    # bu kadar sessizlik varsa jeneriktir
JENERIK_MAX_SN    = 600.0   # 10 dk'dan fazlaysa altyazı eksik, jenerik değil
JENERIK_ARAMA_ESIGI = 0.70  # jenerik bölümün son %30'unda aranır; ortadaki sessiz
                            # sahne (aksiyon, diyalogsuz montaj) jenerik sayılmasın


def cue_ayristir(metin: str) -> list[tuple[float, float, str]]:
    """VTT/SRT metnini (başlangıç_sn, bitiş_sn, gövde) listesine çevirir.

    Biçim ayrımı yapılmaz: iki biçimin de zaman satırı aynı, tek fark ondalık
    ayracı. Zaman satırı bulunamayan blok sessizce atlanır.
    """
    cueler: list[tuple[float, float, str]] = []
    for eslesme in _TIME_LINE.finditer(metin):
        s = _sn(eslesme.group(1), eslesme.group(2), eslesme.group(3), eslesme.group(4))
        e = _sn(eslesme.group(5), eslesme.group(6), eslesme.group(7), eslesme.group(8))
        if e <= s:
            continue
        # Gövde: zaman satırının sonundan bir sonraki boş satıra kadar.
        kuyruk = metin[eslesme.end():]
        govde  = kuyruk.split("\n\n")[0].split("\r\n\r\n")[0].strip()
        cueler.append((s, e, govde))
    cueler.sort(key=lambda c: c[0])
    return cueler


def _sn(sa: str, dk: str, sn: str, ms: str) -> float:
    return int(sa) * 3600 + int(dk) * 60 + int(sn) + int(ms.ljust(3, "0")) / 1000.0


def acilis_bul(cueler: list[tuple[float, float, str]], sure_sn: float) -> tuple[float, float] | None:
    """Açılış şarkısı aralığı — başa yakın, ardışık müzik cue'ları bloğu."""
    if not cueler or sure_sn <= 0:
        return None

    sinir  = sure_sn * INTRO_ARAMA_ORANI
    muzik  = [c for c in cueler if c[0] <= sinir and _MUSIC.search(c[2])]
    if not muzik:
        return None

    # En uzun ardışık bloğu seç: sahne arası kısa müzik cue'ları da eşleşir,
    # açılış olan en geniş olanıdır.
    en_iyi: tuple[float, float] | None = None
    blok_bas, blok_son = muzik[0][0], muzik[0][1]
    for bas, son, _ in muzik[1:]:
        if bas - blok_son <= INTRO_BOSLUK_SN:
            blok_son = max(blok_son, son)
        else:
            en_iyi = _daha_uzun(en_iyi, (blok_bas, blok_son))
            blok_bas, blok_son = bas, son
    en_iyi = _daha_uzun(en_iyi, (blok_bas, blok_son))

    if en_iyi is None:
        return None
    uzunluk = en_iyi[1] - en_iyi[0]
    if not (INTRO_MIN_SN <= uzunluk <= INTRO_MAX_SN):
        return None
    return en_iyi


def _daha_uzun(a: tuple[float, float] | None, b: tuple[float, float]) -> tuple[float, float]:
    if a is None:
        return b
    return b if (b[1] - b[0]) > (a[1] - a[0]) else a


def jenerik_bul(cueler: list[tuple[float, float, str]], sure_sn: float) -> float | None:
    """Jenerik başlangıcı — bölümün sonuna yakın EN UZUN sessizlik.

    "Son replikten sonrası jeneriktir" YETMİYOR: gerçek bölümlerde jeneriğin
    ARDINDAN tanıtım cue'su geliyor ("TÜM BÖLÜMLERİ ŞİMDİ İZLEYİN"), o zaman son
    replik jeneriğin BİTTİĞİ yeri işaretler. Bu yüzden cue'lar ARASINDAKİ büyük
    boşluklar da aday sayılır.
    """
    if not cueler or sure_sn <= 0:
        return None

    en_erken = sure_sn * JENERIK_ARAMA_ESIGI    # bundan önceki boşluk jenerik değil
    # (boşluk_uzunluğu, başlangıç) — EN UZUN boşluk kazanır. En erkeni seçmek
    # yanıltıyordu: ölçülen bölümde (DiziYou/One Piece, 63 dk) son çeyrekte 75 ve
    # 89 sn'lik sessiz SAHNELER vardı, jenerik 160 sn'lik boşluktu. Sessiz sahne
    # olur, jenerik kadar uzun sessizlik olmaz.
    adaylar: list[tuple[float, float]] = []

    # Sondaki sessizlik.
    son_replik = max(c[1] for c in cueler)
    if JENERIK_MIN_SN <= (sure_sn - son_replik) <= JENERIK_MAX_SN and son_replik >= en_erken:
        adaylar.append((sure_sn - son_replik, son_replik))

    # Cue'lar arasındaki boşluklar.
    for (_, onceki_bitis, _), (sonraki_bas, _, _) in zip(cueler, cueler[1:]):
        if onceki_bitis < en_erken:
            continue
        bosluk = sonraki_bas - onceki_bitis
        if JENERIK_MIN_SN <= bosluk <= JENERIK_MAX_SN:
            adaylar.append((bosluk, onceki_bitis))

    return max(adaylar)[1] if adaylar else None


def isaretleri_cikar(metin: str, sure_sn: float) -> dict:
    """Altyazı metni + toplam süre → {intro_start, intro_end, credits_start, source}.

    Bulunamayan işaret None kalır; istemci None gördüğünde o özelliği hiç göstermez.
    """
    cueler = cue_ayristir(metin)
    acilis = acilis_bul(cueler, sure_sn)
    return {
        "intro_start"   : round(acilis[0], 1) if acilis else None,
        "intro_end"     : round(acilis[1], 1) if acilis else None,
        "credits_start" : (lambda j: round(j, 1) if j is not None else None)(jenerik_bul(cueler, sure_sn)),
        "source"        : "subtitle",
        "cue_count"     : len(cueler),
    }
