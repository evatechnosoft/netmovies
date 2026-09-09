# NetMovies — KultFilmler eklentisi
#
# Upstream (Kekik-cloudstream) parser'ı eski temaya göre yazılmış (`div.movie-box`,
# `PHA+` base64 iframe, `div.parts-middle` alternatifler); site 2026'da özel bir
# temaya geçti ve bunların hiçbiri eşleşmiyor. Bu dosya yeni yapıya göre yazıldı:
#   - Kartlar: `a.mcard` (h3 başlık, img.pimg poster) — ana sayfa, kategori ve arama aynı.
#   - Kaynaklar: `<script id="kf-srcdata">` JSON listesi (name/langLabel/html-iframe).
#   - Oynatıcı: FirePlayer (vidpapi.xyz). `POST /player/index.php?data=<hash>&do=getVideo`
#     JSON'unda `securedLink` (md5+expires imzalı HLS master) düz metin geliyor;
#     `ck` ile şifreli `videoSources` alanına dokunmaya gerek kalmadı.
#   Manifest Referer istemeden 200 döndü (kanıtlandı); yine de iframe adresi referer verilir.

from __future__ import annotations

import json
import re
from urllib.parse import urlsplit

from KekikStream.Core import (
    PluginBase,
    MainPageResult,
    SearchResult,
    MovieInfo,
    SeriesInfo,
    Episode,
    ExtractResult,
    Subtitle,
    HTMLHelper,
)
from Plugins.__dizi_common import extract_embedded_sources, season_episode
from Plugins.__kekik_domain import discover_main_url

_MAIN_URL = discover_main_url(
    "KultFilmler/src/main/kotlin/com/keyiflerolsun/KultFilmler.kt",
    "https://kultfilmler.net",
    "KULTFILMLER_URL",
)

_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"


