"""Dizilla provider ported from the Kekik-cloudstream plugin."""

from __future__ import annotations

import json
import os
import re

from urllib.parse import quote, urlsplit

import httpx

from KekikStream.Core import Episode, ExtractResult, HTMLHelper, MainPageResult, PluginBase, SearchResult, SeriesInfo
from Plugins.__dizi_common import (
    absolute,
    fetch_html,
    first_attr,
    first_text,
    normalize_url,
)
from Plugins.__kekik_domain import discover_main_url

# Domain dizilla.nl → dizilla.club → dizilla.now taşındı; TR'de SNI-bloklu → WARP proxy şart.
_MAIN_URL = discover_main_url(
    "Dizilla/src/main/kotlin/com/keyiflerolsun/Dizilla.kt", "https://dizilla.now", "DIZILLA_URL"
)

# Kaynak iframe'i (pichive) Cloudflare arkasında: tarayıcı User-Agent'ı yoksa 403 döner.
_BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

# Ekim 2026 yeniden tasarımı (Next.js app router): __NEXT_DATA__/secureData ve şifreli
# arama ucu kalktı. Veri artık sayfanın RSC "flight" metninde, kaçışlı JSON olarak duruyor:
# dizi sayfasında JSON-LD `TVEpisode` adresleri, bölüm sayfasında `"sources":[...]`.
_BOLUM_ADRESI = re.compile(r'https?://[^"/]+/([^"]+?)-(\d+)-sezon-(\d+)-bolum[^"]*')
# Posterler küçük önizleme boyutunda (/0/0/100/) geliyor; dizi sayfası 256x384 kullanıyor.
_KUCUK_POSTER = "/images/0/0/100/"


def _flight(text: str) -> str:
    """RSC flight payload'undaki `\\"` kaçışlarını aç — JSON parçaları düz okunur."""
    return text.replace('\\"', '"')


def _poster(url: str | None) -> str | None:
    return url.replace(_KUCUK_POSTER, "/images/256/384/85/") if url else None


def _bolumler(text: str, base_url: str) -> list[Episode]:
    """JSON-LD `containsSeason[].episode[].url` adreslerinden sezon/bölüm listesi."""
    bulunan: dict[tuple[int, int], str] = {}
    for esle in _BOLUM_ADRESI.finditer(_flight(text)):
        sezon, bolum = int(esle.group(2)), int(esle.group(3))
        bulunan.setdefault((sezon, bolum), normalize_url(esle.group(0), base_url))
    return [
        Episode(season=sezon, episode=bolum, title=f"{sezon}. Sezon {bolum}. Bölüm", url=adres)
        for (sezon, bolum), adres in sorted(bulunan.items())
    ]


def _kaynaklar(text: str) -> list[dict]:
    """Bölüm sayfasındaki `"sources":[...]` dizisi; Türkçe dublaj önce."""
    flight = _flight(text)
    basla = flight.find('"sources":[')
    if basla < 0:
        return []
    try:
        dizi, _ = json.JSONDecoder().raw_decode(flight, basla + len('"sources":'))
    except ValueError:
        return []
    kaynaklar = [k for k in dizi if isinstance(k, dict) and k.get("src") and not k.get("trailer")]
    return sorted(kaynaklar, key=lambda k: k.get("lang") != "dub")


