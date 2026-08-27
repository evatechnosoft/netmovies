"""Dizilla provider ported from the Kekik-cloudstream plugin."""

from __future__ import annotations

import re

from KekikStream.Core import Episode, ExtractResult, HTMLHelper, MainPageResult, PluginBase, SearchResult, SeriesInfo
from Plugins.__dizi_common import absolute, extract_embedded_sources, first_attr, first_text, season_episode
from Plugins.__kekik_domain import discover_main_url

_MAIN_URL = discover_main_url(
    "Dizilla/src/main/kotlin/com/keyiflerolsun/Dizilla.kt", "https://dizilla.nl", "DIZILLA_URL"
)


class Dizilla(PluginBase):
    name = "Dizilla"
    language = "tr"
    main_url = _MAIN_URL
    favicon = f"https://www.google.com/s2/favicons?domain={_MAIN_URL}&sz=64"
    description = "Dizilla — altyazılı ve Türkçe dublaj yabancı diziler."
    main_page = {
        f"{main_url}/tum-bolumler": "Son Bölümler",
        f"{main_url}/dublaj-bolumler": "Dublaj Bölümleri",
        f"{main_url}/dizi-turu/aile": "Aile",
        f"{main_url}/dizi-turu/aksiyon": "Aksiyon",
        f"{main_url}/dizi-turu/bilim-kurgu": "Bilim Kurgu",
        f"{main_url}/dizi-turu/romantik": "Romantik",
        f"{main_url}/dizi-turu/komedi": "Komedi",
    }

    @staticmethod
    def _result(node: HTMLHelper, base_url: str, category: str) -> MainPageResult | None:
        title = first_text(node, ("h2", "a"))
        href = absolute(base_url, first_attr(node, ("a",), "href"))
        poster = absolute(base_url, first_attr(node, ("img",), "data-src")) or absolute(base_url, first_attr(node, ("img",), "src"))
        if not title or not href:
            return None
        return MainPageResult(category=category, title=title, url=href, poster=poster)

    async def get_main_page(self, page: int, url: str, category: str) -> list[MainPageResult]:
        response = await self.httpx.get(url)
        selector = HTMLHelper(response.text)
        css = "div.grid-cols-3 a" if "/dizi-turu/" in url else "div.grid a"
        results: list[MainPageResult] = []
        for node in selector.select(css):
            item = self._result(node, self.main_url, category)
            if item:
                results.append(item)
        return results

    async def search(self, query: str) -> list[SearchResult]:
        response = await self.httpx.get(self.main_url)
        selector = HTMLHelper(response.text)
        key = selector.select_attr("input[name='cKey']", "value")
        value = selector.select_attr("input[name='cValue']", "value")
        if not key or not value:
            return []
        response = await self.httpx.post(
            f"{self.main_url}/bg/searchcontent",
            data={"cKey": key, "cValue": value, "searchterm": query},
            headers={"Accept": "application/json", "X-Requested-With": "XMLHttpRequest"},
        )
        try:
            payload = response.json()
        except Exception:
            return []
        results: list[SearchResult] = []
        for item in payload.get("data", {}).get("result", []) if isinstance(payload, dict) else []:
            if not isinstance(item, dict):
                continue
            title = str(item.get("title") or "").strip()
            slug = str(item.get("slug") or "").strip()
            if title and slug:
                results.append(SearchResult(title=title, url=f"{self.main_url}/{slug}", poster=item.get("poster")))
        return results

    async def load_item(self, url: str) -> SeriesInfo:
        response = await self.httpx.get(url)
        selector = HTMLHelper(response.text)
        title = first_text(selector, ("div.page-top h1", "h1")) or ""
        poster = absolute(self.main_url, first_attr(selector, ("div.page-top img", "img"), "src"))
        description = first_text(selector, ("div.mv-det-p", "div.w-full div.text-base"))
        episodes: list[Episode] = []
        for season in selector.select("div.gap-2 a[href*='-sezon']"):
            season_url = absolute(self.main_url, season.attrs.get("href"))
            if not season_url:
                continue
            season_selector = HTMLHelper((await self.httpx.get(season_url)).text)
            for node in season_selector.select("div.episodes div.cursor-pointer, div.dub-episodes div.cursor-pointer"):
                ep_title = first_text(node, ("a",))
                ep_url = absolute(self.main_url, first_attr(node, ("a.opacity-60", "a"), "href"))
                if not ep_title or not ep_url:
                    continue
                season_no, episode_no = season_episode(ep_title)
                episodes.append(Episode(season=season_no, episode=episode_no, title=ep_title, url=ep_url))
        return SeriesInfo(url=url, title=title, poster=poster, description=description, episodes=episodes)

    async def load_links(self, url: str) -> list[ExtractResult]:
        response = await self.httpx.get(url)
        selector = HTMLHelper(response.text)
        pages = [url]
        pages.extend(absolute(self.main_url, node.attrs.get("href")) for node in selector.select("a[href*='player']") if node.attrs.get("href"))
        results: list[ExtractResult] = []
        for page in pages:
            if not page:
                continue
            player_selector = HTMLHelper((await self.httpx.get(page)).text)
            iframe = first_attr(player_selector, ("div#playerLsDizilla iframe", "iframe"), "src")
            if not iframe:
                continue
            iframe_url = absolute(page, iframe)
            if iframe_url:
                iframe_response = await self.httpx.get(iframe_url, headers={"Referer": f"{self.main_url}/"})
                results.extend(extract_embedded_sources(iframe_response.text, iframe_url, self.name))
                self.collect_results(results, await self.extract(iframe_url, referer=page))
        return self.deduplicate(results)
