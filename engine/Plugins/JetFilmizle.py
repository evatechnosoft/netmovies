# NetMovies — JetFilmizle eklentisi
#
# Katalog HTML kazınmıyor: sayfa Google Rich Results için schema.org `ItemList`
# basıyor (`@type: Movie` · name · url · image · datePublished · rating). Tema
# değişse de bu bozulmaz.
#
# Oynatma zinciri dört adım (hepsi doğrulandı):
#   1. Film sayfası → `<input type="hidden" name="film_id" value="19532">` ve
#      kaynak düğmeleri (`data-source-index`, `data-player-type`).
#   2. POST `/jetplayer` {film_id, source_index, player_type} → embed iframe.
#   3. `videopark.top/vip/?id=<n>` embed'i → `PUB_ID` · `PUBLISHER_ID` · `WORKER_URL`.
#   4. `WORKER_URL/api/stream?pubId=…&publisherId=…` → JSON `hlsSource.file`
#      (master manifest, Referer'lı 200).

from __future__ import annotations

import json
import re

from KekikStream.Core import ExtractResult, MainPageResult, MovieInfo, PluginBase, SearchResult
from Plugins.__kekik_domain import discover_main_url

_MAIN_URL = discover_main_url(
    "JetFilmizle/src/main/kotlin/com/keyiflerolsun/JetFilmizle.kt", "https://jetfilmizle.now", "JETFILMIZLE_URL"
)

_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"

# JSON-LD `Movie` bloğu. Şema sayfaya göre değişiyor: listede `"image":"<url>"`,
# aramada `"position"` alanı ekli ve `image` bir `ImageObject` nesnesi. Bu yüzden
# blok kabaca ayrılıp alanlar tek tek okunur — tek kalıp ikisini birden tutmuyor.
_LD_BLOK   = re.compile(r'"@type":"Movie"(.{0,1200}?)(?="@type":"Movie"|\Z)', re.S)
_LD_AD     = re.compile(r'"name":"([^"]+)"')
_LD_URL    = re.compile(r'"url":"(https?:[^"]*?/film/[^"]+?)"')
_LD_POSTER = re.compile(r'"image":(?:"([^"]+)"|\{[^}]*?"url":"([^"]+)")')
_LD_YIL    = re.compile(r'"datePublished":"(\d{4})')
_FILM_ID  = re.compile(r'<input type="hidden" name="film_id" value="(\d+)">')
_KAYNAK   = re.compile(r'data-source-index="(\d+)"\s*data-player-type="(\w+)"')
_IFRAME   = re.compile(r"<iframe src='([^']+)'")
_DEGISKEN = re.compile(r'(WORKER_URL|PUB_ID|PUBLISHER_ID)\s*=\s*[\'"]([^\'"]+)[\'"]')


