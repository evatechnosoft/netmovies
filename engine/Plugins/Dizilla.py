"""Dizilla provider ported from the Kekik-cloudstream plugin."""

from __future__ import annotations

import os
import re

import httpx

from KekikStream.Core import Episode, ExtractResult, HTMLHelper, MainPageResult, PluginBase, SearchResult, SeriesInfo
from Plugins.__dizi_common import (
    absolute,
    extract_embedded_sources,
    fetch_html,
    first_attr,
    first_text,
    normalize_url,
    season_episode,
)
from Plugins.__kekik_domain import discover_main_url

# Domain dizilla.nl → dizilla.club taşındı; TR'de SNI-bloklu → WARP proxy şart.
_MAIN_URL = discover_main_url(
    "Dizilla/src/main/kotlin/com/keyiflerolsun/Dizilla.kt", "https://dizilla.club", "DIZILLA_URL"
)


class Dizilla(PluginBase):
    # Dizilla SNI-bloklu → SADECE bu plugin çıkışını WARP proxy'sinden geçir.
    # NOT: PluginBase'in FallbackHTTPX'i proxy param'ını uygulamıyor (direkt bağlanıp
    # ConnectError) → super sonrası self.httpx'i proxy'li DÜZ httpx.AsyncClient ile
    # değiştiriyoruz (kanıt: dizilla.club proxy ile 200). Diğer plugin'ler dokunulmaz
    # → movie/RecTV/HDFC direkt kalır, bozulmaz.
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        warp = os.getenv("WARP_PROXY")
        if warp:
            headers = {}
            try:
                headers = dict(self.httpx.headers)
            except Exception:
                headers = {"User-Agent": "Mozilla/5.0"}
            self.httpx = httpx.AsyncClient(
                proxy=warp, follow_redirects=True, timeout=20, headers=headers,
            )

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
        return MainPageResult(category=category, title=title, url=normalize_url(href, base_url), poster=poster)

    async def get_main_page(self, page: int, url: str, category: str) -> list[MainPageResult]:
        text = await fetch_html(self.httpx, normalize_url(url, self.main_url))
        selector = HTMLHelper(text)
        css = "div.grid-cols-3 a" if "/dizi-turu/" in url else "div.grid a"
        results: list[MainPageResult] = []
        for node in selector.select(css):
            item = self._result(node, self.main_url, category)
            if item:
                results.append(item)
        return results

    async def search(self, query: str) -> list[SearchResult]:
        text = await fetch_html(self.httpx, self.main_url)
        selector = HTMLHelper(text)
        key = selector.select_attr("input[name='cKey']", "value")
        value = selector.select_attr("input[name='cValue']", "value")
        if not key or not value:
            return []
        try:
            response = await self.httpx.post(
                f"{self.main_url}/bg/searchcontent",
                data={"cKey": key, "cValue": value, "searchterm": query},
                headers={"Accept": "application/json", "X-Requested-With": "XMLHttpRequest"},
                timeout=8.0,
            )
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
        text = await fetch_html(self.httpx, normalize_url(url, self.main_url))
        selector = HTMLHelper(text)
        title = first_text(selector, ("div.page-top h1", "h1")) or ""
        poster = absolute(self.main_url, first_attr(selector, ("div.page-top img", "img"), "src"))
        description = first_text(selector, ("div.mv-det-p", "div.w-full div.text-base"))
        episodes: list[Episode] = []
        for season in selector.select("div.gap-2 a[href*='-sezon']"):
            season_url = absolute(self.main_url, season.attrs.get("href"))
            if not season_url:
                continue
            season_text = await fetch_html(self.httpx, normalize_url(season_url, self.main_url))
            season_selector = HTMLHelper(season_text)
            for node in season_selector.select("div.episodes div.cursor-pointer, div.dub-episodes div.cursor-pointer"):
                ep_title = first_text(node, ("a",))
                ep_url = absolute(self.main_url, first_attr(node, ("a.opacity-60", "a"), "href"))
                if not ep_title or not ep_url:
                    continue
                ep_url = normalize_url(ep_url, self.main_url)
                season_no, episode_no = season_episode(ep_title)
                episodes.append(Episode(season=season_no, episode=episode_no, title=ep_title, url=ep_url))
        return SeriesInfo(url=normalize_url(url, self.main_url), title=title, poster=poster, description=description, episodes=episodes)

    async def load_links(self, url: str) -> list[ExtractResult]:
        text = await fetch_html(self.httpx, normalize_url(url, self.main_url))
        selector = HTMLHelper(text)
        pages = [url]
        pages.extend(absolute(self.main_url, node.attrs.get("href")) for node in selector.select("a[href*='player']") if node.attrs.get("href"))
        results: list[ExtractResult] = []
        for page in pages:
            if not page:
                continue
            page_text = await fetch_html(self.httpx, normalize_url(page, self.main_url))
            player_selector = HTMLHelper(page_text)
            iframe = first_attr(player_selector, ("div#playerLsDizilla iframe", "iframe"), "src")
            if not iframe:
                continue
            iframe_url = absolute(page, iframe)
            if iframe_url:
                iframe_text = await fetch_html(self.httpx, iframe_url, headers={"Referer": f"{self.main_url}/"})
                results.extend(extract_embedded_sources(iframe_text, iframe_url, self.name))
                self.collect_results(results, await self.extract(iframe_url, referer=page))
        return self.deduplicate(results)