class KultFilmler(PluginBase):
    name        = "KultFilmler"
    language    = "tr"
    main_url    = _MAIN_URL
    favicon     = f"https://www.google.com/s2/favicons?domain={_MAIN_URL}&sz=64"
    description = "KultFilmler — kült/klasik filmler ve mini diziler, Türkçe altyazı/dublaj."

    # Kategori adlarında "Film" ya da tür adı geçer (aggregate `_pick_categories` bunu arar).
    main_page = {
        f"{main_url}/"                                   : "Yeni Filmler",
        f"{main_url}/category/aksiyon-filmleri-izle/"    : "Aksiyon",
        f"{main_url}/category/bilim-kurgu-filmleri-izle/": "Bilim Kurgu",
        f"{main_url}/category/dram-filmleri-izle/"       : "Dram",
        f"{main_url}/category/gerilim-filmleri-izle/"    : "Gerilim",
        f"{main_url}/category/komedi-filmleri-izle/"     : "Komedi",
        f"{main_url}/category/korku-filmleri-izle/"      : "Korku",
        f"{main_url}/category/suc-filmleri-izle/"        : "Suç",
        f"{main_url}/category/animasyon-filmleri-izle/"  : "Animasyon",
        f"{main_url}/dizi-arsivi/"                       : "Diziler",
    }

    def _card(self, node: HTMLHelper) -> tuple[str, str, str | None] | None:
        href  = node.attrs.get("href") if hasattr(node, "attrs") else None
        title = node.select_text("h3") or node.select_attr("img", "alt")
        if not href or not title:
            return None
        poster = node.select_attr("img", "src")
        return title.strip(), self.fix_url(href), self.fix_url(poster) if poster else None

    # ------------------------------------------------------------------ Ana sayfa
    async def get_main_page(self, page: int, url: str, category: str) -> list[MainPageResult]:
        target   = url if page <= 1 else f"{url.rstrip('/')}/page/{page}/"
        response = await self.httpx.get(target, headers={"User-Agent": _UA})
        secici   = HTMLHelper(response.text)

        results: list[MainPageResult] = []
        for node in secici.select("a.mcard"):
            card = self._card(node)
            if card:
                results.append(MainPageResult(category=category, title=card[0], url=card[1], poster=card[2]))
        return results

    # ------------------------------------------------------------------ Arama
    async def search(self, query: str) -> list[SearchResult]:
        response = await self.httpx.get(f"{self.main_url}/?s={query}", headers={"User-Agent": _UA})
        secici   = HTMLHelper(response.text)

        results: list[SearchResult] = []
        for node in secici.select("a.mcard"):
            card = self._card(node)
            if card:
                results.append(SearchResult(title=card[0], url=card[1], poster=card[2]))
        return results

    # ------------------------------------------------------------------ Detay
    async def load_item(self, url: str) -> MovieInfo | SeriesInfo:
        response = await self.httpx.get(url, headers={"User-Agent": _UA})
        secici   = HTMLHelper(response.text)

        title       = (secici.select_text("h1.vtitle") or secici.select_text("h1") or "").strip()
        poster      = secici.og_poster
        description = secici.select_text("p.desc") or secici.og_description
        tags        = secici.select_texts("div.genres a")
        year        = secici.regex_first(r"(\d{4})", secici.select_text("a[href*='/yapim/']") or "")
        rating      = secici.select_text("div.imdb span.score")
        # Oyuncular sayfada ayrı bir liste olarak yok; JSON-LD `actor` alanından okunur.
        actors      = re.findall(r'"actor":\[(.*?)\]', response.text, re.S)
        actors      = re.findall(r'"name":"([^"]+)"', actors[0]) if actors else []
        actors      = [json.loads(f'"{a}"') for a in actors]

        episode_nodes = secici.select("a.ep")
        if episode_nodes:
            episodes: list[Episode] = []
            for node in episode_nodes:
                href  = node.attrs.get("href")
                label = (node.select_text("h4") or "").strip()
                if not href or not label:
                    continue
                season, number = season_episode(label)
                episodes.append(Episode(season=season, episode=number, title=label, url=self.fix_url(href)))
            return SeriesInfo(
                url         = url,
                title       = title,
                poster      = self.fix_url(poster) if poster else None,
                description = description,
                tags        = tags,
                rating      = rating,
                year        = year,
                actors      = actors,
                episodes    = episodes,
            )

        return MovieInfo(
            url         = url,
            title       = title,
            poster      = self.fix_url(poster) if poster else None,
            description = description,
            tags        = tags,
            rating      = rating,
            year        = year,
            actors      = actors,
        )

    # ------------------------------------------------------------------ Linkler
    async def load_links(self, url: str) -> list[ExtractResult]:
        page = await self.httpx.get(url, headers={"User-Agent": _UA})
        html = page.text

        # Kaynak listesi `kf-srcdata` JSON'unda; her kayıt iframe HTML'i taşır.
        sources: list[tuple[str, str]] = []
        data_match = re.search(r'id="kf-srcdata"[^>]*>(.*?)</script>', html, re.S)
        if data_match:
            try:
                for item in json.loads(data_match.group(1)):
                    src = re.search(r'src="([^"]+)"', item.get("html") or "")
                    if src:
                        label = " ".join(x for x in (item.get("name"), item.get("langLabel")) if x)
                        sources.append((label or "Kaynak", src.group(1)))
            except (json.JSONDecodeError, TypeError):
                pass
        if not sources:
            sources = [("Kaynak", src) for src in re.findall(r'<iframe[^>]+src="([^"]+)"', html)]

        results: list[ExtractResult] = []
        for label, iframe_url in dict.fromkeys(sources):
            iframe_url = self.fix_url(iframe_url)
            try:
                if "/video/" in iframe_url:
                    found = await self._fireplayer(iframe_url, label, url)
                else:
                    found = await self.extract(iframe_url, referer=url)
                    found = found if isinstance(found, list) else ([found] if found else [])
            except Exception:
                found = []
            if not found:
                try:
                    frame = await self.httpx.get(iframe_url, headers={"User-Agent": _UA, "Referer": f"{self.main_url}/"})
                    found = extract_embedded_sources(frame.text, iframe_url, f"{self.name} | {label}")
                except Exception:
                    found = []
            results.extend(found)
        return self.deduplicate(results)

    async def _fireplayer(self, iframe_url: str, label: str, page_url: str) -> list[ExtractResult]:
        """FirePlayer (vidpapi.xyz): hash → getVideo JSON → imzalı HLS master + altyazı."""
        parts  = urlsplit(iframe_url)
        origin = f"{parts.scheme}://{parts.netloc}"
        video  = parts.path.rstrip("/").rsplit("/", 1)[-1]

        # Altyazı iframe sayfasındaki `playerjsSubtitle` değişkeninde: "[Türkçe]https://…srt"
        subtitles: list[Subtitle] = []
        frame = await self.httpx.get(iframe_url, headers={"User-Agent": _UA, "Referer": f"{self.main_url}/"})
        sub_var = re.search(r'playerjsSubtitle\s*=\s*"([^"]*)"', frame.text)
        for sub_name, sub_url in re.findall(r"\[([^\]]+)\](https?://[^,\s]+)", sub_var.group(1) if sub_var else ""):
            subtitles.append(Subtitle(name=sub_name, url=sub_url))

        response = await self.httpx.post(
            f"{origin}/player/index.php?data={video}&do=getVideo",
            data    = {"hash": video, "r": f"{self.main_url}/"},
            headers = {
                "User-Agent"      : _UA,
                "Referer"         : iframe_url,
                "Origin"          : origin,
                "X-Requested-With": "XMLHttpRequest",
            },
            timeout = 15.0,
        )
        payload = response.json()
        stream  = payload.get("securedLink") or payload.get("videoSource")
        if not stream:
            return []

        return [
            ExtractResult(
                name       = f"{self.name} | {label}",
                url        = stream,
                referer    = iframe_url,
                user_agent = _UA,
                subtitles  = subtitles,
            )
        ]
