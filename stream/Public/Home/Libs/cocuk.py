# NetMovies — Çocuk modu: panelden seçilen YouTube kanalları + onaylı çizgi filmler.
#
# Çocuk ekranı (/tv?profil=cocuk) YALNIZ buradan beslenir; liste sunucuda süzülür,
# istemci başka raf istemez. Kanal videoları YouTube'un kanal RSS'inden gelir
# (son 15 video, anahtarsız, stream imajında yt-dlp yok). Shorts atlanır.
# ponytail: RSS son 15 videoyu verir; daha eski arşiv gerekirse engine yt-dlp
# `--flat-playlist <kanal>/videos` yolu eklenir.

from __future__ import annotations

import asyncio
import html
import re
import time
import xml.etree.ElementTree as ET
from urllib.parse import quote, urlsplit

import httpx

from CLI import konsol

_YT        = "https://www.youtube.com"
_KANAL_ID  = re.compile(r"^UC[\w-]{22}$")
_CANONICAL = re.compile(r'<link rel="canonical" href="https://www\.youtube\.com/channel/(UC[\w-]{22})"')
_OG        = re.compile(r'<meta property="og:(title|image)" content="([^"]*)"')
_NS        = {"a": "http://www.w3.org/2005/Atom", "yt": "http://www.youtube.com/xml/schemas/2015"}
_UA        = {"User-Agent": "Mozilla/5.0", "Accept-Language": "tr"}
_ONBELLEK  : dict[str, tuple[float, list[dict]]] = {}
_ONBELLEK_SN = 1800


# --------------------------------------------------------------------------- Kanal girdisi
def kanal_adresi(girdi: str) -> str | None:
    """@handle, kanal adresi ya da UC… kimliğinden kanal sayfası adresi.
    Yalnız youtube.com: başka konak (SSRF) ve video adresi reddedilir."""
    g = str(girdi or "").strip()
    if _KANAL_ID.match(g):
        return f"{_YT}/channel/{g}"
    if g.startswith("@"):
        g = f"{_YT}/{g}"
    elif not g.startswith(("http://", "https://")):
        g = f"https://{g}" if "youtube.com" in g else f"{_YT}/@{g}"
    parca = urlsplit(g)
    konak = (parca.hostname or "").lower()
    if konak != "youtube.com" and not konak.endswith(".youtube.com"):
        return None
    yol = [p for p in parca.path.split("/") if p]
    if yol and yol[0].startswith("@") and len(yol[0]) > 1:
        return f"{_YT}/{quote(yol[0], safe='@')}"
    if len(yol) > 1 and yol[0] in ("channel", "c", "user"):
        return f"{_YT}/{yol[0]}/{quote(yol[1])}"
    return None


def kanal_sayfasi_coz(sayfa: str) -> dict | None:
    """Kanal sayfası HTML'inden {id, ad, avatar}."""
    kimlik = _CANONICAL.search(sayfa)
    if not kimlik:
        return None
    og = {k: html.unescape(v) for k, v in _OG.findall(sayfa)}
    return {"id": kimlik.group(1), "ad": og.get("title") or kimlik.group(1), "avatar": og.get("image", "")}


async def kanal_coz(girdi: str) -> dict | None:
    adres = kanal_adresi(girdi)
    if not adres:
        return None
    async with httpx.AsyncClient(timeout=10.0, follow_redirects=True, headers=_UA) as istemci:
        yanit = await istemci.get(adres)
    if yanit.status_code != 200:
        return None
    return kanal_sayfasi_coz(yanit.text)


# --------------------------------------------------------------------------- Videolar
def rss_videolari(xml: str) -> list[dict]:
    """Kanal RSS'inden video listesi; Shorts (link /shorts/) atlanır."""
    videolar = []
    for e in ET.fromstring(xml).findall("a:entry", _NS):
        kimlik = e.findtext("yt:videoId", "", _NS)
        link   = e.find("a:link", _NS)
        if not kimlik or (link is not None and "/shorts/" in (link.get("href") or "")):
            continue
        videolar.append({
            "id"    : kimlik,
            "title" : e.findtext("a:title", "", _NS),
            "url"   : f"{_YT}/watch?v={kimlik}",
            "poster": f"https://i.ytimg.com/vi/{kimlik}/hqdefault.jpg",
        })
    return videolar


