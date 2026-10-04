# YouTube (resmi) — yerli dizilerin yayıncı kanalındaki tam bölüm oynatma listeleri.
#
# Neredeyse her yerli dizinin resmi kanalı "Bölümler" listesi tutuyor (A.B.İ. →
# kanal "A.B.İ.", 22 bölüm, 1080p). Sitelerin 360p kopyaları bu videoların
# kendisi. Liste = dizi kartı, video = bölüm; oynatma yt-dlp'nin HLS master'ı
# (ses+görüntü birleşik, 1080p'ye kadar).
#
# Yalnız RESMİ liste kabul edilir: kanal adı dizi adıyla aynı ya da bilinen bir
# yayıncı. Hayran yüklemeleri (yeniden kesilmiş, eksik) ve fragman listeleri elenir.
from __future__ import annotations

import asyncio
import json
import re
import unicodedata

from urllib.parse import parse_qs, quote_plus, urlparse

from KekikStream.Core import Episode, ExtractResult, PluginBase, SearchResult, SeriesInfo
from Plugins.__warp_client import WARP_PROXY, ytdlp_info

# Playlist filtresi (sp=EgIQAw==): arama yalnız oynatma listesi döndürür.
_ARAMA    = "https://www.youtube.com/results?search_query={}&sp=EgIQAw%253D%253D"
_YAYINCI  = ("atv", "show tv", "star tv", "kanal d", "trt", "now", "tv8", "fox", "tabii", "exxen", "gain")
_BOLUM_NO = re.compile(r"(\d+)\s*\.?\s*b[öo]l[üu]m|b[öo]l[üu]m\s*(\d+)|episode\s*(\d+)", re.I)
_SEZON_NO = re.compile(r"(\d+)\s*\.?\s*sezon|season\s*(\d+)", re.I)
# Fragman/kesit 20 dakikayı geçmez; tam bölüm 40+ dakika.
_MIN_SURE = 20 * 60


def _sade(metin: str) -> str:
    metin = unicodedata.normalize("NFKD", (metin or "").replace("İ", "i").replace("ı", "i").lower())
    return re.sub(r"[^a-z0-9]+", " ", "".join(c for c in metin if not unicodedata.combining(c))).strip()


def resmi_mi(sorgu: str, kanal: str, liste: str) -> bool:
    """Kanal dizinin kendi kanalı mı, ya da bilinen yayıncı + liste adı diziyi taşıyor mu."""
    s, k, l = _sade(sorgu), _sade(kanal), _sade(liste)
    if not s:
        return False
    if k == s or k.replace(" ", "") == s.replace(" ", ""):
        return True
    return any(k == y or k.startswith(y + " ") for y in _YAYINCI) and s in l


def bolum_numarasi(baslik: str) -> tuple[int, int] | None:
    """'A.B.İ. 16. Bölüm @atvturkiye' → (1, 16); 'Episode 20' → (1, 20)."""
    m = _BOLUM_NO.search(baslik or "")
    if not m:
        return None
    no = int(next(g for g in m.groups() if g))
    z  = _SEZON_NO.search(baslik or "")
    return (int(next(g for g in z.groups() if g)) if z else 1, no)


async def _ytdlp_json(url: str, *ek: str, timeout: float = 60.0) -> dict | None:
    proc = await asyncio.create_subprocess_exec(
        "yt-dlp", "--no-warnings", "--flat-playlist", "-J", "--proxy", WARP_PROXY, *ek, url,
        stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE,
    )
    try:
        stdout, _ = await asyncio.wait_for(proc.communicate(), timeout=timeout)
    except asyncio.TimeoutError:
        proc.kill()
        return None
    try:
        return json.loads(stdout) if proc.returncode == 0 and stdout else None
    except ValueError:
        return None


class YouTube(PluginBase):
    name        = "YouTube"
    language    = "tr"
    main_url    = "https://www.youtube.com"
    favicon     = "https://www.google.com/s2/favicons?domain=youtube.com&sz=64"
    description = "Yerli dizilerin resmi YouTube kanallarındaki tam bölüm listeleri (1080p)."
    main_page   = {}

    async def get_main_page(self, page: int, url: str, category: str) -> list:
        return []

    async def search(self, query: str) -> list[SearchResult]:
        d = await _ytdlp_json(_ARAMA.format(quote_plus(f"{query} bölümler")), "--playlist-items", "1-10")
        sonuc = []
        for e in (d or {}).get("entries") or []:
            kanal, baslik = e.get("channel") or e.get("uploader") or "", e.get("title") or ""
            if e.get("url") and resmi_mi(query, kanal, baslik):
                # Kart adı dizinin adı olmalı (eşleştirme başlıkla yapılıyor), liste adı değil.
                ad = kanal if _sade(kanal) == _sade(query) else query
                sonuc.append(SearchResult(title=ad, url=e["url"], poster=(e.get("thumbnails") or [{}])[-1].get("url")))
        return sonuc

    async def load_item(self, url: str) -> SeriesInfo:
        # Arama adresi de kabul edilir: zincir "<dizi> için resmi liste" diye sorabilsin.
        if "/results?" in url:
            sorgu = parse_qs(urlparse(url).query).get("search_query", [""])[0]
            bulunan = await self.search(sorgu)
            if not bulunan:
                return SeriesInfo(url=url, title=sorgu, episodes=[])
            url = bulunan[0].url
        d = await _ytdlp_json(url, timeout=90.0) or {}
        bolumler: dict[tuple[int, int], Episode] = {}
        for e in d.get("entries") or []:
            no = bolum_numarasi(e.get("title") or "")
            if not no or (e.get("duration") or 0) < _MIN_SURE or not e.get("id"):
                continue
            # Aynı bölüm iki kez yüklenmiş olabilir (ör. "Episode 20" + "20. Bölüm"): ilki kalır.
            bolumler.setdefault(no, Episode(season=no[0], episode=no[1], title=f"{no[1]}. Bölüm",
                                            url=f"https://www.youtube.com/watch?v={e['id']}"))
        kapak = (d.get("thumbnails") or [{}])[-1].get("url")
        return SeriesInfo(
            url         = url,
            title       = d.get("channel") or d.get("title") or "",
            poster      = kapak,
            description = d.get("description") or None,
            episodes    = [bolumler[k] for k in sorted(bolumler)],
        )

    async def load_links(self, url: str) -> list[ExtractResult]:
        info = await ytdlp_info(url, timeout=90.0)
        master = next((f["manifest_url"] for f in (info or {}).get("formats") or [] if f.get("manifest_url")), None)
        if not master:
            return []
        return [ExtractResult(name=f"{self.name} | Resmi", url=master, referer="", user_agent="", subtitles=[])]
