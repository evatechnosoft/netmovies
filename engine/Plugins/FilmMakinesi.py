# NetMovies — FilmMakinesi eklentisi
#
# Site `filmmakinesi.co`'ya taşındı ve temayı da değiştirdi (`.to` NXDOMAIN,
# `.de` SSL EOF). Eski `.to` yapısının hiçbir parçası geçerli değil:
#   - Kartlar: `<a href title class="poster">` (arşiv/tür/arama sayfalarında aynı).
#   - Tür rafları: `/<tur>-filmleri-hd-izle/`, arşiv `/film-arsivi/` (+ `page/N/`),
#     arama WordPress `?s=` (zayıf: yalnız başlık/açıklama eşleşmesi).
#   - Oynatıcı: `oynatloload.top/embed/<id>`. JW config artık sayfada değil,
#     iki JSON ucunda: embed'i Referer ile GET et -> `ultra_embed_auth` çerezi ->
#     `/api/video-bilgi/<id>` kalite + altyazı verir. Çerezsiz `domain_not_allowed`.
#     Manifest jetonu ~4 saat, Referer'sız 200; kaynak yine `_ALWAYS_PROXY_PLUGINS`
#     içinde bırakıldı ki jeton istemciye sızmasın.

from __future__ import annotations

import re

from KekikStream.Core import ExtractResult, HTMLHelper, MainPageResult, MovieInfo, PluginBase, SearchResult, Subtitle
from Plugins.__kekik_domain import discover_main_url

_DISCOVERED_URL = discover_main_url(
    "FilmMakinesi/src/main/kotlin/com/keyiflerolsun/FilmMakinesi.kt", "https://filmmakinesi.co", "FILMMAKINESI_URL"
)
# Upstream hâlâ ölü `.de`/`.to`'yu gösteriyor; doğrulanmış `.co`'ya çekilir.
_MAIN_URL = "https://filmmakinesi.co" if re.search(r"filmmakinesi\.(de|to)/?$", _DISCOVERED_URL) else _DISCOVERED_URL

_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"

_KART   = re.compile(r'<a href="([^"]+)" title="([^"]*)" class="poster">(.*?)</a>', re.S)
_POSTER = re.compile(r'<img[^>]+src="([^"]+)"')
_EMBED  = re.compile(r'<iframe[^>]+src="(https?://[^"]*/embed/(\d+))"')
# Düğmenin tamamı alınır: metinden önce bir <svg> geliyor, `>` sonrası ilk parçayı
# okuyan desen hep boş dönüyordu. Blokta hem `data-lang="dual"` hem görünen etiket var.
_DIL    = re.compile(r'<button[^>]*class="language-link".*?</button>', re.S | re.I)
# Arşivin ilk kartları henüz vizyona girmemiş yapımlar: sayfalarında yalnız YouTube
# fragmanı var, oynatıcı yok. Ayırt eden işaret kartta: dil rozeti (`poster-lang`)
# yalnız izlenebilir kayıtlarda basılıyor. Rozetsiz kart kataloğa girerse "oynat"
# boş dönüyor (smoke.sh zinciri Şrek 5 / Buz Devri 6'da kırmızıya düştü).
_ROZET  = re.compile(r'poster-lang')


