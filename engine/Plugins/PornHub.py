"""PornHub sağlayıcısı — liste/arama HTML'den, oynatma linki yt-dlp'den.

Video sayfası httpx'e 403 döner (TLS parmak izi; aynı istek curl'de 200), liste
sayfaları dönmez. Bu yüzden liste kazınır, oynatma yt-dlp'ye bırakılır. Akış
adresleri WARP çıkış IP'sine bağlı imzalıdır — her iki yol da aynı tünelden geçer.
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

class PornHub(PluginBase):
    name        = "PornHub"
    language    = "en"
    main_url    = "https://www.pornhub.com"
    favicon     = "https://www.pornhub.com/favicon.ico"
    description = "PornHub — HLS akışlı geniş yetişkin arşivi."

    main_page = {
        f"{main_url}/video?o=mr&page=SAYFA"     : "Yeni Eklenenler",
        f"{main_url}/video?o=tr&t=w&page=SAYFA" : "Haftanın Trendleri",
        f"{main_url}/video?o=ht&t=m&page=SAYFA" : "Ayın En İyileri",
        f"{main_url}/video?o=tr&t=a&page=SAYFA" : "Tüm Zamanlar",
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
        for li in tree.select("li.pcVideoListItem"):
            a = li.css_first("a.linkVideoThumb")
            if not a:
                continue
            href  = a.attrs.get("href") or ""
            title = (a.attrs.get("title") or "").strip()
            if not href or not title:
                continue
            img    = li.css_first("img")
            poster = (img.attrs.get("data-src") or img.attrs.get("src") or "") if img else ""
            results.append(MainPageResult(
                category = category,
                title    = title,
                url      = href if href.startswith("http") else f"{self.main_url}{href}",
                poster   = poster,
            ))
        return results

    async def get_main_page(self, page: int, url: str, category: str) -> list[MainPageResult]:
        return self._cards(await self._fetch(url.replace("SAYFA", str(page or 1))), category)

    async def search(self, query: str) -> list[SearchResult]:
        html = await self._fetch(f"{self.main_url}/video/search?search={query.replace(' ', '+')}")
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

        # Aynı çözünürlüğün birden çok varyantı olur; her yükseklikten biri yeter.
        by_height: dict[int, str] = {}
        for fmt in info.get("formats") or []:
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
            for height in sorted(by_height, reverse=True)
        ]
