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
import time
import unicodedata

from pathlib      import Path
from urllib.parse import parse_qs, quote_plus, urlparse

from KekikStream.Core import Episode, ExtractResult, MainPageResult, MovieInfo, PluginBase, SearchResult, SeriesInfo
from Plugins.__warp_client import WARP_PROXY, ytdlp_info

# Playlist filtresi (sp=EgIQAw==): arama yalnız oynatma listesi döndürür.
_ARAMA    = "https://www.youtube.com/results?search_query={}&sp=EgIQAw%253D%253D"
_YAYINCI  = ("atv", "show tv", "star tv", "kanal d", "trt", "now", "tv8", "fox", "tabii", "exxen", "gain")
_BOLUM_NO = re.compile(r"(\d+)\s*\.?\s*b[öo]l[üu]m|b[öo]l[üu]m\s*(\d+)|episode\s*(\d+)", re.I)
_SEZON_NO = re.compile(r"(\d+)\s*\.?\s*sezon|season\s*(\d+)", re.I)
# Elle seçilmiş listeler (Dean, 4 Ekim): resmi kanalı olmayan yayınlar, ör. Kaos
# Show sunucunun kendi kanalında ("Hayrettin"). Yeni yayın = bu dosyaya bir satır
# + `docker compose up -d --build engine`.
_LISTEM: list[dict] = json.loads((Path(__file__).with_name("youtube_listem.json")).read_text("utf-8"))
# Fragman/kesit 20 dakikayı geçmez; tam bölüm 40+ dakika.
_MIN_SURE = 20 * 60


def _sade(metin: str) -> str:
    metin = unicodedata.normalize("NFKD", (metin or "").replace("İ", "i").replace("ı", "i").lower())
    return re.sub(r"[^a-z0-9]+", " ", "".join(c for c in metin if not unicodedata.combining(c))).strip()


def kanal_dizinin_mi(sorgu: str, kanal: str) -> bool:
    """Kanal dizinin kendi kanalı mı ("A.B.İ." ↔ "abi": noktalama/boşluk sayılmaz)."""
    s, k = _sade(sorgu).replace(" ", ""), _sade(kanal).replace(" ", "")
    return bool(s) and k == s


def resmi_mi(sorgu: str, kanal: str, liste: str) -> bool:
    """Kanal dizinin kendi kanalı mı, ya da bilinen yayıncı + liste adı diziyi taşıyor mu."""
    s, k, l = _sade(sorgu), _sade(kanal), _sade(liste)
    if not s:
        return False
    if kanal_dizinin_mi(sorgu, kanal):
        return True
    return any(k == y or k.startswith(y + " ") for y in _YAYINCI) and s in l


def video_mu(url: str) -> bool:
    """Tek video adresi mi (watch?v= / youtu.be); arama ve liste sayfası değil."""
    p = urlparse(url or "")
    return bool(parse_qs(p.query).get("v")) or p.netloc.endswith("youtu.be")


def bolum_numarasi(baslik: str) -> tuple[int, int] | None:
    """'A.B.İ. 16. Bölüm @atvturkiye' → (1, 16); 'Episode 20' → (1, 20)."""
    m = _BOLUM_NO.search(baslik or "")
    if not m:
        return None
    no = int(next(g for g in m.groups() if g))
    z  = _SEZON_NO.search(baslik or "")
    return (int(next(g for g in z.groups() if g)) if z else 1, no)


def liste_bolumleri(entries: list[dict]) -> list[Episode]:
    """Resmi listede bölüm no başlıktan (fragman elenir); numarasız listede sıra = bölüm."""
    bolumler: dict[tuple[int, int], Episode] = {}
    for e in entries:
        no = bolum_numarasi(e.get("title") or "")
        if not no or (e.get("duration") or 0) < _MIN_SURE or not e.get("id"):
            continue
        # Aynı bölüm iki kez yüklenmiş olabilir (ör. "Episode 20" + "20. Bölüm"): ilki kalır.
        bolumler.setdefault(no, Episode(season=no[0], episode=no[1], title=f"{no[1]}. Bölüm",
                                        url=f"https://www.youtube.com/watch?v={e['id']}"))
    if bolumler:
        return [bolumler[k] for k in sorted(bolumler)]
    # Aramadan açılan sıradan liste (konser, belgesel, program): listedeki sırayla.
    videolar = [e for e in entries if e.get("id")]
    return [Episode(season=1, episode=i + 1, title=e.get("title") or f"{i + 1}. video",
                    url=f"https://www.youtube.com/watch?v={e['id']}") for i, e in enumerate(videolar)]


# Arama + liste okuma soğukta ~15 sn; hızlı yolun 15 sn bütçesine bölüm bağlantısı
# (yt-dlp ~8 sn) kalmıyordu. Liste nadiren değişir: 1 saat bellekte.
_ONBELLEK: dict[tuple, tuple[float, dict | None]] = {}
_ONBELLEK_SURE = 3600


