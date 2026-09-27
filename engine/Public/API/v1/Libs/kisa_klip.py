from urllib.parse import parse_qs, urlparse


# Bölüm sayılabilecek en kısa süre (sn); istemcideki MIN_GECERLI_SURE_MS ile aynı.
KISA_KLIP_SN = 90.0


def kisa_klip_mi(url: str | None) -> bool:
    """Adres süresini kendisi söylüyorsa (googlevideo `dur=`) ve bölüm olamayacak kadar kısaysa True."""
    try:
        dur = parse_qs(urlparse(url or "").query).get("dur")
        return bool(dur) and float(dur[0]) < KISA_KLIP_SN
    except ValueError:
        return False
