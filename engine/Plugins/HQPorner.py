"""HQPorner adult provider for NetMovies with WARP proxy support."""

from __future__ import annotations

import os
import re
import httpx

from KekikStream.Core import (
    ExtractResult,
    HTMLHelper,
    MainPageResult,
    MovieInfo,
    PluginBase,
    SearchResult,
)

_WARP_PROXY = os.getenv("WARP_PROXY_URL", "http://172.31.0.4:8080")


class HQPorner(PluginBase):
    name = "HQPorner"
    language = "en"
    main_url = "https://hqporner.com"
    favicon = "https://hqporner.com/favicon.ico"
    description = "HQPorner — 4K/1080p/60FPS yetişkin arşivi."

    main_page = {
        f"{main_url}/top/month/SAYFA": "Popüler (Ay)",
        f"{main_url}/top/week/SAYFA": "Popüler (Hafta)",
        f"{main_url}/category/1080p-porn/SAYFA": "1080p HD",
        f"{main_url}/category/4k-porn/SAYFA": "4K Ultra HD",
        f"{main_url}/category/60fps-porn/SAYFA": "60 FPS",
    }

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        # WARP proxy desteği: yerel ISP engeline takılmamak için
        try:
            self._client = httpx.AsyncClient(
                proxy=_WARP_PROXY,
                headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"},
                timeout=12.0,
                follow_redirects=True,
            )
        except Exception:
            self._client = self.httpx

    async def _fetch(self, url: str) -> str:
        try:
            resp = await self._client.get(url)
            return resp.text
        except Exception:
            resp = await self.httpx.get(url)
            return resp.text

    @staticmethod
    def _card(node: HTMLHelper, base_url: str, category: str) -> MainPageResult | None:
        a = node.css_first("h3 a")
        img = node.css_first("img")
        if not a:
            return None
        title = a.text(strip=True)
        href = a.attrs.get("href") or ""
        poster = img.attrs.get("src") if img else ""
        if not title or not href:
            return None
        if href.startswith("/"):
            href = base_url + href
        if poster and poster.startswith("//"):
            poster = "https:" + poster
        return MainPageResult(category=category, title=title, url=href, poster=poster)

    async def get_main_page(self, page: int, url: str, category: str) -> list[MainPageResult]:
        target_url = url.replace("SAYFA", str(page or 1))
        html = await self._fetch(target_url)
        tree = HTMLHelper(html)
        results = []
        for sec in tree.select("div.box.page-content div.row section"):
            card = self._card(sec, self.main_url, category)
            if card:
                results.append(card)
        return results

    async def search(self, query: str) -> list[SearchResult]:
        search_url = f"{self.main_url}/?q={query.replace(' ', '+')}&p=1"
        html = await self._fetch(search_url)
        tree = HTMLHelper(html)
        results = []
        for sec in tree.select("div.box.page-content div.row section"):
            card = self._card(sec, self.main_url, "Arama Sonucu")
            if card:
                results.append(SearchResult(title=card.title, url=card.url, poster=card.poster))
        return results

    async def load_item(self, url: str) -> MovieInfo | None:
        html = await self._fetch(url)
        tree = HTMLHelper(html)
        h1 = tree.css_first("h1.main-h1")
        title = h1.text(strip=True) if h1 else "Video"
        img = tree.css_first("div.player-wrapper img, img.cover")
        poster = img.attrs.get("src") if img else None
        if poster and poster.startswith("//"):
            poster = "https:" + poster
        return MovieInfo(url=url, title=title, poster=poster, plot=title)

    async def load_links(self, url: str) -> list[ExtractResult]:
        html = await self._fetch(url)
        m = re.search(r"url:\s*'/blocks/altplayer\.php\?i=//([^']+)'", html)
        if not m:
            m = re.search(r"blocks/altplayer\.php\?i=//([^\s'\"]+)", html)
        if not m:
            return []

        vid_url = "https://" + m.group(1)
        return [
            ExtractResult(
                name=f"{self.name} | 1080p HD",
                url=vid_url,
                referer=f"{self.main_url}/",
            )
        ]
