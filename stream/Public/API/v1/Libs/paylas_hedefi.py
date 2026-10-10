# "Paylaş → TV'de aç": paylaşılan bağlantı bir sağlayıcının sayfasıysa TV onu
# web görünümünde değil NetMovies oynatıcısında açar (reklamsız, kaldığın yerden,
# kaynak zinciri). Karar sunucuda: telefon uygulaması güncellenmeden çalışır.
#
# Sağlayıcı domainleri otomatik değişir (dizipal2225 → dizipal2230); eşleşme önce
# tam konak, sonra rakamları atılmış marka adıyla (`dizipal`) yapılır.

import re

from urllib.parse import urlparse

# Kendi sayfası olmayan / oynatma sayfası olmayan kayıtlar eşleşmeye girmez.
_ATLA = {"M3U Listelerim"}


def _marka(konak: str) -> str:
    """'www.dizipal2225.com' → 'dizipal'; 'm.youtube.com' → 'youtube'."""
    parcalar = [p for p in konak.lower().split(".") if p not in ("www", "m")]
    ad = parcalar[-2] if len(parcalar) >= 2 else (parcalar[0] if parcalar else "")
    return re.sub(r"\d+", "", ad)


def youtube_video_mu(url: str) -> bool:
    p = urlparse(url)
    konak = (p.hostname or "").lower()
    if konak.endswith("youtu.be"):
        return len(p.path.strip("/")) > 0
    if not konak.endswith("youtube.com"):
        return False
    return "v=" in p.query or p.path.startswith(("/shorts/", "/live/"))


def youtube_izleme_adresi(url: str) -> str:
    """youtu.be / shorts / live → tek biçim watch?v=… (YouTube eklentisi bunu tanır)."""
    p = urlparse(url)
    konak = (p.hostname or "").lower()
    if konak.endswith("youtu.be"):
        kimlik = p.path.strip("/").split("/")[0]
    elif p.path.startswith(("/shorts/", "/live/")):
        kimlik = p.path.split("/")[2]
    else:
        return url
    return f"https://www.youtube.com/watch?v={kimlik}"


def eklenti_bul(url: str, eklentiler: list[dict]) -> str | None:
    """Paylaşılan adres hangi eklentinin sitesinde? Bulunamazsa None (sayfa açılır)."""
    konak = (urlparse(url).hostname or "").lower()
    if not konak:
        return None
    if "youtube.com" in konak or konak.endswith("youtu.be"):
        # Kanal/arama sayfası oynatılamaz; yalnız tek video oynatıcıya gider.
        return "YouTube" if youtube_video_mu(url) else None
    aday = [e for e in eklentiler if e.get("name") not in _ATLA and str(e.get("main_url") or "").startswith("http")]
    for e in aday:
        if (urlparse(e["main_url"]).hostname or "").lower().removeprefix("www.") == konak.removeprefix("www."):
            return e["name"]
    marka = _marka(konak)
    if len(marka) < 4:   # 'tv', 'co' gibi kısa adlar yanlış eşleşir
        return None
    for e in aday:
        if _marka(urlparse(e["main_url"]).hostname or "") == marka:
            return e["name"]
    return None


def oynatma_komutu(url: str, eklentiler: list[dict]) -> dict | None:
    """Sağlayıcıya aitse `play` komutu, değilse None."""
    eklenti = eklenti_bul(url, eklentiler)
    if not eklenti:
        return None
    adres = youtube_izleme_adresi(url) if eklenti == "YouTube" else url
    return {"type": "play", "plugin": eklenti, "url": adres, "title": "", "poster": "", "episode": -1}
