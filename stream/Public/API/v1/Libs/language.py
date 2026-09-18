# NetMovies — kaynak dili önceliği (tek kural, tek yer).
#
# Kural: Türkçe dublaj → Türkçe altyazı → orijinal dil → dil bilinmiyor.
# Aynı gruptaki kaynakların kendi sırası korunur (stable sort), yani sağlayıcının
# kendi tercih sırası bozulmaz. TV istemcisi de aynı kuralı uygular
# (client-tv/.../SourceResolver.kt); burada sıralamak web'i ve tüm API
# istemcilerini de aynı davranışa bağlar.

from typing import Any

DUB_MARKERS = ("dublaj", "dublajlı", "tr dublaj", "dubbed")
TR_SUB_MARKERS = ("türkçe altyazı", "türkçe alt yazı", "altyazı", "alt yazı", "turkce altyazi", "türkçe", "turkish")
# Sağlayıcılar orijinal dili kendi sözcükleriyle işaretliyor: DiziPal/DiziYou
# "Orijinal" ve "Orijinal Dil", bazıları İngilizce "original"/"altyazısız" yazıyor.
# Bunlar "bilinmiyor" sayılınca listedeki her satır aynı görünüyordu (Dean, 17 Eylül:
# beş kaydın beşi de "dil bilinmiyor").
ORIGINAL_MARKERS = ("orijinal", "original", "altyazısız", "altyazisiz")

RANK_DUBBED = 0
RANK_TR_SUB = 1
RANK_ORIGINAL = 2
RANK_UNKNOWN = 3

RANK_NAMES = {
    RANK_DUBBED  : "Türkçe dublaj",
    RANK_TR_SUB  : "Türkçe altyazı",
    RANK_ORIGINAL: "orijinal dil",
    RANK_UNKNOWN : "dil bilinmiyor",
}

# Poster rozeti: üç harf, kart üstünde yer kaplamasın.
RANK_BADGES = {
    RANK_DUBBED  : "DUB",
    RANK_TR_SUB  : "ALT",
    RANK_ORIGINAL: "ORJ",
}


def _text_of(link: Any, key: str) -> str:
    value = link.get(key) if isinstance(link, dict) else getattr(link, key, "")
    return str(value or "")


def _subtitle_names(link: Any) -> str:
    subtitles = link.get("subtitles") if isinstance(link, dict) else getattr(link, "subtitles", None)
    names = []
    for sub in subtitles or []:
        names.append(_text_of(sub, "name"))
    return " ".join(names)


def language_rank(link: Any) -> int:
    """Bir kaynağın dil önceliği: 0 dublaj, 1 Türkçe altyazı, 2 bilinmiyor."""
    name = _text_of(link, "name").lower()
    if any(marker in name for marker in DUB_MARKERS):
        return RANK_DUBBED

    subs = _subtitle_names(link).lower()
    if any(marker in name for marker in TR_SUB_MARKERS) or any(marker in subs for marker in TR_SUB_MARKERS):
        return RANK_TR_SUB

    # Orijinal, altyazı kontrolünden SONRA: "Orijinal · Türkçe altyazı" gibi bir ad
    # altyazılı sayılmalı, orijinal sözcüğü onu geri almamalı.
    if any(marker in name for marker in ORIGINAL_MARKERS):
        return RANK_ORIGINAL

    return RANK_UNKNOWN


def language_name(rank: int) -> str:
    return RANK_NAMES.get(rank, RANK_NAMES[RANK_UNKNOWN])


def order_by_language(links: list) -> list:
    """Kaynakları dil tercihine göre sıralar; grup içi sıra korunur."""
    return sorted(links, key=language_rank)
