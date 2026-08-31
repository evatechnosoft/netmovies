# NetMovies — DiziYou eklentisi
# KekikStream PluginBase formatına, keyiflerolsun/Kekik-cloudstream (Kotlin) referans
# alınarak uyarlanmıştır. En temiz dizi kaynağı: harici extractor/iframe çözme yok;
# oynatma linkini doğrudan sitenin storage m3u8'inden üretir (Orijinal + Türkçe Dublaj)
# ve .vtt altyazılarını ekler.

from __future__ import annotations

import re

from KekikStream.Core import (
    PluginBase,
    MainPageResult,
    SearchResult,
    SeriesInfo,
    Episode,
    ExtractResult,
    Subtitle,
    HTMLHelper,
)

try:
    from Plugins.__dizi_common import fetch_html, normalize_url
    from Plugins.__kekik_domain import discover_main_url
except Exception:
    import sys, os as _os
    sys.path.insert(0, _os.path.dirname(__file__))
    from __dizi_common import fetch_html, normalize_url
    from __kekik_domain import discover_main_url

# Güncel domain otomatik çekilir; DIZIYOU_URL ile elle sabitlenebilir.
_MAIN_URL = discover_main_url(
    "DiziYou/src/main/kotlin/com/keyiflerolsun/DiziYou.kt",
    "https://www.diziyou.one",
    "DIZIYOU_URL",
)


