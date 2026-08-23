# NetMovies — RecTV eklentisi
# KekikStream PluginBase formatına, keyiflerolsun/Kekik-cloudstream (Kotlin) referans.
# Tek API'den Canlı TV + Son Filmler + Son Diziler; extractor GEREKTİRMEZ, doğrudan
# m3u8/mp4 döner. "Kırmızı/çekemiyor" sorununun iki tipik sebebi burada çözülür:
#   1) Domain sık değişir (b.prectvNN.sbs) → RECTV_URL env ile anında güncellenir,
#      plugin_health domain düşünce uyarır.
#   2) API "user-agent: okhttp/4.12.0", oynatma "User-Agent: googleusercontent"
#      header'ı ister → burada doğru set edilir.

from __future__ import annotations

import os
import json

from KekikStream.Core import (
    PluginBase,
    MainPageResult,
    SearchResult,
    MovieInfo,
    SeriesInfo,
    Episode,
    ExtractResult,
)

# Domain değişince: docker-compose'da RECTV_URL ver ya da burada güncelle.
_MAIN_URL = (os.getenv("RECTV_URL") or "https://b.prectv38.sbs").rstrip("/")
_SW_KEY   = os.getenv("RECTV_SW_KEY") or "4F5A9C3D9A86FA54EACEDDD635185/c3c5bd17-e37b-4b94-a944-8a3688a30452"

_API_UA    = "okhttp/4.12.0"          # API istekleri bu UA olmadan reddediyor
_PLAY_UA   = "googleusercontent"      # oynatma/proxy bu UA ile çekmeli
_PLAY_REF  = "https://twitter.com/"