async def kanal_videolari(kanal_id: str) -> list[dict]:
    """Son videolar, 30 dk önbellekli. Ağ hatasında eski liste ya da boş: raf düşer, sayfa düşmez."""
    simdi = time.time()
    kayit = _ONBELLEK.get(kanal_id)
    if kayit and simdi - kayit[0] < _ONBELLEK_SN:
        return kayit[1]
    try:
        async with httpx.AsyncClient(timeout=10.0, headers=_UA) as istemci:
            yanit = await istemci.get(f"{_YT}/feeds/videos.xml", params={"channel_id": kanal_id})
        yanit.raise_for_status()
        videolar = rss_videolari(yanit.text)
    except (httpx.HTTPError, ET.ParseError) as hata:
        konsol.log(f"[yellow]cocuk:[/] {kanal_id} RSS alınamadı: {hata}")
        return kayit[1] if kayit else []
    _ONBELLEK[kanal_id] = (simdi, videolar)
    return videolar


# --------------------------------------------------------------------------- Süzgeç
def _kucuk(metin: str) -> str:
    # Türkçe büyük/küçük: "İ"→"i", "I"→"ı"; yoksa "KIZ" "kiz" olur, "kız" yakalanmaz.
    return str(metin).replace("İ", "i").replace("I", "ı").lower()


def yasakli_mi(baslik: str, yasakli: list[str]) -> bool:
    # ponytail: düz alt-dize; "kan" "kanal"ı da yakalar — çocukta fazla gizlemek az gizlemekten iyi.
    b = _kucuk(baslik)
    return any(k and k in b for k in (_kucuk(x).strip() for x in yasakli or []))


def suz(videolar: list[dict], yasakli: list[str]) -> list[dict]:
    return [v for v in videolar if not yasakli_mi(v.get("title", ""), yasakli)]


# --------------------------------------------------------------------------- Panel kaydı
def temiz_kanallar(liste) -> list[dict]:
    out, gorulen = [], set()
    for k in (liste if isinstance(liste, list) else [])[:50]:
        if not isinstance(k, dict) or not _KANAL_ID.match(str(k.get("id") or "")) or k["id"] in gorulen:
            continue
        gorulen.add(k["id"])
        avatar = str(k.get("avatar") or "")
        yasakli = k.get("yasakli") if isinstance(k.get("yasakli"), list) else []
        out.append({
            "id"     : k["id"],
            "ad"     : str(k.get("ad") or k["id"]).strip()[:80],
            "avatar" : avatar[:500] if avatar.startswith("https://") else "",
            "yasakli": [s for s in (str(x).strip()[:40] for x in yasakli[:100]) if s],
        })
    return out


def temiz_icerik(liste) -> list[dict]:
    out, gorulen = [], set()
    for i in (liste if isinstance(liste, list) else [])[:200]:
        if not isinstance(i, dict):
            continue
        plugin, url = str(i.get("plugin") or "").strip(), str(i.get("url") or "").strip()
        if not plugin or not url or (plugin, url) in gorulen:
            continue
        gorulen.add((plugin, url))
        out.append({
            "plugin": plugin[:60],
            "title" : str(i.get("title") or "").strip()[:120],
            "url"   : url[:1000],
            "poster": str(i.get("poster") or "")[:1000],
            "tur"   : "serie" if i.get("tur") == "serie" else "movie",
        })
    return out


# --------------------------------------------------------------------------- TV rafları
async def raflar(cfg: dict) -> list[dict]:
    """Çocuk ekranının TÜM içeriği: kanal başına bir raf + "Çizgi filmler"."""
    kanallar = temiz_kanallar(cfg.get("cocuk_kanallar"))
    listeler = await asyncio.gather(*(kanal_videolari(k["id"]) for k in kanallar))
    out = []
    for kanal, videolar in zip(kanallar, listeler):
        kartlar = [{
            "plugin": "youtube", "title": v["title"], "poster": v["poster"],
            "url": quote(v["url"], safe=""), "content_url": v["url"], "tur_sat": "movie",
        } for v in suz(videolar, kanal["yasakli"])]
        if kartlar:
            out.append({"ad": kanal["ad"], "tur": "movie", "kartlar": kartlar})
    cizgi = [{
        "plugin": i["plugin"], "title": i["title"], "poster": i["poster"],
        "url": i["url"], "tur_sat": i["tur"],
    } for i in temiz_icerik(cfg.get("cocuk_icerik"))]
    if cizgi:
        out.append({"ad": "Çizgi filmler", "tur": "serie", "kartlar": cizgi})
    return out