class DiziYou(PluginBase):
    name        = "DiziYou"
    language    = "tr"
    main_url    = _MAIN_URL
    favicon     = f"https://www.google.com/s2/favicons?domain={_MAIN_URL}&sz=64"
    description = "DiziYou — Türkçe dublaj/altyazı yerli ve yabancı diziler."

    # get_main_page'de "SAYFA" placeholder'ı gerçek sayfa numarasıyla değiştirilir.
    main_page = {
        f"{main_url}/dizi-arsivi/page/SAYFA/?tur=Aksiyon"    : "Aksiyon",
        f"{main_url}/dizi-arsivi/page/SAYFA/?tur=Dram"       : "Dram",
        f"{main_url}/dizi-arsivi/page/SAYFA/?tur=Komedi"     : "Komedi",
        f"{main_url}/dizi-arsivi/page/SAYFA/?tur=Gerilim"    : "Gerilim",
        f"{main_url}/dizi-arsivi/page/SAYFA/?tur=Bilim+Kurgu": "Bilim Kurgu",
        f"{main_url}/dizi-arsivi/page/SAYFA/?tur=Fantazi"    : "Fantazi",
        f"{main_url}/dizi-arsivi/page/SAYFA/?tur=Macera"     : "Macera",
        f"{main_url}/dizi-arsivi/page/SAYFA/?tur=Korku"      : "Korku",
        f"{main_url}/dizi-arsivi/page/SAYFA/?tur=Gizem"      : "Gizem",
        f"{main_url}/dizi-arsivi/page/SAYFA/?tur=Belgesel"   : "Belgesel",
        f"{main_url}/dizi-arsivi/page/SAYFA/?tur=Animasyon"  : "Animasyon",
    }

    # ------------------------------------------------------------------ Ana sayfa
    async def get_main_page(self, page: int, url: str, category: str) -> list[MainPageResult]:
        target   = url.replace("SAYFA", str(page or 1))
        text     = await fetch_html(self.httpx, normalize_url(target, self.main_url))
        secici   = HTMLHelper(text)

        results: list[MainPageResult] = []
        for node in secici.select("div.single-item"):
            link = node.select_first("div#categorytitle a")
            if not link:
                continue
            title = link.text(strip=True)
            href  = link.attrs.get("href")
            if not title or not href:
                continue
            poster = node.select_attr("img", "src")
            results.append(
                MainPageResult(
                    category = category,
                    title    = title,
                    url      = normalize_url(self.fix_url(href), self.main_url),
                    poster   = self.fix_url(poster) if poster else None,
                )
            )
        return results

    # ------------------------------------------------------------------ Arama
    async def search(self, query: str) -> list[SearchResult]:
        text     = await fetch_html(self.httpx, f"{self.main_url}/?s={query}")
        secici   = HTMLHelper(text)

        results: list[SearchResult] = []
        # Arama sonuçları hem liste konteynerinde hem tekil kartlarda gelebilir.
        for node in secici.select("div#list-series, div.single-item"):
            link = node.select_first("div#categorytitle a") or node.select_first("a")
            if not link:
                continue
            title = link.text(strip=True)
            href  = link.attrs.get("href")
            if not title or not href:
                continue
            poster = node.select_attr("img", "src")
            results.append(
                SearchResult(
                    title  = title,
                    url    = normalize_url(self.fix_url(href), self.main_url),
                    poster = self.fix_url(poster) if poster else None,
                )
            )
        return results

    # ------------------------------------------------------------------ Detay
    async def load_item(self, url: str) -> SeriesInfo:
        text     = await fetch_html(self.httpx, normalize_url(url, self.main_url))
        secici   = HTMLHelper(text)

        title       = secici.select_text("h1")
        poster      = secici.select_attr("div.category_image img", "src")
        description = None
        desc_node   = secici.select_first("div.diziyou_desc")
        if desc_node:
            description = desc_node.select_direct_text() or desc_node.text(strip=True)
        tags        = secici.select_texts("div.genres a")

        episodes: list[Episode] = []
        for a in secici.select("a"):
            box = a.select_first("div.bolumust")
            if not box:
                continue
            ep_title_node = box.select_first("div.baslik")
            if not ep_title_node:
                continue
            ep_meta = ep_title_node.text(strip=True)
            href    = a.attrs.get("href")
            if not href:
                continue
            se = re.search(r"(\d+)\.\s*Sezon", ep_meta)
            ep = re.search(r"(\d+)\.\s*Bölüm", ep_meta)
            ep_name = box.select_text("div.bolumismi") or ep_meta
            ep_name = ep_name.replace("(", "").replace(")", "").strip()
            episodes.append(
                Episode(
                    season  = int(se.group(1)) if se else 1,
                    episode = int(ep.group(1)) if ep else None,
                    title   = ep_name,
                    url     = normalize_url(self.fix_url(href), self.main_url),
                )
            )

        return SeriesInfo(
            url         = normalize_url(url, self.main_url),
            title       = title,
            poster      = self.fix_url(poster) if poster else None,
            description = description,
            tags        = tags,
            episodes    = episodes,
        )

    # ------------------------------------------------------------------ Linkler
    async def load_links(self, url: str) -> list[ExtractResult]:
        text     = await fetch_html(self.httpx, normalize_url(url, self.main_url))
        secici   = HTMLHelper(text)

        player = secici.select_first("iframe#diziyouPlayer")
        if not player:
            return []
        src = player.attrs.get("src") or ""
        item_id = src.rstrip("/").split("/")[-1].split(".html")[0]
        if not item_id:
            return []

        storage = self.main_url.replace("www", "storage")
        referer = f"{self.main_url}/"

        # Mevcut dil seçeneklerini oku (span.diziyouOption id'leri)
        option_ids = {
            node.attrs.get("id")
            for node in secici.select("span.diziyouOption")
            if node.attrs.get("id")
        }

        subtitles: list[Subtitle] = []
        if "turkceAltyazili" in option_ids:
            subtitles.append(Subtitle(name="Türkçe", url=f"{storage}/subtitles/{item_id}/tr.vtt"))
        if "ingilizceAltyazili" in option_ids:
            subtitles.append(Subtitle(name="English", url=f"{storage}/subtitles/{item_id}/en.vtt"))

        results: list[ExtractResult] = []
        # Orijinal dil (Türkçe/İngilizce altyazı seçeneği varsa)
        if option_ids & {"turkceAltyazili", "ingilizceAltyazili"}:
            results.append(
                ExtractResult(
                    name      = f"{self.name} | Orijinal Dil",
                    url       = f"{storage}/episodes/{item_id}/play.m3u8",
                    referer   = referer,
                    subtitles = list(subtitles),
                )
            )
        # Türkçe dublaj
        if "turkceDublaj" in option_ids:
            results.append(
                ExtractResult(
                    name    = f"{self.name} | Türkçe Dublaj",
                    url     = f"{storage}/episodes/{item_id}_tr/play.m3u8",
                    referer = referer,
                )
            )

        return self.deduplicate(results)
