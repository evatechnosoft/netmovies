"""xHamster sağlayıcısı — liste/arama HTML'den, oynatma linki yt-dlp'den.

Sayfa Vue SSR: görsel class'ların çoğu derleme hash'i taşır (`root-33e82`) ve her
dağıtımda değişir. Yalnız semantik olanlara (`video-thumb__image-container`)
dayanılır; hash'li seçici kullanılırsa eklenti sessizce boşalır.
"""

from __future__ import annotations

from KekikStream.Core import (
    ExtractResult,
    HTMLHelper,
    MainPageResult,
    MovieInfo,
    PluginBase,
    SearchResult,
)

from Plugins.__warp_client import warp_client, ytdlp_info


class xHamster(PluginBase):
    name        = "xHamster"
    language    = "en"
    main_url    = "https://xhamster.com"
    favicon     = "https://xhamster.com/favicon.ico"
    description = "xHamster — geniş yetişkin arşivi, HLS akış."

    main_page = {
        f"{main_url}/newest/SAYFA"         : "Yeni Eklenenler",
        f"{main_url}/best/weekly/SAYFA"    : "Haftanın En İyileri",
        f"{main_url}/best/monthly/SAYFA"   : "Ayın En İyileri",
        f"{main_url}/categories/hd/SAYFA"  : "HD",
    }

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self._client = warp_client()

    async def _fetch(self, url: str) -> str:
        resp = await self._client.get(url)
        return resp.text

    def _cards(self, html: str, category: str) -> list[MainPageResult]:
        tree    = HTMLHelper(html)
        results = []
        görülen = set()
        for a in tree.select("a.video-thumb__image-container"):
            href  = a.attrs.get("href") or ""
            title = (a.attrs.get("aria-label") or "").strip()
            if not href or not title or href in görülen:
                continue
            görülen.add(href)
            img = a.css_first("img")
            results.append(MainPageResult(
                category = category,
                title    = title,
                url      = href,
                poster   = (img.attrs.get("src") or "") if img else "",
            ))
        return results

    async def get_main_page(self, page: int, url: str, category: str) -> list[MainPageResult]:
        sayfa  = str(page or 1)
        # 1. sayfada sondaki "/1" gereksiz yönlendirme üretiyor.
        hedef  = url.replace("/SAYFA", "") if sayfa == "1" else url.replace("SAYFA", sayfa)
        return self._cards(await self._fetch(hedef), category)

    async def search(self, query: str) -> list[SearchResult]:
        html = await self._fetch(f"{self.main_url}/search/{query.replace(' ', '-')}")
        return [
            SearchResult(title=card.title, url=card.url, poster=card.poster)
            for card in self._cards(html, "Arama Sonucu")
        ]

    async def load_item(self, url: str) -> MovieInfo | None:
        info = await ytdlp_info(url)
        if not info:
            return MovieInfo(url=url, title="Video", poster=None, plot="")
        return MovieInfo(
            url    = url,
            title  = info.get("title") or "Video",
            poster = info.get("thumbnail"),
            plot   = (info.get("description") or info.get("title") or "")[:500],
        )

    async def load_links(self, url: str) -> list[ExtractResult]:
        info = await ytdlp_info(url)
        if not info:
            return []

        # HLS önce: aynı yükseklikteki doğrudan mp4 ("Untested", h264-480p) WARP'tan
        # da 403 veriyor, HLS varyantı oynuyor. setdefault ilk geleni tutar.
        by_height: dict[int, str] = {}
        # AV1 en sona ve H.264 varken hiç: Mi Box AV1 çözemiyor, 1080p HLS'in ilk
        # varyantı av1 olunca yalnız ses geliyordu.
        def _av1(f: dict) -> bool:
            return str(f.get("vcodec") or "").startswith("av01") or str(f.get("vcodec")) == "av1" or ".av1." in str(f.get("url"))
        formats = sorted(info.get("formats") or [], key=lambda f: (_av1(f), "m3u8" not in str(f.get("protocol") or "")))
        if any(not _av1(f) for f in formats):
            formats = [f for f in formats if not _av1(f)]
        for fmt in formats:
            akis = fmt.get("url")
            if not akis or fmt.get("vcodec") == "none":
                continue
            by_height.setdefault(int(fmt.get("height") or 0), akis)

        return [
            ExtractResult(
                name    = f"{self.name} | {height}p" if height else f"{self.name} | oto",
                url     = by_height[height],
                referer = f"{self.main_url}/",
            )
            # 1080p önce, 4K sonda (2160p H.264 Mi Box'ta yalnız ses verir).
            for height in sorted(by_height, key=lambda h: (h > 1080, -h))
        ]