class JetFilmizle(PluginBase):
    name        = "JetFilmizle"
    language    = "tr"
    main_url    = _MAIN_URL
    favicon     = f"https://www.google.com/s2/favicons?domain={_MAIN_URL}&sz=64"
    description = "JetFilmizle — Türkçe dublaj ve altyazılı filmler, tür rafları."

    main_page = {
        f"{main_url}/filmler"           : "Yeni Filmler",
        f"{main_url}/trendler"          : "Trend Filmler",
        f"{main_url}/tur/aksiyon"       : "Aksiyon",
        f"{main_url}/tur/bilim-kurgu"   : "Bilim Kurgu",
        f"{main_url}/tur/macera"        : "Macera",
        f"{main_url}/tur/komedi"        : "Komedi",
        f"{main_url}/tur/gerilim"       : "Gerilim",
        f"{main_url}/tur/korku"         : "Korku",
        f"{main_url}/tur/dram"          : "Dram",
        f"{main_url}/tur/fantastik"     : "Fantastik",
        f"{main_url}/tur/animasyon"     : "Animasyon",
        f"{main_url}/tur/suc"           : "Suç",
    }

    def _films(self, html: str) -> list[tuple[str, str, str | None, str | None]]:
        out: list[tuple[str, str, str | None, str | None]] = []
        for blok in _LD_BLOK.finditer(html):
            govde     = blok.group(1)
            ad, adres = _LD_AD.search(govde), _LD_URL.search(govde)
            if not ad or not adres:
                continue
            poster = _LD_POSTER.search(govde)
            yil    = _LD_YIL.search(govde)
            out.append((
                self._cozumle(ad.group(1)),
                adres.group(1).replace("\\/", "/"),
                (poster.group(1) or poster.group(2)).replace("\\/", "/") if poster else None,
                yil.group(1) if yil else None,
            ))
        return list(dict.fromkeys(out))

    @staticmethod
    def _cozumle(metin: str) -> str:
        """JSON-LD kaçışlarını (`\\u00fc`, `\\/`) çözer."""
        try:
            return json.loads(f'"{metin}"')
        except ValueError:
            return metin.replace("\\/", "/")

    async def get_main_page(self, page: int, url: str, category: str) -> list[MainPageResult]:
        # Sayfalama `?sayfa=N` (yol eki `/2` 404 veriyor).
        target   = url if page <= 1 else f"{url}?sayfa={page}"
        response = await self.httpx.get(target, headers={"User-Agent": _UA})
        if response.status_code != 200:
            return []
        return [
            MainPageResult(category=category, title=ad, url=u, poster=p)
            for ad, u, p, _ in self._films(response.text)
        ]

    async def search(self, query: str) -> list[SearchResult]:
        response = await self.httpx.get(f"{self.main_url}/arama", params={"q": query}, headers={"User-Agent": _UA})
        return [SearchResult(title=ad, url=u, poster=p) for ad, u, p, _ in self._films(response.text)]

    async def load_item(self, url: str) -> MovieInfo:
        response = await self.httpx.get(url, headers={"User-Agent": _UA})
        html     = response.text

        kendi = next((f for f in self._films(html) if f[1].rstrip("/") == url.rstrip("/")), None)
        # og:title yok; sayfa adı <title>'da: "Bugün Güzel izle - JetFilmizle"
        baslik = re.search(r"<title[^>]*>([^<|]+)", html)
        poster = re.search(r'<meta property="og:image" content="([^"]+)"', html)
        ozet   = re.search(r'<meta property="og:description" content="([^"]*)"', html)

        return MovieInfo(
            url         = url,
            title       = (kendi[0] if kendi else None)
                          or (re.sub(r"\s*(izle|filmi|full hd|türkçe dublaj).*$", "", baslik.group(1), flags=re.I).strip() if baslik else None)
                          or url.rstrip("/").split("/")[-1],
            poster      = (kendi[2] if kendi else None) or (poster.group(1) if poster else None),
            description = ozet.group(1) if ozet else None,
            tags        = re.findall(r'/tur/([a-z0-9-]+)', html)[:5],
            year        = kendi[3] if kendi else None,
        )

    async def _embed_cozumle(self, embed_url: str) -> str | None:
        """`videopark.top/vip/?id=…` → master HLS. Akış sayfada değil, worker API'sinde."""
        frame = await self.httpx.get(embed_url, headers={"User-Agent": _UA, "Referer": f"{self.main_url}/"})
        degerler = dict(_DEGISKEN.findall(frame.text))
        worker, pub = degerler.get("WORKER_URL"), degerler.get("PUB_ID")
        if not worker or not pub:
            return None

        veri = await self.httpx.get(
            f"{worker}/api/stream",
            params  = {"pubId": pub, "title": pub, "publisherId": degerler.get("PUBLISHER_ID", "")},
            headers = {"User-Agent": _UA, "Referer": "https://videopark.top/"},
        )
        if veri.status_code != 200:
            return None
        return ((veri.json().get("hlsSource") or {}).get("file")) or None

    async def load_links(self, url: str) -> list[ExtractResult]:
        page    = await self.httpx.get(url, headers={"User-Agent": _UA})
        film_id = _FILM_ID.search(page.text)
        if not film_id:
            return []

        results: list[ExtractResult] = []
        for indeks, tip in list(dict.fromkeys(_KAYNAK.findall(page.text)))[:4]:
            oynatici = await self.httpx.post(
                f"{self.main_url}/jetplayer",
                data    = {"film_id": film_id.group(1), "source_index": indeks, "player_type": tip},
                headers = {"User-Agent": _UA, "Referer": url, "X-Requested-With": "XMLHttpRequest"},
            )
            iframe = _IFRAME.search(oynatici.text)
            if not iframe:
                continue

            akis = await self._embed_cozumle(iframe.group(1))
            if not akis:
                continue

            # `player_type` sitenin dil rozeti: "dublaj" / "altyazi" / "yerli".
            dil = " | Türkçe Dublaj" if tip in ("dublaj", "yerli") else (" | Türkçe Altyazı" if "altyaz" in tip else "")
            results.append(
                ExtractResult(
                    name       = f"{self.name} | VideoPark{dil}",
                    url        = akis,
                    referer    = "https://videopark.top/",
                    user_agent = _UA,
                    subtitles  = [],
                )
            )
        return self.deduplicate(results)
