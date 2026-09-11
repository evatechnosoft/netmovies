# NetMovies — FullHDFilmizlesene eklentisi
#
# Oynatma zinciri iki kat şifreli; ikisi de sayfanın kendi JS'inden okundu:
#   1. Film sayfasında `var scx = {"<kaynak>":{"tt":<b64 ad>,"sx":{"t":["<şifre>"]}}}`.
#      Şifre ROT13 + base64 → embed adresi (`https://rapidvid.net/vod/<id>`).
#   2. Embed sayfasında `av('<şifre>')`: ters çevir → base64 → "K9L" döngüsüne göre
#      karakter kaydırma (1,3,2) → base64 → akış adresi.
#
# Kart yapısı listede ve aramada aynı: `li.film` içinde `a.tt`, `span.trz` (dil
# rozeti), `span.film-yil`, `span.imdb`, poster `picture > source[data-srcset]`.

from __future__ import annotations

import base64
import json
import re

from KekikStream.Core import ExtractResult, MainPageResult, MovieInfo, PluginBase, SearchResult
from Plugins.__kekik_domain import discover_main_url

_MAIN_URL = discover_main_url(
    "FullHDFilmizlesene/src/main/kotlin/com/keyiflerolsun/FullHDFilmizlesene.kt",
    "https://www.fullhdfilmizlesene.now",
    "FULLHDFILMIZLESENE_URL",
)

_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"

_KART   = re.compile(r'<li class="film">(.*?)</li>', re.S)
_AD     = re.compile(r'<a class="tt" href="([^"]+)">([^<]*?)\s*izle\s*</a>', re.I)
_POSTER = re.compile(r'data-srcset="([^"\s]+)')
_YIL    = re.compile(r'<span class="film-yil">(\d{4})</span>')
_DIL    = re.compile(r'<span class="trz[^"]*"[^>]*title="([^"]*)"')
_SCX    = re.compile(r"var\s+scx\s*=\s*(\{.*?\});", re.S)
_AV     = re.compile(r"av\(['\"]([A-Za-z0-9+/=]{20,})['\"]\)")

# `av()` içindeki kaydırma anahtarı: "K9L" → (75%5)+1, (57%5)+1, (76%5)+1
_KAYDIRMA = [ord(harf) % 5 + 1 for harf in "K9L"]


def _b64(metin: str) -> bytes:
    return base64.b64decode(metin + "=" * (-len(metin) % 4))