async def _ytdlp_json(url: str, *ek: str, timeout: float = 60.0) -> dict | None:
    anahtar = (url, ek)
    kayit = _ONBELLEK.get(anahtar)
    if kayit and time.time() - kayit[0] < _ONBELLEK_SURE:
        return kayit[1]
    sonuc = await _ytdlp_json_cek(url, *ek, timeout=timeout)
    if sonuc is not None:
        _ONBELLEK[anahtar] = (time.time(), sonuc)
    return sonuc


async def _ytdlp_json_cek(url: str, *ek: str, timeout: float = 60.0) -> dict | None:
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
    main_page   = {"listem": "YouTube Listem"}

    async def get_main_page(self, page: int, url: str, category: str) -> list[MainPageResult]:
        if page > 1:
            return []
        return [MainPageResult(category=category, title=l["title"], url=l["url"], poster=l.get("poster")) for l in _LISTEM]

    async def search(self, query: str) -> list[SearchResult]:
        s = _sade(query)
        secili = [SearchResult(title=l["title"], url=l["url"], poster=l.get("poster")) for l in _LISTEM if _sade(l["title"]) == s]
        if secili:
            return secili   # elle seçilmiş liste aramaya gerek bırakmaz
        d = await _ytdlp_json(_ARAMA.format(quote_plus(f"{query} bölümler")), "--playlist-items", "1-10")
        sonuc = []
        for e in (d or {}).get("entries") or []:
            kanal, baslik = e.get("channel") or e.get("uploader") or "", e.get("title") or ""
            if e.get("url") and resmi_mi(query, kanal, baslik):
                # Kart adı dizinin adı olmalı (eşleştirme başlıkla yapılıyor), liste adı değil.
                # Dizinin kendi kanalıysa kanal adı: DDizi "Abi" diye arıyor, kart "A.B.İ."
                # kalsın ki izleme geçmişi ve kayıt eşleşmesi tek ada toplansın.
                ad = kanal if kanal_dizinin_mi(query, kanal) else query
                sonuc.append(SearchResult(title=ad, url=e["url"], poster=(e.get("thumbnails") or [{}])[-1].get("url")))
        return sonuc

    async def load_item(self, url: str) -> SeriesInfo | MovieInfo:
        # Arama adresi de kabul edilir: zincir "<dizi> için resmi liste" diye sorabilsin.
        arama = "/results?" in url
        if arama:
            sorgu = parse_qs(urlparse(url).query).get("search_query", [""])[0]
            bulunan = await self.search(sorgu)
            if not bulunan:
                return SeriesInfo(url=url, title=sorgu, episodes=[])
            url = bulunan[0].url
        d = await _ytdlp_json(url, timeout=90.0) or {}
        kapak = (d.get("thumbnails") or [{}])[-1].get("url")
        # Aramadan açılan tek video film gibi oynar; açıklama/yorum taşınmaz (Dean: "derli toplu").
        if video_mu(url) and not d.get("entries"):
            kimlik = d.get("id") or ""
            return MovieInfo(url=url, title=d.get("title") or "", poster=kapak or f"https://i.ytimg.com/vi/{kimlik}/hqdefault.jpg")
        bolumler = liste_bolumleri(d.get("entries") or [])
        # Çekirdek "1. Bölüm" gibi jenerik adı None yapıyor: None = resmi bölüm adı.
        resmi    = bool(bolumler) and all(not b.title or b.title.endswith(". Bölüm") for b in bolumler)
        # Arama adresi = zincir "resmi bölüm listesi" soruyor. Adı diziyle aynı
        # rastgele kanal (Lioness → "LioNess" yemek kanalı) numarasız liste verir;
        # sıra numarası bölüm sanılınca dizi yerine yemek videosu oynuyordu.
        if arama and not resmi:
            bolumler = []
        return SeriesInfo(
            url         = url,
            # Resmi listede kanal = dizinin adı; sıradan listede liste adı anlamlı.
            title       = next((l["title"] for l in _LISTEM if l["url"] == url), None)
                          or (d.get("channel") if resmi else d.get("title")) or d.get("title") or "",
            poster      = kapak,
            description = d.get("description") or None,
            episodes    = bolumler,
        )

    async def load_links(self, url: str) -> list[ExtractResult]:
        # Hızlı yol kart adresi olarak arama/liste adresi gönderir; `_links_for` önce
        # load_links'i dener. yt-dlp o sayfada her videoyu tek tek açıyor (60+ sn),
        # motorun 30 sn'si burada bitiyordu. Yalnız video adresi çözülür.
        if not video_mu(url):
            return []
        info = await ytdlp_info(url, timeout=90.0)
        master = next((f["manifest_url"] for f in (info or {}).get("formats") or [] if f.get("manifest_url")), None)
        if not master:
            return []
        return [ExtractResult(name=f"{self.name} | Resmi", url=master, referer="", user_agent="", subtitles=[])]
