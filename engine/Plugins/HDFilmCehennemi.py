# NetMovies — HDFilmCehennemi eklentisi
# KekikStream PluginBase formatına, keyiflerolsun/Kekik-cloudstream (Kotlin) referans
# alınarak uyarlanmıştır. Tek dosya, harici extractor'a bağımlı değildir; oynatma
# linkini sitenin kendi "playerr" scriptinden (P.A.C.K.E.R + base64) çözer.

from __future__ import annotations

import re
import json
import base64

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

try:
    from Plugins.__kekik_domain import discover_main_url
    from Plugins._js_player     import extract_player_config
except Exception:
    import sys, os as _os
    sys.path.insert(0, _os.path.dirname(__file__))
    from __kekik_domain import discover_main_url
    from _js_player     import extract_player_config

# Güncel domain otomatik çekilir; HDFC_URL ile elle sabitlenebilir.
_MAIN_URL = discover_main_url(
    "HDFilmCehennemi/src/main/kotlin/com/keyiflerolsun/HDFilmCehennemi.kt",
    "https://www.hdfilmcehennemi.nl",
    "HDFC_URL",
)


# ----------------------------------------------------------------------------
# P.A.C.K.E.R. (Dean Edwards) unpacker — sitenin "eval(function(p,a,c,k,e,d)...)"
# ile paketlenmiş player scriptini açmak için. Kotlin tarafındaki getAndUnpack
# karşılığı.
# ----------------------------------------------------------------------------
class _JSUnpacker:
    _ARGS = re.compile(
        r"}\s*\(\s*'(.*)'\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*'(.*?)'\.split\('\|'\)",
        re.DOTALL,
    )

    @staticmethod
    def _unbase(value: str, radix: int) -> int:
        alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        result = 0
        for ch in value:
            result = result * radix + alphabet.index(ch)
        return result

    @classmethod
    def detect(cls, script: str) -> bool:
        return "}(" in script and "split('|')" in script and "eval(" in script

    @classmethod
    def unpack(cls, script: str) -> str:
        match = cls._ARGS.search(script)
        if not match:
            return script

        payload, radix, count, symtab_raw = match.groups()
        radix = int(radix)
        symtab = symtab_raw.split("|")

        def repl(m: re.Match) -> str:
            word = m.group(0)
            idx = cls._unbase(word, radix)
            if idx < len(symtab) and symtab[idx]:
                return symtab[idx]
            return word

        payload = payload.replace("\\'", "'").replace("\\\\", "\\")
        return re.sub(r"\b\w+\b", repl, payload)


