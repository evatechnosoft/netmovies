import re

from urllib.parse import parse_qs, urljoin, urlparse

import httpx


# Bölüm sayılabilecek en kısa süre (sn); istemcideki MIN_GECERLI_SURE_MS ile aynı.
KISA_KLIP_SN = 90.0


# X/Twitter (video.twimg.com) HEM 31 sn'lik tanıtım klibini (Lioness S2B3) HEM
# 2,5 saatlik tam bölümü (Tuzlu Kahve 3-4, Haysiyet 3) taşıyor; konağa bakarak
# karar vermek tam bölümleri de eledi. Süre playlist'ten ölçülür: master → ilk
# varyant → #EXTINF toplamı. Yalnız bu konak için, iki küçük istek.
_OLCULEN_KONAKLAR = ("video.twimg.com",)
_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36"
_client: httpx.AsyncClient | None = None


def kisa_klip_mi(url: str | None) -> bool:
    """Adres süresini kendisi söylüyorsa (googlevideo `dur=`) ve bölüm olamayacak kadar kısaysa True."""
    try:
        dur = parse_qs(urlparse(url or "").query).get("dur")
        return bool(dur) and float(dur[0]) < KISA_KLIP_SN
    except ValueError:
        return False


def hls_suresi(master: str, varyant_metni: str | None) -> float:
    """Playlist metninden süre (sn). `master` doğrudan medya playlist'iyse varyant gerekmez."""
    metin = varyant_metni if varyant_metni is not None else master
    return sum(float(x) for x in re.findall(r"#EXTINF:([\d.]+)", metin))


def _ilk_varyant(master: str, master_url: str) -> str | None:
    satirlar = [s.strip() for s in master.splitlines() if s.strip() and not s.startswith("#")]
    return urljoin(master_url, satirlar[0]) if satirlar and "#EXTINF" not in master else None


async def hls_kisa_klip_mi(url: str | None) -> bool:
    """Ölçülen konaktaki HLS kaynağı bölüm olamayacak kadar kısaysa True; ölçülemezse False."""
    global _client
    parca = urlparse(url or "")
    if parca.hostname not in _OLCULEN_KONAKLAR or not parca.path.endswith(".m3u8"):
        return False
    try:
        if _client is None:
            _client = httpx.AsyncClient(timeout=6.0, headers={"User-Agent": _UA}, follow_redirects=True)
        master = (await _client.get(url)).text
        varyant_url = _ilk_varyant(master, url)
        varyant = (await _client.get(varyant_url)).text if varyant_url else None
        sure = hls_suresi(master, varyant)
        return 0 < sure < KISA_KLIP_SN
    except Exception:
        return False