class RecTV(PluginBase):
    name        = "RecTV"
    language    = "tr"
    main_url    = _MAIN_URL
    favicon     = "https://www.google.com/s2/favicons?domain=https://rectv.me&sz=64"
    description = "RecTV — Canlı TV, son filmler ve diziler (tek API, doğrudan yayın)."

    # "SAYFA" placeholder get_main_page'de sayfa numarasıyla değişir.
    main_page = {
        f"{_MAIN_URL}/api/channel/by/filtres/0/0/SAYFA/{_SW_KEY}/"      : "Canlı TV",
        f"{_MAIN_URL}/api/movie/by/filtres/0/created/SAYFA/{_SW_KEY}/"  : "Son Filmler",
        f"{_MAIN_URL}/api/serie/by/filtres/0/created/SAYFA/{_SW_KEY}/"  : "Son Diziler",
        f"{_MAIN_URL}/api/movie/by/filtres/1/created/SAYFA/{_SW_KEY}/"  : "Aksiyon",
        f"{_MAIN_URL}/api/movie/by/filtres/2/created/SAYFA/{_SW_KEY}/"  : "Dram",
        f"{_MAIN_URL}/api/movie/by/filtres/3/created/SAYFA/{_SW_KEY}/"  : "Komedi",
        f"{_MAIN_URL}/api/movie/by/filtres/4/created/SAYFA/{_SW_KEY}/"  : "Bilim Kurgu",
        f"{_MAIN_URL}/api/movie/by/filtres/8/created/SAYFA/{_SW_KEY}/"  : "Korku",
        f"{_MAIN_URL}/api/movie/by/filtres/13/created/SAYFA/{_SW_KEY}/" : "Animasyon",
        f"{_MAIN_URL}/api/movie/by/filtres/19/created/SAYFA/{_SW_KEY}/" : "Belgesel",
    }

    def _is_live(self, item: dict) -> bool:
        return (item.get("label") or "").lower() == "canlı" or (item.get("label") or "") == "CANLI"

    # ------------------------------------------------------------------ Ana sayfa
    async def get_main_page(self, page: int, url: str, category: str) -> list[MainPageResult]:
        target   = url.replace("SAYFA", str((page or 1) - 1))  # API 0-indexli
        response = await self.httpx.get(target, headers={"user-agent": _API_UA})
        try:
            items = response.json()
        except Exception:
            return []
        if not isinstance(items, list):
            return []

        results: list[MainPageResult] = []
        for item in items:
            if not item.get("title"):
                continue
            results.append(
                MainPageResult(
                    category = category,
                    title    = item["title"],
                    url      = json.dumps(item, ensure_ascii=False, separators=(",", ":")),
                    poster   = item.get("image"),
                )
            )
        return results

    # ------------------------------------------------------------------ Arama
    async def search(self, query: str) -> list[SearchResult]:
        response = await self.httpx.get(
            f"{self.main_url}/api/search/{query}/{_SW_KEY}/",
            headers={"user-agent": _API_UA},
        )
        try:
            data = response.json()
        except Exception:
            return []

        results: list[SearchResult] = []
        for key in ("channels", "posters"):
            for item in (data.get(key) or []):
                if not item.get("title"):
                    continue
                results.append(
                    SearchResult(
                        title  = item["title"],
                        url    = json.dumps(item, ensure_ascii=False, separators=(",", ":")),
                        poster = item.get("image"),
                    )
                )
        return results

    # ------------------------------------------------------------------ Detay
    async def load_item(self, url: str) -> MovieInfo | SeriesInfo:
        try:
            item = json.loads(url)
        except Exception:
            # Doğrudan m3u8 gelirse (canlı) minimal bilgi
            return MovieInfo(url=url, title="RecTV")

        genres = ", ".join(g.get("title", "") for g in (item.get("genres") or []) if g.get("title")) or None

        if item.get("type") == "serie":
            episodes: list[Episode] = []
            try:
                resp = await self.httpx.get(
                    f"{self.main_url}/api/season/by/serie/{item.get('id')}/{_SW_KEY}/",
                    headers={"user-agent": _API_UA},
                )
                seasons = resp.json()
            except Exception:
                seasons = []

            import re
            for season in (seasons or []):
                s_title = season.get("title", "")
                s_num = re.search(r"\d+", s_title)
                for ep in (season.get("episodes") or []):
                    srcs = ep.get("sources") or []
                    if not srcs:
                        continue
                    e_num = re.search(r"\d+", ep.get("title", ""))
                    episodes.append(
                        Episode(
                            season  = int(s_num.group()) if s_num else 1,
                            episode = int(e_num.group()) if e_num else None,
                            title   = ep.get("title"),
                            # bölümün ilk kaynağını (m3u8) doğrudan taşı
                            url     = srcs[0].get("url"),
                        )
                    )
            return SeriesInfo(
                url         = url,
                title       = item.get("title"),
                poster      = item.get("image"),
                description = item.get("description"),
                tags        = genres,
                rating      = item.get("rating"),
                year        = item.get("year"),
                episodes    = episodes,
            )

        return MovieInfo(
            url         = url,
            title       = item.get("title"),
            poster      = item.get("image"),
            description = item.get("description"),
            tags        = genres,
            rating      = item.get("rating"),
            year        = item.get("year"),
        )

    # ------------------------------------------------------------------ Linkler
    async def load_links(self, url: str) -> list[ExtractResult]:
        # Bölüm URL'i doğrudan m3u8 olabilir (load_item dizide öyle taşıyor)
        if url.startswith("http"):
            return [ExtractResult(
                name       = self.name,
                url        = url,
                referer    = _PLAY_REF,
                user_agent = _PLAY_UA,
            )]

        try:
            item = json.loads(url)
        except Exception:
            return []

        results: list[ExtractResult] = []
        for source in (item.get("sources") or []):
            src_url = source.get("url")
            if not src_url:
                continue
            results.append(
                ExtractResult(
                    name       = f"{self.name} - {source.get('type', 'kaynak')}",
                    url        = src_url,
                    referer    = _PLAY_REF,
                    user_agent = _PLAY_UA,
                )
            )
        return self.deduplicate(results)