class FilmMakinesi(PluginBase):
    name        = "FilmMakinesi"
    language    = "tr"
    main_url    = _MAIN_URL
    favicon     = f"https://www.google.com/s2/favicons?domain={_MAIN_URL}&sz=64"
    description = "FilmMakinesi — Türkçe dublaj/altyazılı filmler, tür rafları."

    # Kategori adlarında "Film" ya da tür adı geçer (aggregate `_pick_categories` bunu arar).
    main_page = {
        f"{main_url}/film-arsivi/"                  : "Yeni Filmler",
        f"{main_url}/populer-filmler/"              : "Popüler Filmler",
        f"{main_url}/aksiyon-filmleri-hd-izle/"     : "Aksiyon",
        f"{main_url}/bilim-kurgu-filmleri-hd-izle/" : "Bilim Kurgu",
        f"{main_url}/macera-filmleri-hd-izle/"      : "Macera",
        f"{main_url}/komedi-fimleri-hd-izle/"       : "Komedi",
        f"{main_url}/gerilim-filmleri-hd-izle/"     : "Gerilim",
        f"{main_url}/korku-filmleri-hd-izle/"       : "Korku",
        f"{main_url}/dram-filmleri-hd-izle/"        : "Dram",
        f"{main_url}/fantastik-filmleri-hd-izle/"   : "Fantastik",
        f"{main_url}/animasyon-filmleri-hd-izle/"   : "Animasyon",
        f"{main_url}/suc-filmleri-hd-izle/"         : "Suç",
        f"{main_url}/yerli-filmleri-hd-izle/"       : "Yerli Filmler",
    }

    def _cards(self, html: str) -> list[tuple[str, str, str | None]]:
        out: list[tuple[str, str, str | None]] = []
        for href, title, govde in _KART.findall(html):
            if not (href and title) or not _ROZET.search(govde):
                continue
            poster = _POSTER.search(govde)
            out.append((title.strip(), self.fix_url(href), self.fix_url(poster.group(1)) if poster else None))
        return out

    async def get_main_page(self, page: int, url: str, category: str) -> list[MainPageResult]:
        target   = url if page <= 1 else f"{url.rstrip('/')}/page/{page}/"
        response = await self.httpx.get(target, headers={"User-Agent": _UA})
        if response.status_code != 200:
            return []
        return [MainPageResult(category=category, title=t, url=u, poster=p) for t, u, p in self._cards(response.text)]

    async def search(self, query: str) -> list[SearchResult]:
        response = await self.httpx.get(f"{self.main_url}/", params={"s": query}, headers={"User-Agent": _UA})
        return [SearchResult(title=t, url=u, poster=p) for t, u, p in self._cards(response.text)]

    async def load_item(self, url: str) -> MovieInfo:
        response = await self.httpx.get(url, headers={"User-Agent": _UA})
        secici   = HTMLHelper(response.text)

        # h1: "Ballerina 2026 izle <small>(2025)</small>" — yıl small'da; başlıktaki
        # sayı filmin adının parçası olabiliyor ("Fall 2"), o yüzden ikisi ayrı okunur.
        h1    = secici.select_text("h1") or ""
        yil   = secici.regex_first(r"\((\d{4})\)", h1)
        title = re.sub(r"\s*\(\d{4}\)\s*$", "", re.sub(r"\s*izle\b.*$", "", h1, flags=re.I).strip()).strip()

        return MovieInfo(
            url         = url,
            title       = title or h1.strip(),
            poster      = self.fix_url(secici.og_poster) if secici.og_poster else None,
            description = secici.og_description,
            # Tür breadcrumb'ın 2. basamağında (`/<tur>-filmleri-hd-izle/`).
            tags        = secici.select_texts("a[href*='-filmleri-hd-izle']"),
            year        = yil,
        )

    async def load_links(self, url: str) -> list[ExtractResult]:
        page = await self.httpx.get(url, headers={"User-Agent": _UA})

        # Dil rozeti sekme düğmesinde: "DUAL (Türkçe Dublaj & Altyazılı)" / "Altyazılı".
        # Ada "dublaj" girmezse gateway (Libs/language.py) altyazı dosyasına bakıp
        # kaynağı yanlışlıkla "Türkçe altyazı" sayıyor.
        rozet = " ".join(_DIL.findall(page.text)).lower()
        dil   = " | Türkçe Dublaj" if ("dublaj" in rozet or "dual" in rozet) else ""

        results: list[ExtractResult] = []
        for embed_url, video_id in dict.fromkeys(_EMBED.findall(page.text)):
            # Embed sayfası `ultra_embed_auth` çerezini basar; API onsuz 403 veriyor.
            await self.httpx.get(embed_url, headers={"User-Agent": _UA, "Referer": f"{self.main_url}/"})
            kok   = embed_url.split("/embed/")[0]
            bilgi = await self.httpx.get(
                f"{kok}/api/video-bilgi/{video_id}",
                headers={"User-Agent": _UA, "Referer": embed_url, "Origin": kok},
            )
            if bilgi.status_code != 200:
                continue
            veri = bilgi.json()

            subtitles = [
                Subtitle(name=t.get("label") or "Altyazı", url=self._mutlak(kok, t["file"]))
                for t in veri.get("tracks") or []
                if t.get("file")
            ]
            if not subtitles and veri.get("subtitle"):
                subtitles = [Subtitle(name="Türkçe", url=self._mutlak(kok, veri["subtitle"]))]

            # `sources` kalite başına ayrı manifest verir; yoksa `src` master HLS.
            kaynaklar = [(s.get("label") or "", s["file"]) for s in veri.get("sources") or [] if s.get("file")]
            if not kaynaklar and (veri.get("src") or veri.get("hls_m3u8_path")):
                kaynaklar = [("", veri.get("src") or veri["hls_m3u8_path"])]

            for etiket, dosya in kaynaklar:
                kalite = f" {etiket}" if etiket else ""
                results.append(
                    ExtractResult(
                        name       = f"{self.name} | OynatLoad{kalite}{dil}",
                        url        = dosya,
                        referer    = embed_url,
                        user_agent = _UA,
                        subtitles  = subtitles,
                    )
                )
        return self.deduplicate(results)

    @staticmethod
    def _mutlak(kok: str, yol: str) -> str:
        return yol if yol.startswith("http") else f"{kok}{yol if yol.startswith('/') else '/' + yol}"
