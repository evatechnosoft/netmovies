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

_YOUTUBE_ADRESI = re.compile(r"(?:youtube\.com|youtu\.be)/", re.I)

# key="value" çiftlerini SIRADAN BAĞIMSIZ yakalar (Gemini taslağındaki
# sıra-bağımlı tek-regex hatasının düzeltilmiş hali).
_ATTR_RE = re.compile(r'([\w-]+)="([^"]*)"')


# M3U `group-title` ham geliyor: "Undefined", "Animation;Kids", "Business;Series"
# gibi değerler ana sayfada tek posterlik çöp raflara dönüşüyordu. Çoklu grupta ilk
# grup esas alınır, İngilizce adlar Türkçeleşir.
_GROUP_TR = {
    "general": "Genel", "undefined": "Genel", "unknown": "Genel", "n/a": "Genel", "other": "Genel",
    "news": "Haber", "music": "Müzik", "sports": "Spor", "sport": "Spor",
    "kids": "Çocuk", "movies": "Film", "movie": "Film", "series": "Dizi",
    "documentary": "Belgesel", "religious": "Dini", "entertainment": "Eğlence",
    "education": "Eğitim", "culture": "Kültür", "business": "İş", "lifestyle": "Yaşam",
    "travel": "Gezi", "animation": "Animasyon", "comedy": "Komedi", "shop": "Alışveriş",
}


def _normalize_group(raw: str | None) -> str:
    """Ham `group-title` → tek, Türkçe, anlamlı grup adı."""
    ilk = (raw or "").split(";")[0].strip()
    if not ilk:
        return "Genel"
    return _GROUP_TR.get(ilk.lower(), ilk)


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
                "group":  _normalize_group(attrs.get("group-title")),
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
        self._damga: dict[str, float] = {}
        self._load_sources()
        # Grupları kategori olarak main_page'e yaz (UI kategori sekmeleri buradan gelir)
        groups = sorted({it["group"] for it in self._items})
        self.main_page = {f"m3u://group/{g}": g for g in groups} or {"m3u://group/Genel": "Genel"}

    # ------------------------------------------------------------------ Kaynak yükleme
    def _tazele(self):
        """Yerel liste dosyası değiştiyse yeniden okur.

        Yönetim panelindeki "Canlı Kanallar" kartı `/data/lists/ozel.m3u`'yu
        yazıyor; eklenti listeyi yalnız kurulumda okusaydı her düzenlemeden sonra
        engine'i yeniden başlatmak gerekirdi.
        """
        damga = {}
        for src in self._yerel_kaynaklar():
            try:
                damga[src] = os.path.getmtime(src)
            except OSError:
                continue
        if damga == self._damga:
            return
        self._damga = damga
        self._items = []
        self._load_sources()
        gruplar = sorted({it["group"] for it in self._items})
        self.main_page = {f"m3u://group/{g}": g for g in gruplar} or {"m3u://group/Genel": "Genel"}

    @staticmethod
    def _yerel_kaynaklar() -> list[str]:
        raw = os.getenv("M3U_SOURCES", "").strip()
        return [s.strip() for s in raw.split(",") if s.strip() and not s.strip().startswith(("http://", "https://"))]

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
        self._tazele()
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
        self._tazele()
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
        self._tazele()
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
        self._tazele()
        item = next((it for it in self._items if it["stream_url"] == url), None)
        headers = item["headers"] if item else {}
        title   = item["title"] if item else "M3U"

        # Kanalların bir kısmı yayıncının RESMİ YouTube canlı yayını (ATV, Show TV…).
        # YouTube adresi doğrudan oynatılamaz: yt-dlp canlı yayının HLS master'ını
        # üretir (ses ayrı rendition olduğu için `manifest_url` alınır, tek format
        # değil — DDizi'deki sessiz-video dersiyle aynı).
        if _YOUTUBE_ADRESI.search(url):
            from Plugins.__warp_client import ytdlp_info

            info = await ytdlp_info(url, timeout=90.0)
            master = next(
                (f["manifest_url"] for f in (info or {}).get("formats") or [] if f.get("manifest_url")),
                None,
            )
            if master:
                return [
                    ExtractResult(
                        name       = f"{self.name} | {title} (canlı)",
                        url        = master,
                        referer    = "",
                        user_agent = headers.get("User-Agent") or "Mozilla/5.0",
                    )
                ]
            return []

        return [
            ExtractResult(
                name          = f"{self.name} | {title}",
                url           = url,
                referer       = headers.get("Referer"),
                user_agent    = headers.get("User-Agent"),
                extra_headers = {k: v for k, v in headers.items() if k not in ("Referer", "User-Agent")},
            )
        ]