class HDFilmCehennemi(PluginBase):
    name        = "HDFilmCehennemi"
    language    = "tr"
    main_url    = _MAIN_URL
    favicon     = f"https://www.google.com/s2/favicons?domain={_MAIN_URL}&sz=64"
    description = "HDFilmCehennemi — Türkçe dublaj/altyazı film ve dizi kaynağı."

    main_page = {
        f"{main_url}"                                     : "Yeni Eklenen Filmler",
        f"{main_url}/yabancidiziizle-2"                   : "Yeni Eklenen Diziler",
        f"{main_url}/category/tavsiye-filmler-izle2"      : "Tavsiye Filmler",
        f"{main_url}/imdb-7-puan-uzeri-filmler"           : "IMDB 7+ Filmler",
        f"{main_url}/en-cok-begenilen-filmleri-izle"      : "En Çok Beğenilenler",
        f"{main_url}/tur/aksiyon-filmleri-izleyin-3"      : "Aksiyon",
        f"{main_url}/tur/komedi-filmlerini-izleyin-1"     : "Komedi",
        f"{main_url}/tur/korku-filmlerini-izle-2/"        : "Korku",
        f"{main_url}/tur/bilim-kurgu-filmlerini-izleyin-2": "Bilim Kurgu",
        f"{main_url}/tur/animasyon-filmlerini-izleyin-4"  : "Animasyon",
    }

    # ------------------------------------------------------------------ Ana sayfa
    async def get_main_page(self, page: int, url: str, category: str) -> list[MainPageResult]:
        response = await self.httpx.get(url)
        secici   = HTMLHelper(response.text)

        results: list[MainPageResult] = []
        for node in secici.select("div.section-content a.poster"):
            title = node.select_text("strong.poster-title")
            href  = node.attrs.get("href")
            if not title or not href:
                continue
            poster = node.select_attr("img", "data-src") or node.select_attr("img", "src")
            results.append(
                MainPageResult(
                    category = category,
                    title    = title,
                    url      = self.fix_url(href),
                    poster   = self.fix_url(poster) if poster else None,
                )
            )
        return results

    # ------------------------------------------------------------------ Arama
    async def search(self, query: str) -> list[SearchResult]:
        response = await self.httpx.get(
            f"{self.main_url}/search?q={query}",
            headers={"X-Requested-With": "fetch"},
        )
        try:
            payload = response.json()
        except Exception:
            return []

        results: list[SearchResult] = []
        for html in payload.get("results", []):
            secici = HTMLHelper(html)
            title  = secici.select_text("h4.title")
            href   = secici.select_attr("a", "href")
            if not title or not href:
                continue
            poster = secici.select_attr("img", "src") or secici.select_attr("img", "data-src")
            if poster:
                poster = poster.replace("/thumb/", "/list/")
            results.append(
                SearchResult(
                    title  = title,
                    url    = self.fix_url(href),
                    poster = self.fix_url(poster) if poster else None,
                )
            )
        return results

    # ------------------------------------------------------------------ Detay
    async def load_item(self, url: str) -> MovieInfo | SeriesInfo:
        response = await self.httpx.get(url)
        secici   = HTMLHelper(response.text)

        title = secici.select_text("h1.section-title")
        title = title.split(" izle")[0].strip() if title else title
        poster = None
        posters = secici.select_attrs("aside.post-info-poster img.lazyload", "data-src")
        if posters:
            poster = self.fix_url(posters[-1])
        tags        = secici.select_texts("div.post-info-genres a")
        description = secici.select_text("article.post-info-content > p")
        year        = secici.select_text("div.post-info-year-country a")
        rating      = secici.regex_first(r"([\d.,]+)", secici.select_text("div.post-info-imdb-rating span"))
        actors      = secici.select_texts("div.post-info-cast a strong")

        is_series = bool(secici.select("div.seasons"))

        if is_series:
            episodes: list[Episode] = []
            for node in secici.select("div.seasons-tab-content a"):
                ep_name = node.select_text("h4")
                ep_href = node.attrs.get("href")
                if not ep_name or not ep_href:
                    continue
                se = re.search(r"(\d+)\.\s?Sezon", ep_name)
                ep = re.search(r"(\d+)\.\s?Bölüm", ep_name)
                episodes.append(
                    Episode(
                        season  = int(se.group(1)) if se else 1,
                        episode = int(ep.group(1)) if ep else None,
                        title   = ep_name,
                        url     = self.fix_url(ep_href),
                    )
                )
            return SeriesInfo(
                url         = url,
                title       = title,
                poster      = poster,
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
            poster      = poster,
            description = description,
            tags        = tags,
            rating      = rating,
            year        = year,
            actors      = actors,
        )

    # ------------------------------------------------------------------ Linkler
    async def load_links(self, url: str) -> list[ExtractResult]:
        response = await self.httpx.get(url)
        secici   = HTMLHelper(response.text)

        results: list[ExtractResult] = []

        for group in secici.select("div.alternative-links"):
            lang = (group.attrs.get("data-lang") or "").upper()
            for button in group.select("button.alternative-link"):
                source   = button.text(strip=True).replace("(HDrip Xbet)", "").strip()
                video_id = button.attrs.get("data-video")
                if not video_id:
                    continue

                try:
                    api = await self.httpx.get(
                        f"{self.main_url}/video/{video_id}/",
                        headers={"Content-Type": "application/json", "X-Requested-With": "fetch"},
                        timeout=8.0,
                    )
                    iframe_match = re.search(r'data-src=\\"([^"]+)', api.text)
                    if not iframe_match:
                        continue
                    iframe = iframe_match.group(1).replace("\\", "")
                    if "?rapidrame_id=" in iframe:
                        iframe = f"{self.main_url}/playerr/" + iframe.split("?rapidrame_id=")[1]

                    extracted = await self._invoke_local_source(iframe, f"{source} {lang}".strip())
                    if extracted:
                        results.append(extracted)
                except Exception:
                    continue

        return self.deduplicate(results)

    async def _invoke_local_source(self, iframe_url: str, source_name: str) -> ExtractResult | None:
        """Sitenin kendi player scriptinden m3u8 + altyazıları çözer."""
        try:
            response = await self.httpx.get(iframe_url, headers={"Referer": f"{self.main_url}/"}, timeout=8.0)
            html     = response.text

            # Altyazı/video için göreli URL'ler player origin'ine göre düzeltilir.
            origin = re.match(r"https?://[^/]+", iframe_url)
            origin = origin.group(0) if origin else self.main_url

            def _fix(u: str) -> str:
                if not u:
                    return u
                if u.startswith("http"):
                    return u
                if u.startswith("//"):
                    return "https:" + u
                return origin + ("" if u.startswith("/") else "/") + u

            video_url: str | None = None
            subtitles: list[Subtitle] = []

            # 1) Birincil yol: sitenin kendi JS'ini V8'de çalıştır
            config = extract_player_config(html)
            if config:
                sources = config.get("sources") or []
                if sources:
                    first = sources[0]
                    video_url = _fix(first.get("file") or first.get("src") or "")
                for track in config.get("tracks") or []:
                    if track.get("kind") == "captions" and track.get("file"):
                        subtitles.append(
                            Subtitle(
                                name = track.get("label") or "Altyazı",
                                url  = _fix(track["file"]),
                            )
                        )

            # 2) Yedek: eski file_link="<base64>" şeması (P.A.C.K.E.R'lı)
            if not video_url:
                script = None
                for m in re.finditer(r"<script[^>]*>(.*?)</script>", html, re.DOTALL):
                    body = m.group(1)
                    if "file_link" in body:
                        script = body
                        break
                if script:
                    if _JSUnpacker.detect(script):
                        script = _JSUnpacker.unpack(script)
                    link_match = re.search(r'file_link="([^"]+)"', script)
                    if link_match:
                        try:
                            video_url = base64.b64decode(link_match.group(1)).decode("utf-8")
                        except Exception:
                            video_url = None

            if not video_url:
                return None

            return ExtractResult(
                name      = f"{self.name} | {source_name}",
                url       = video_url,
                referer   = f"{self.main_url}/",
                subtitles = subtitles,
            )
        except Exception:
            return None
