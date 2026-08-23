# NetMovies — M3U/M3U8 Playlist eklentisi
# Kullanıcının kendi M3U listelerini (IPTV, kişisel kaynaklar) KekikStream plugin
# arayüzüne bağlar; böylece stream'in tüm akışı (liste, oynatıcı, header-proxy,
# izleme geçmişi) kod değişmeden bu listelerle çalışır.
#
# Kaynaklar `M3U_SOURCES` ortam değişkeninden gelir (virgülle ayrılmış):
#   - Yerel dosya yolu:  /data/lists/kanallarim.m3u
#   - Uzak URL:          https://ornek.com/liste.m3u8
# EXTVLCOPT / EXTHTTP satırlarındaki header'lar (Referer, User-Agent) korunur.

from __future__ import annotations

import os
import re
import urllib.request

from KekikStream.Core import (
    PluginBase,
    MainPageResult,
    SearchResult,
    MovieInfo,
    ExtractResult,
)

# key="value" çiftlerini SIRADAN BAĞIMSIZ yakalar (Gemini taslağındaki
# sıra-bağımlı tek-regex hatasının düzeltilmiş hali).
_ATTR_RE = re.compile(r'([\w-]+)="([^"]*)"')


def _parse_m3u(content: str) -> list[dict]:
    """Bir M3U/M3U8 metnini normalize edilmiş öğe listesine çevirir."""
    items: list[dict] = []
    meta: dict | None = None
    headers: dict[str, str] = {}

    for raw in content.splitlines():
        line = raw.strip()
        if not line:
            continue

        if line.startswith("#EXTINF:"):
            attrs = dict(_ATTR_RE.findall(line))
            title = line.rsplit(",", 1)[-1].strip()
            meta = {
                "title":  title or attrs.get("tvg-name", "Bilinmeyen"),
                "group":  attrs.get("group-title") or "Genel",
                "poster": attrs.get("tvg-logo", ""),
                "tvg_id": attrs.get("tvg-id", ""),
            }
            headers = {}

        elif line.startswith("#EXTVLCOPT:"):
            opt = line.split(":", 1)[1]
            if "=" in opt:
                k, v = opt.split("=", 1)
                k = k.strip().lower()
                if k in ("http-referrer", "http-referer"):
                    headers["Referer"] = v.strip()
                elif k == "http-user-agent":
                    headers["User-Agent"] = v.strip()

        elif line.startswith("#EXTHTTP:"):
            import json as _json
            try:
                for k, v in _json.loads(line.split(":", 1)[1]).items():
                    headers[k] = str(v)
            except Exception:
                pass

        elif not line.startswith("#") and meta:
            items.append({
                "title":      meta["title"],
                "group":      meta["group"],
                "poster":     meta["poster"],
                "stream_url": line,
                "headers":    dict(headers),
            })
            meta = None
            headers = {}

    return items


class M3UPlaylist(PluginBase):
    name        = "M3U Listelerim"
    language    = "tr"
    main_url    = "m3u://local"
    favicon     = "https://www.google.com/s2/favicons?domain=https://m3u.local&sz=64"
    description = "Kendi M3U/M3U8 listeleriniz (IPTV, kişisel kaynaklar)."

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self._items: list[dict] = []
        self._load_sources()
        # Grupları kategori olarak main_page'e yaz (UI kategori sekmeleri buradan gelir)
        groups = sorted({it["group"] for it in self._items})
        self.main_page = {f"m3u://group/{g}": g for g in groups} or {"m3u://group/Genel": "Genel"}

    # ------------------------------------------------------------------ Kaynak yükleme
    def _load_sources(self):
        raw = os.getenv("M3U_SOURCES", "").strip()
        if not raw:
            return
        for src in (s.strip() for s in raw.split(",") if s.strip()):
            try:
                if src.startswith(("http://", "https://")):
                    req = urllib.request.Request(src, headers={"User-Agent": "Mozilla/5.0"})
                    with urllib.request.urlopen(req, timeout=10) as resp:
                        content = resp.read().decode("utf-8", errors="ignore")
                else:
                    with open(src, "r", encoding="utf-8", errors="ignore") as fh:
                        content = fh.read()
                self._items.extend(_parse_m3u(content))
            except Exception:
                # Bir kaynak hatalıysa diğerlerini düşürme
                continue

    def _group_of(self, url: str) -> str:
        return url.split("m3u://group/", 1)[-1] if url.startswith("m3u://group/") else url

    # ------------------------------------------------------------------ Ana sayfa
    async def get_main_page(self, page: int, url: str, category: str) -> list[MainPageResult]:
        if page and page > 1:
            return []
        group = self._group_of(url)
        return [
            MainPageResult(
                category = category,
                title    = it["title"],
                url      = it["stream_url"],
                poster   = it["poster"] or None,
            )
            for it in self._items
            if it["group"] == group
        ]

    # ------------------------------------------------------------------ Arama
    async def search(self, query: str) -> list[SearchResult]:
        q = query.casefold().strip()
        return [
            SearchResult(
                title  = it["title"],
                url    = it["stream_url"],
                poster = it["poster"] or None,
            )
            for it in self._items
            if q in it["title"].casefold()
        ]

    # ------------------------------------------------------------------ Detay
    async def load_item(self, url: str) -> MovieInfo:
        item = next((it for it in self._items if it["stream_url"] == url), None)
        if not item:
            return MovieInfo(url=url, title=url)
        return MovieInfo(
            url    = item["stream_url"],
            title  = item["title"],
            poster = item["poster"] or None,
            tags   = [item["group"]] if item["group"] else None,
        )

    # ------------------------------------------------------------------ Linkler
    async def load_links(self, url: str) -> list[ExtractResult]:
        item = next((it for it in self._items if it["stream_url"] == url), None)
        headers = item["headers"] if item else {}
        title   = item["title"] if item else "M3U"
        return [
            ExtractResult(
                name          = f"{self.name} | {title}",
                url           = url,
                referer       = headers.get("Referer"),
                user_agent    = headers.get("User-Agent"),
                extra_headers = {k: v for k, v in headers.items() if k not in ("Referer", "User-Agent")},
            )
        ]