class Dizilla(PluginBase):
    # Dizilla SNI-bloklu → SADECE bu plugin çıkışını WARP proxy'sinden geçir.
    # NOT: PluginBase'in FallbackHTTPX'i proxy param'ını uygulamıyor (direkt bağlanıp
    # ConnectError) → super sonrası self.httpx'i proxy'li DÜZ httpx.AsyncClient ile
    # değiştiriyoruz (kanıt: dizilla.club proxy ile 200). Diğer plugin'ler dokunulmaz
    # → movie/RecTV/HDFC direkt kalır, bozulmaz.
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        warp = os.getenv("WARP_PROXY")
        if warp:
            headers = {}
            try:
                headers = dict(self.httpx.headers)
            except Exception:
                headers = {"User-Agent": "Mozilla/5.0"}
            self.httpx = httpx.AsyncClient(
                proxy=warp, follow_redirects=True, timeout=20, headers=headers,
            )

    name = "Dizilla"
    language = "tr"
    main_url = _MAIN_URL
    favicon = f"https://www.google.com/s2/favicons?domain={_MAIN_URL}&sz=64"
    description = "Dizilla — altyazılı ve Türkçe dublaj yabancı diziler."
    # "Son Bölümler" (/tum-bolumler) çıkarıldı: yeni tasarımda dizi kartı taşımıyor,
    # yalnız bölüm bağlantıları var.
    main_page = {
        f"{main_url}/turkce-dublaj-dizi-izle": "Türkçe Dublaj",
        f"{main_url}/dizi-turu/aile": "Aile",
        f"{main_url}/dizi-turu/aksiyon": "Aksiyon",
        f"{main_url}/dizi-turu/bilim-kurgu": "Bilim Kurgu",
        f"{main_url}/dizi-turu/romantik": "Romantik",
        f"{main_url}/dizi-turu/komedi": "Komedi",
    }

    def _kartlar(self, text: str) -> list[tuple[str, str, str | None]]:
        """`a.pcard` kartları — (başlık, adres, poster). Arama, ana sayfa ve tür sayfası aynı."""
        kartlar: list[tuple[str, str, str | None]] = []
        for node in HTMLHelper(text).select("a.pcard"):
            href = absolute(self.main_url, node.attrs.get("href"))
            # Altbilgi/menü bağlantıları elensin: dizi adresi her zaman /dizi/<slug>.
            if not href or "/dizi/" not in href:
                continue
            title = first_text(node, ("span.mvk-t",)) or (node.attrs.get("aria-label") or "").split(",")[0].strip()
            if not title:
                continue
            poster = _poster(first_attr(node, ("img",), "src"))
            kartlar.append((title, normalize_url(href, self.main_url), poster))
        return kartlar

    async def get_main_page(self, page: int, url: str, category: str) -> list[MainPageResult]:
        text = await fetch_html(self.httpx, normalize_url(url, self.main_url))
        return [
            MainPageResult(category=category, title=title, url=href, poster=poster)
            for title, href, poster in self._kartlar(text)
        ]

    async def search(self, query: str) -> list[SearchResult]:
        text = await fetch_html(self.httpx, f"{self.main_url}/ara?q={quote(query)}")
        return [SearchResult(title=title, url=href, poster=poster) for title, href, poster in self._kartlar(text)]

    async def load_item(self, url: str) -> SeriesInfo:
        page = normalize_url(url, self.main_url)
        text = await fetch_html(self.httpx, page)
        selector = HTMLHelper(text)
        title = re.sub(r"\s*İzle$", "", first_text(selector, ("h1",)) or "")
        poster = next(
            (img.attrs.get("src") for img in selector.select("img") if "/images/256/384/" in (img.attrs.get("src") or "")),
            None,
        )
        description = first_attr(selector, ("meta[name='description']",), "content")
        return SeriesInfo(url=page, title=title, poster=poster, description=description, episodes=_bolumler(text, self.main_url))

    async def load_links(self, url: str) -> list[ExtractResult]:
        page = normalize_url(url, self.main_url)
        text = await fetch_html(self.httpx, page)
        results: list[ExtractResult] = []
        for kaynak in _kaynaklar(text):
            iframe_url = absolute(self.main_url, kaynak["src"])
            if not iframe_url:
                continue
            etiket = " ".join(p for p in (kaynak.get("langLabel"), kaynak.get("name")) if p)
            found = await self._pichive_sources(iframe_url, etiket)
            results.extend(found)
            if not found:
                self.collect_results(results, await self.extract(iframe_url, referer=page))
        return self.deduplicate(results)

    async def _pichive_sources(self, iframe_url: str, etiket: str = "") -> list[ExtractResult]:
        """Resolve the pichive player: token → source2.php JSON → master.m3u8.

        Oynatıcı linki HTML'de yok; `openPlayer('<token>')` çağrısındaki token
        `source2.php` ucuna sorulup dönen `m.php` adresi `master.m3u8` ile
        değiştiriliyor (sitenin kendi JS'i de aynısını yapıyor).

        PluginBase'in istemcisi kullanılmaz: onun sabit `accept-encoding`/`connection`
        başlıkları Cloudflare tarafından bot parmak izi sayılıp 403 aldırıyor. Aynı
        istekler düz bir httpx istemcisiyle 200 dönüyor (hem doğrudan hem WARP ile).
        """
        parts   = urlsplit(iframe_url)
        origin  = f"{parts.scheme}://{parts.netloc}"
        proxies = [None]
        warp    = os.getenv("WARP_PROXY")
        if warp:
            proxies.append(warp)

        payload = None
        for proxy in proxies:
            try:
                async with httpx.AsyncClient(
                    proxy           = proxy,
                    timeout         = 15.0,
                    follow_redirects= True,
                    headers         = {"User-Agent": _BROWSER_UA},
                ) as client:
                    text  = (await client.get(iframe_url, headers={"Referer": f"{self.main_url}/"})).text
                    token = re.search(r"openPlayer\('([^']+)'", text)
                    if not token:
                        continue
                    # Token base64'tür ("+", "/", "="): params= ile göndermek yüzde-kodlar
                    # ve Cloudflare 403 verir. Sitenin JS'i de ham olarak ekliyor.
                    response = await client.get(
                        f"{origin}/source2.php?v={token.group(1)}",
                        headers = {
                            "Accept"          : "application/json, text/javascript, */*; q=0.01",
                            "X-Requested-With": "XMLHttpRequest",
                            "Referer"         : iframe_url,
                        },
                    )
                    payload = response.json()
                    break
            except Exception:
                continue

        if not payload:
            return []

        results: list[ExtractResult] = []
        for item in payload.get("playlist") or []:
            for source in item.get("sources") or []:
                file_url = str(source.get("file") or "")
                if not file_url:
                    continue
                results.append(
                    ExtractResult(
                        name       = f"{self.name} | {etiket or source.get('title') or 'Kaynak'}",
                        url        = file_url.replace("m.php", "master.m3u8"),
                        referer    = iframe_url,
                        user_agent = _BROWSER_UA,
                    )
                )
        return results
