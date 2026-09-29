from urllib.parse import parse_qs, urlparse


# Bölüm sayılabilecek en kısa süre (sn); istemcideki MIN_GECERLI_SURE_MS ile aynı.
KISA_KLIP_SN = 90.0


# Sürenin bilinmediği ama bölüm barındıramayan konaklar: X/Twitter videosu en çok
# birkaç dakika; DiziMom kaldırılan bölümün yerine buradan 31 sn'lik tanıtım koyuyor
# (Lioness S2B3). Süzgeç googlevideo `dur=`'a bakıyordu, bu klip TV'de "bölüm" diye açıldı.
_KLIP_KONAKLARI = ("video.twimg.com",)


def kisa_klip_mi(url: str | None) -> bool:
    """Adres süresini kendisi söylüyorsa (googlevideo `dur=`) ve bölüm olamayacak kadar kısaysa,
    ya da konak yalnız kısa klip barındırıyorsa True."""
    parca = urlparse(url or "")
    if parca.hostname in _KLIP_KONAKLARI:
        return True
    try:
        dur = parse_qs(parca.query).get("dur")
        return bool(dur) and float(dur[0]) < KISA_KLIP_SN
    except ValueError:
        return False
