# NetMovies — FilmMakinesi eklentisi
#
# Upstream (Kekik-cloudstream) `.de` domainini ve `section#film_posts article`
# kartlarını hedefliyor; ikisi de bayat: `.de` TR'den SNI-bloklu ve WARP'ta CF
# challenge'a düşüyor, site `filmmakinesi.to`'da yeni temayla yayında (doğrudan 200).
#   - Kartlar: `a.item[data-title]` (liste/tür/arama sayfalarında aynı).
#   - Tür rafları: `/tur/<slug>/film/` (+ `sayfa/N/`), arama: `/arama/?s=`.
#   - Oynatıcı: closeload.filmmakinesi.to iframe → jwplayer `sources:[{file: <var>}]`,
#     değişken önceki script'teki obfuscated fonksiyondan üretiliyor → `_js_player`
#     sayfanın kendi JS'ini çalıştırıp config'i yakalıyor (JSON-LD'deki contentUrl
#     sahte: 404). Manifest Referer'sız 404 → proxy zorunlu (`_ALWAYS_PROXY_PLUGINS`).

from __future__ import annotations

import re

from KekikStream.Core import ExtractResult, HTMLHelper, MainPageResult, MovieInfo, PluginBase, SearchResult, Subtitle
from Plugins.__kekik_domain import discover_main_url
from Plugins._js_player import extract_player_config

_DISCOVERED_URL = discover_main_url(
    "FilmMakinesi/src/main/kotlin/com/keyiflerolsun/FilmMakinesi.kt", "https://filmmakinesi.to", "FILMMAKINESI_URL"
)
# Keşif upstream'in ölü `.de`'sini bulursa doğrulanmış `.to`'ya çekilir.
_MAIN_URL = "https://filmmakinesi.to" if _DISCOVERED_URL.endswith("filmmakinesi.de") else _DISCOVERED_URL

_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"


class FilmMakinesi(PluginBase):
    name        = "FilmMakinesi"
    language    = "tr"
    main_url    = _MAIN_URL
    favicon     = f"https://www.google.com/s2/favicons?domain={_MAIN_URL}&sz=64"
    description = "FilmMakinesi — Türkçe dublaj/altyazılı filmler, tür rafları."

    # Kategori adlarında "Film" ya da tür adı geçer (aggregate `_pick_categories` bunu arar).
    main_page = {
        f"{main_url}/filmler-1/"               : "Yeni Filmler",
        f"{main_url}/tur/aksiyon-fmy54y/film/" : "Aksiyon",
        f"{main_url}/tur/bilim-kurgu-fm3/film/": "Bilim Kurgu",
        f"{main_url}/tur/macera-fm1/film/"     : "Macera",
        f"{main_url}/tur/komedi-fm1/film/"     : "Komedi",
        f"{main_url}/tur/gerilim-fm1/film/"    : "Gerilim",
        f"{main_url}/tur/korku-fm2/film/"      : "Korku",
        f"{main_url}/tur/dram-fm1/film/"       : "Dram",
        f"{main_url}/tur/fantastik-fm1/film/"  : "Fantastik",
        f"{main_url}/tur/animasyon-fm2/film/"  : "Animasyon",
    }

    def _cards(self, html: str) -> list[tuple[str, str, str | None]]:
        secici = HTMLHelper(html)
        out: list[tuple[str, str, str | None]] = []
        for node in secici.select("a.item[data-title]"):
            href  = node.attrs.get("href")
            title = node.attrs.get("data-title")
            if href and title:
                out.append((title.strip(), self.fix_url(href), self.fix_url(node.select_attr("img", "src") or "") or None))
        return out

    async def get_main_page(self, page: int, url: str, category: str) -> list[MainPageResult]:
        target   = url if page <= 1 else f"{url.rstrip('/')}/sayfa/{page}/"
        response = await self.httpx.get(target, headers={"User-Agent": _UA})
        return [MainPageResult(category=category, title=t, url=u, poster=p) for t, u, p in self._cards(response.text)]

    async def search(self, query: str) -> list[SearchResult]:
        response = await self.httpx.get(f"{self.main_url}/arama/", params={"s": query}, headers={"User-Agent": _UA})
        return [SearchResult(title=t, url=u, poster=p) for t, u, p in self._cards(response.text)]

    async def load_item(self, url: str) -> MovieInfo:
        response = await self.httpx.get(url, headers={"User-Agent": _UA})
        secici   = HTMLHelper(response.text)
        # h1 "İfşa Günü izle (2026)" biçiminde; yıl ayrı span'da.
        h1    = secici.select_text("h1") or ""
        title = re.sub(r"\s*izle\b.*$", "", h1, flags=re.I).strip() or h1.strip()
        return MovieInfo(
            url         = url,
            title       = title,
            poster      = self.fix_url(secici.og_poster) if secici.og_poster else None,
            description = secici.og_description,
            tags        = secici.select_texts("a[href*='/tur/']"),
            rating      = secici.select_text(".imdb-score span"),
            year        = secici.regex_first(r"(\d{4})", h1),
        )

    async def load_links(self, url: str) -> list[ExtractResult]:
        page    = await self.httpx.get(url, headers={"User-Agent": _UA})
        iframes = [f for f in re.findall(r'<iframe[^>]+src="([^"]+)"', page.text) if "youtube" not in f]

        results: list[ExtractResult] = []
        for iframe_url in dict.fromkeys(iframes):
            iframe_url = self.fix_url(iframe_url)
            frame  = await self.httpx.get(iframe_url, headers={"User-Agent": _UA, "Referer": f"{self.main_url}/"})
            config = extract_player_config(frame.text) or {}
            subtitles = [
                Subtitle(name=t.get("label") or "Altyazı", url=t["file"])
                for t in config.get("tracks") or []
                if t.get("kind") == "captions" and t.get("file")
            ]
            for source in config.get("sources") or []:
                if source.get("file"):
                    results.append(
                        ExtractResult(
                            name       = f"{self.name} | CloseLoad",
                            url        = source["file"],
                            referer    = iframe_url,
                            user_agent = _UA,
                            subtitles  = subtitles,
                        )
                    )
        return self.deduplicate(results)
