"""DiziMom provider ported from the Kekik-cloudstream plugin."""

from __future__ import annotations

import os

from KekikStream.Core import Episode, ExtractResult, HTMLHelper, MainPageResult, PluginBase, SearchResult, SeriesInfo
from Plugins.__dizi_common import absolute, extract_embedded_sources, fetch_html, first_attr, first_text, normalize_url, season_episode
from Plugins.__kekik_domain import discover_main_url

# Domain zinciri: dizimom.plus → .work → .food → .diy. Upstream .kt hâlâ ölü .plus'ı
# gösterdiği için gömülü yedek doğrulanmış son adrese çekildi; aksi halde temiz bir
# kurulumda (.env'siz) DiziMom boş dönüyordu.
_DISCOVERED_URL = discover_main_url(
    "DiziMom/src/main/kotlin/com/keyiflerolsun/DiziMom.kt", "https://www.dizimom.diy", "DIZIMOM_URL"
)
_MAIN_URL = os.getenv("DIZIMOM_URL") or (
    "https://www.dizimom.diy" if _DISCOVERED_URL.endswith(("dizimom.plus", "dizimom.work", "dizimom.food")) else _DISCOVERED_URL
)


class DiziMom(PluginBase):
    name = "DiziMom"
    language = "tr"
    main_url = _MAIN_URL
    favicon = f"https://www.google.com/s2/favicons?domain={_MAIN_URL}&sz=64"
    description = "DiziMom — yerli, yabancı ve TV dizileri."
    main_page = {
        f"{main_url}/tum-bolumler/page/SAYFA/": "Son Bölümler",
        f"{main_url}/yerli-dizi-izle/page/SAYFA/": "Yerli Diziler",
        f"{main_url}/yabanci-dizi-izle/page/SAYFA/": "Yabancı Diziler",
        f"{main_url}/tv-programlari-izle/page/SAYFA/": "TV Programları",
    }

    @staticmethod
    def _result(node: HTMLHelper, base_url: str, category: str) -> MainPageResult | None:
        title = first_text(node, ("div.categorytitle a", "div.episode-name a", "a"))
        href = absolute(base_url, first_attr(node, ("div.categorytitle a", "div.episode-name a", "a"), "href"))
        poster = absolute(base_url, first_attr(node, ("div.cat-img img", "a img", "img"), "src"))
        if not title or not href:
            return None
        return MainPageResult(category=category, title=title.split(" izle")[0].strip(), url=href, poster=poster)

    async def get_main_page(self, page: int, url: str, category: str) -> list[MainPageResult]:
        target = normalize_url(url.replace("SAYFA", str(page or 1)), self.main_url)
        selector = HTMLHelper(await fetch_html(self.httpx, target))
        css = "div.episode-box" if "tum-bolumler" in url else "div.single-item"
        results: list[MainPageResult] = []
        for node in selector.select(css):
            item = self._result(node, self.main_url, category)
            if item:
                results.append(item)
        return results

    async def search(self, query: str) -> list[SearchResult]:
        target = f"{self.main_url}/?s={query}"
        selector = HTMLHelper(await fetch_html(self.httpx, target))
        results: list[SearchResult] = []
        for node in selector.select("div.single-item"):
            item = self._result(node, self.main_url, "")
            if item:
                results.append(SearchResult(title=item.title, url=item.url, poster=item.poster))
        return results

    async def load_item(self, url: str) -> SeriesInfo:
        target = normalize_url(url, self.main_url)
        selector = HTMLHelper(await fetch_html(self.httpx, target))
        title = (first_text(selector, ("div.title h1", "h1")) or "").split(" izle")[0].strip()
        poster = absolute(self.main_url, first_attr(selector, ("div.category_image img", "img"), "src"))
        description = first_text(selector, ("div.category_desc",))
        episodes: list[Episode] = []
        for node in selector.select("div.bolumust"):
            ep_title = first_text(node, ("div.baslik",))
            ep_url = absolute(self.main_url, first_attr(node, ("a",), "href"))
            if not ep_title or not ep_url:
                continue
            season, episode = season_episode(ep_title)
            episodes.append(Episode(season=season, episode=episode, title=ep_title, url=ep_url))
        return SeriesInfo(url=url, title=title, poster=poster, description=description, episodes=episodes)

    async def load_links(self, url: str) -> list[ExtractResult]:
        target = normalize_url(url, self.main_url)
        headers = {"User-Agent": "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 Chrome/114.0.0.0 Mobile Safari/537.36"}
        try:
            await self.httpx.post(f"{self.main_url}/wp-login.php", headers=headers, data={"log": "keyiflerolsun", "pwd": "12345", "rememberme": "forever", "redirect_to": self.main_url})
        except Exception:
            pass
        selector = HTMLHelper(await fetch_html(self.httpx, target, headers=headers))
        pages = [target]
        pages.extend(node.attrs.get("href") for node in selector.select("div.sources a") if node.attrs.get("href"))
        results: list[ExtractResult] = []
        for page in pages:
            if not page:
                continue
            page_url = absolute(self.main_url, page) or page
            page_selector = HTMLHelper(await fetch_html(self.httpx, page_url, headers=headers))
            iframe = first_attr(page_selector, ("div.video p iframe", "iframe"), "src")
            iframe_url = absolute(page_url, iframe)
            if iframe_url:
                iframe_html = await fetch_html(self.httpx, iframe_url, headers=headers)
                results.extend(extract_embedded_sources(iframe_html, iframe_url, self.name))
                self.collect_results(results, await self.extract(iframe_url, referer=page_url))
        return self.deduplicate(results)
