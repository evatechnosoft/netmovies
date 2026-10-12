# NetMovies — DiziAbi eklentisi
# Upstream (Kekik-cloudstream) karşılığı yok. Lioness S3B8 için eklendi: DiziMom'un
# sayfası S1E8 videosunu taşıyordu, DiziAbi gerçek finali (68 dk) veriyor.
# Zincir: bölüm sayfası `data-pv` → pilavyer `s.php?s=` → `window.__PLAYER__.stream`
# (HLS master; Türkçe dublaj + orijinal ses aynı akışta, altyazılar `subs`).

from __future__ import annotations

import json
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

# Upstream .kt yok; adres taşınırsa DIZIABI_URL ile sabitlenir.
_MAIN_URL = discover_main_url("DiziAbi/none.kt", "https://www.diziabi.net", "DIZIABI_URL", signature="DiziAbi")
_PLAYER   = "https://play2.pilavyerplay.top"


class DiziAbi(PluginBase):
    name        = "DiziAbi"
    language    = "tr"
    main_url    = _MAIN_URL
    favicon     = f"https://www.google.com/s2/favicons?domain={_MAIN_URL}&sz=64"
    description = "DiziAbi — yabancı diziler, Türkçe dublaj + altyazı."

    main_page = {
        f"{main_url}/yabanci-dizi-izle/?page=SAYFA": "Yabancı Diziler",
        f"{main_url}/tur/aksiyon?page=SAYFA"       : "Aksiyon",
        f"{main_url}/tur/suc?page=SAYFA"           : "Suç",
        f"{main_url}/tur/bilim-kurgu?page=SAYFA"   : "Bilim Kurgu",
    }

    def _kartlar(self, text: str) -> list[tuple[str, str, str | None]]:
        """Liste/arama kartları: (başlık, adres, poster). Yalnız diziler — film sayfası
        bölüm listesi taşımaz."""
        out = []
        for a in HTMLHelper(text).select("a.group.block"):
            href  = a.attrs.get("href") or ""
            title = a.select_text("h3")
            if "/dizi/" not in href or not title:
                continue
            out.append((title, normalize_url(href, self.main_url), a.select_attr("img", "src")))
        return out

    async def get_main_page(self, page: int, url: str, category: str) -> list[MainPageResult]:
        text = await fetch_html(self.httpx, url.replace("SAYFA", str(page or 1)))
        return [
            MainPageResult(category=category, title=t, url=u, poster=p)
            for t, u, p in self._kartlar(text)
        ]

    async def search(self, query: str) -> list[SearchResult]:
        text = await fetch_html(self.httpx, f"{self.main_url}/ara?q={query}")
        return [SearchResult(title=t, url=u, poster=p) for t, u, p in self._kartlar(text)]

    async def load_item(self, url: str) -> SeriesInfo:
        url    = normalize_url(url, self.main_url).split("?")[0]
        text   = await fetch_html(self.httpx, url)
        secici = HTMLHelper(text)

        # Sayfa yalnız seçili sezonu listeler; diğerleri `?sezon=N`.
        sezonlar = sorted({int(n) for n in re.findall(r"\?sezon=(\d+)#bolumler", text)}) or [1]
        episodes: list[Episode] = []
        for sezon in sezonlar:
            sayfa = await fetch_html(self.httpx, f"{url}?sezon={sezon}")
            for a in HTMLHelper(sayfa).select("a.group"):
                href = a.attrs.get("href") or ""
                # Numara adresten: kart metni bölüm adıyla birleşiyor ("2831" + "2. Sezon").
                m    = re.search(r"-(\d+)-sezon-(\d+)-bolum", href)
                if "/bolum/" not in href or not m or int(m.group(1)) != sezon:
                    continue
                episodes.append(Episode(
                    season  = sezon,
                    episode = int(m.group(2)),
                    title   = f"{m.group(2)}. Bölüm",
                    url     = normalize_url(href, self.main_url),
                ))

        baslik = secici.select_attr("meta[property='og:title']", "content") or ""
        return SeriesInfo(
            url         = url,
            title       = baslik.split(" | ")[0].strip() or secici.select_text("h1"),
            poster      = secici.select_attr("meta[property='og:image']", "content"),
            description = secici.select_attr("meta[property='og:description']", "content"),
            episodes    = episodes,
        )

    async def load_links(self, url: str) -> list[ExtractResult]:
        text = await fetch_html(self.httpx, normalize_url(url, self.main_url))
        slug = re.search(r'data-pv="([^"]+)"', text)
        if not slug:
            return []
        oynatici = await fetch_html(
            self.httpx, f"{_PLAYER}/assets/js/s.php?s={slug.group(1)}",
            headers={"Referer": f"{self.main_url}/"},
        )
        m = re.search(r"window\.__PLAYER__\s*=\s*(\{.*?\});", oynatici, re.S)
        if not m:
            return []
        veri = json.loads(m.group(1))
        if not veri.get("stream"):
            return []
        # Türkçe altyazı önde: oynatıcı ilkini varsayılan alıyor.
        subs = sorted(veri.get("subs") or [], key=lambda s: s.get("lang") != "tr")
        return [ExtractResult(
            name      = f"{self.name} | Dublaj + Orijinal",
            url       = veri["stream"],
            referer   = f"{_PLAYER}/",
            subtitles = [Subtitle(name=s.get("label") or s.get("lang") or "Altyazı", url=s["src"]) for s in subs if s.get("src")],
        )]