class FullHDFilmizlesene(PluginBase):
    name        = "FullHDFilmizlesene"
    language    = "tr"
    main_url    = _MAIN_URL
    favicon     = f"https://www.google.com/s2/favicons?domain={_MAIN_URL}&sz=64"
    description = "FullHDFilmizlesene — dublaj/altyazı rozetli, 4K'ya kadar filmler."

    main_page = {
        f"{main_url}/yeni-filmler"                    : "Yeni Filmler",
        f"{main_url}/en-cok-izlenen-filmler"          : "En Çok İzlenen Filmler",
        f"{main_url}/filmizle/aksiyon-filmleri"       : "Aksiyon",
        f"{main_url}/filmizle/bilim-kurgu-filmleri"   : "Bilim Kurgu",
        f"{main_url}/filmizle/macera-filmleri"        : "Macera",
        f"{main_url}/filmizle/komedi-filmleri"        : "Komedi",
        f"{main_url}/filmizle/gerilim-filmleri"       : "Gerilim",
        f"{main_url}/filmizle/korku-filmleri"         : "Korku",
        f"{main_url}/filmizle/dram-filmleri"          : "Dram",
        f"{main_url}/filmizle/fantastik-filmleri"     : "Fantastik",
        f"{main_url}/filmizle/animasyon-filmleri"     : "Animasyon",
        f"{main_url}/filmizle/suc-filmleri"           : "Suç",
    }

    def _cards(self, html: str) -> list[tuple[str, str, str | None]]:
        out: list[tuple[str, str, str | None]] = []
        for govde in _KART.findall(html):
            ad = _AD.search(govde)
            if not ad:
                continue
            poster = _POSTER.search(govde)
            out.append((ad.group(2).strip(), self.fix_url(ad.group(1)), poster.group(1) if poster else None))
        return list(dict.fromkeys(out))

    async def get_main_page(self, page: int, url: str, category: str) -> list[MainPageResult]:
        target   = url if page <= 1 else f"{url.rstrip('/')}/{page}"
        response = await self.httpx.get(target, headers={"User-Agent": _UA})
        if response.status_code != 200:
            return []
        return [MainPageResult(category=category, title=t, url=u, poster=p) for t, u, p in self._cards(response.text)]

    async def search(self, query: str) -> list[SearchResult]:
        response = await self.httpx.get(f"{self.main_url}/arama/{query}", headers={"User-Agent": _UA})
        return [SearchResult(title=t, url=u, poster=p) for t, u, p in self._cards(response.text)]

    async def load_item(self, url: str) -> MovieInfo:
        response = await self.httpx.get(url, headers={"User-Agent": _UA})
        html     = response.text

        baslik = re.search(r"<title[^>]*>([^<|]+)", html)
        poster = re.search(r'<meta property="og:image" content="([^"]+)"', html)
        ozet   = re.search(r'<meta property="og:description" content="([^"]*)"', html)
        # Yıl okunmuyor: sayfadaki ilk `film-yil` benzer-filmler bloğundan geliyor
        # ve yanlış değer veriyordu (The Matrix 1 -> 2003).

        return MovieInfo(
            url         = url,
            title       = re.sub(r"\s*(izle|film|full hd|türkçe dublaj).*$", "", baslik.group(1), flags=re.I).strip() if baslik else url.rstrip("/").split("/")[-1],
            poster      = poster.group(1) if poster else None,
            description = ozet.group(1) if ozet else None,
            tags        = re.findall(r"/filmizle/([a-z0-9-]+)-filmleri", html)[:5],
            year        = None,
        )

    @staticmethod
    def _embed_adresi(sifreli: str) -> str | None:
        """`scx` girdisi: ROT13 + base64."""
        try:
            return _b64(codecs_rot13(sifreli)).decode("utf-8")
        except Exception:
            return None

    @staticmethod
    def _akis_adresi(sifreli: str) -> str | None:
        """`av()` girdisi: ters → base64 → "K9L" kaydırması → base64."""
        try:
            ara = _b64(sifreli[::-1]).decode("latin-1")
            duz = "".join(chr(ord(ch) - _KAYDIRMA[i % 3]) for i, ch in enumerate(ara))
            return _b64(duz).decode("utf-8")
        except Exception:
            return None

    async def load_links(self, url: str) -> list[ExtractResult]:
        page = await self.httpx.get(url, headers={"User-Agent": _UA})
        scx  = _SCX.search(page.text)
        if not scx:
            return []

        try:
            kaynaklar = json.loads(scx.group(1))
        except ValueError:
            return []

        # Dil rozeti sayfada: "Dublaj & Altyazı" / "Altyazı" / "Dublaj".
        rozet = (_DIL.search(page.text) or [None, ""])[1].lower() if _DIL.search(page.text) else ""
        dil   = " | Türkçe Dublaj" if "dublaj" in rozet else (" | Türkçe Altyazı" if "altyaz" in rozet else "")

        results: list[ExtractResult] = []
        for anahtar, veri in kaynaklar.items():
            for sifreli in ((veri.get("sx") or {}).get("t") or [])[:1]:
                embed = self._embed_adresi(sifreli)
                if not embed:
                    continue

                frame = await self.httpx.get(embed, headers={"User-Agent": _UA, "Referer": f"{self.main_url}/"})
                av    = _AV.search(frame.text)
                akis  = self._akis_adresi(av.group(1)) if av else None
                if not akis:
                    continue

                etiket = anahtar.capitalize()
                results.append(
                    ExtractResult(
                        name       = f"{self.name} | {etiket}{dil}",
                        url        = akis,
                        referer    = embed,
                        user_agent = _UA,
                        subtitles  = [],
                    )
                )
        return self.deduplicate(results)


def codecs_rot13(metin: str) -> str:
    """ROT13 — `codecs` içe aktarmadan, yalnız ASCII harfler kayar."""
    sonuc = []
    for ch in metin:
        if "a" <= ch <= "z":
            sonuc.append(chr((ord(ch) - 97 + 13) % 26 + 97))
        elif "A" <= ch <= "Z":
            sonuc.append(chr((ord(ch) - 65 + 13) % 26 + 65))
        else:
            sonuc.append(ch)
    return "".join(sonuc)
