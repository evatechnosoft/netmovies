# NetMovies — DDizi eklentisi (yerli/yabancı dizi, resmi YouTube yayınları)
#
# Site bölümleri kendi CDN'inde tutmuyor: her bölüm yayıncının RESMİ YouTube
# videosuna gömülü (`ddizi.re/player/youtube.php?id=<vid>`). Doğrulandı — Gönül
# Dağı 219. Bölüm @trt1, 7336 sn, 1080p. Bu yüzden çözüm zinciri yt-dlp:
# HTML kazıma yok, site şablonu değişse de oynatma kırılmıyor.
#
#   - Dizi kartları: `/yabanci-dizi-izle`, `/eski.diziler` (`div.dizi-boxpost-cat`)
#   - Yerli dizi listesi ana sayfanın kenar çubuğunda (`ul.list_` içinde `/diziler/`)
#   - Bölüm: `/izle/<id>/<slug>-<n>-bolum-izle-*.htm`
#   - Arama POST: `/arama/` · alan adı `arama` (GET `?s=` sorguyu yok sayıyor)

from __future__ import annotations

import re

from KekikStream.Core import Episode, ExtractResult, MainPageResult, PluginBase, SearchResult, SeriesInfo
from Plugins.__kekik_domain import discover_main_url
from Plugins.__warp_client import ytdlp_info

_MAIN_URL = discover_main_url("DDizi/src/main/kotlin/com/keyiflerolsun/DDizi.kt", "https://www.ddizi.im", "DDIZI_URL")

_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"

_KART    = re.compile(r'<div class="dizi-boxpost[a-z-]*"><a href="([^"]+)" title="([^"]*)">(.*?)</a>', re.S)
_POSTER  = re.compile(r'data-src="([^"]+)"')
_LISTE   = re.compile(r'<li><a href="(https?://[^"]*/diziler/\d+/[^"]+)"[^>]*>([^<]{2,60})</a>')
_BOLUM   = re.compile(r'href="(https?://[^"]*/izle/\d+/[^"]+\.htm)"')
_BOLUM_N = re.compile(r"-(\d+)-bolum")
_YOUTUBE = re.compile(r'youtube\.php\?id=([A-Za-z0-9_-]{6,})')


class DDizi(PluginBase):
    name        = "DDizi"
    language    = "tr"
    main_url    = _MAIN_URL
    favicon     = f"https://www.google.com/s2/favicons?domain={_MAIN_URL}&sz=64"
    description = "DDizi — yerli ve yabancı diziler, yayıncının resmi bölümleri."

    main_page = {
        f"{main_url}/"                  : "Yerli Diziler",
        f"{main_url}/yabanci-dizi-izle" : "Yabancı Diziler",
        f"{main_url}/eski.diziler"      : "Eski Diziler",
    }

    def _cards(self, html: str) -> list[tuple[str, str, str | None]]:
        """Dizi kartları. Kenar çubuğu listesi posterli kart vermiyor, yalnız ad+url."""
        out: list[tuple[str, str, str | None]] = []

        for href, title, govde in _KART.findall(html):
            # Kart hem diziye (`/diziler/`) hem tek bölüme (`/izle/`) çıkabiliyor;
            # katalog dizi ister, bölüm kartı `load_item`'da boş kabuk olur.
            if "/diziler/" not in href:
                continue
            poster = _POSTER.search(govde)
            out.append((re.sub(r"\s*izle\s*$", "", title).strip(), self.fix_url(href), self.fix_url(poster.group(1)) if poster else None))

        if not out:
            out = [(baslik.strip(), self.fix_url(href), None) for href, baslik in _LISTE.findall(html)]

        return list(dict.fromkeys(out))

    async def get_main_page(self, page: int, url: str, category: str) -> list[MainPageResult]:
        if page > 1:
            return []
        response = await self.httpx.get(url, headers={"User-Agent": _UA})
        if response.status_code != 200:
            return []
        return [MainPageResult(category=category, title=t, url=u, poster=p) for t, u, p in self._cards(response.text)]

    async def search(self, query: str) -> list[SearchResult]:
        response = await self.httpx.post(
            f"{self.main_url}/arama/",
            data    = {"arama": query},
            headers = {"User-Agent": _UA, "Referer": f"{self.main_url}/"},
        )
        # Arama sayfası kenar çubuğunu da taşıyor: sorguyla ilgisiz diziler de
        # geliyor. Süzme çağıranın işi (`baslik_uyusuyor` / `_alakali`), burada
        # ham liste döner — aksi halde Türkçeleştirilmiş adlar elenir.
        return [SearchResult(title=t, url=u, poster=p) for t, u, p in self._cards(response.text)]

    async def load_item(self, url: str) -> SeriesInfo:
        response = await self.httpx.get(url, headers={"User-Agent": _UA})
        html     = response.text

        # Dizi sayfasında h1 yok; ad <title>'da: "Gönül Dağı Son Bölüm izle Full Trt1 | Ddizi"
        etiket = re.search(r"<title[^>]*>([^<]+)", html)
        title  = re.sub(r"\s*(son bölüm\s*)?izle.*$", "", (etiket.group(1) if etiket else "").split("|")[0], flags=re.I).strip()

        bolumler: list[Episode] = []
        for href in dict.fromkeys(_BOLUM.findall(html)):
            no = _BOLUM_N.search(href)
            if not no:
                continue
            numara = int(no.group(1))
            # Sezon bilgisi sitede yok; tek sezon varsayılır, sıra bölüm numarası.
            bolumler.append(Episode(season=1, episode=numara, title=f"{numara}. Bölüm", url=self.fix_url(href)))
        bolumler.sort(key=lambda e: e.episode)

        poster = re.search(r'<meta property="og:image" content="([^"]+)"', html)
        ozet   = re.search(r'<meta name="description" content="([^"]*)"', html)
        return SeriesInfo(
            url         = url,
            title       = title or url.rstrip("/").split("/")[-1],
            poster      = self.fix_url(poster.group(1)) if poster else None,
            description = ozet.group(1) if ozet else None,
            episodes    = bolumler,
        )

    async def load_links(self, url: str) -> list[ExtractResult]:
        response = await self.httpx.get(url, headers={"User-Agent": _UA})
        video    = _YOUTUBE.search(response.text)
        if not video:
            return []

        info = await ytdlp_info(f"https://www.youtube.com/watch?v={video.group(1)}", timeout=90.0)
        if not info:
            return []

        # YouTube tek formatta ses+video vermiyor (DASH: ayrı akışlar, hepsinde
        # `acodec: none`). Birleşik olan tek şey HLS variant playlist'i — ses
        # rendition'ı orada tanımlı. Tek master URL alınır, kalite seçimi ABR'den.
        master = next(
            (f["manifest_url"] for f in info.get("formats") or [] if f.get("manifest_url")),
            None,
        )
        if not master:
            return []

        return [
            ExtractResult(
                name       = f"{self.name} | YouTube (resmi)",
                url        = master,
                referer    = "",
                user_agent = _UA,
                subtitles  = [],
            )
        ]
