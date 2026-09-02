"""Dizilla provider ported from the Kekik-cloudstream plugin."""

from __future__ import annotations

import base64
import hashlib
import json
import os
import re

from urllib.parse import urlsplit

import httpx
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

from KekikStream.Core import Episode, ExtractResult, HTMLHelper, MainPageResult, PluginBase, SearchResult, SeriesInfo
from Plugins.__dizi_common import (
    absolute,
    fetch_html,
    first_attr,
    first_text,
    get_warp_client,
    normalize_url,
    season_episode,
)
from Plugins.__kekik_domain import discover_main_url

# Domain dizilla.nl → dizilla.club → dizilla.now taşındı; TR'de SNI-bloklu → WARP proxy şart.
_MAIN_URL = discover_main_url(
    "Dizilla/src/main/kotlin/com/keyiflerolsun/Dizilla.kt", "https://dizilla.now", "DIZILLA_URL"
)

# Site Next.js'e geçince arama ucu /bg/searchcontent (cKey/cValue hidden input) yerine
# POST /api/bg/searchContent?searchterm=... oldu ve gövde AES ile şifreleniyor.
# Şema `_next/static/chunks/2067.*.js` + `_app.*.js` modül 379'dan deobfuscate edildi:
#   key = sha256("!!22xx!!90!!").digest() -> base64 -> ilk 32 karakter (utf8 bayt olarak)
#   iv  = 16 sıfır bayt, mod = AES-256-CBC, gövde base64
_SEARCH_KEY = base64.b64encode(hashlib.sha256(b"!!22xx!!90!!").digest()).decode()[:32].encode()
_SEARCH_IV = bytes(16)
# Poster'lar ampproject önekiyle ve /f/f/ (genişlik/yükseklik) yer tutucusuyla geliyor.
_AMP_PREFIX = "images-macellan-online.cdn.ampproject.org/i/s/"
# Kaynak iframe'i (pichive) Cloudflare arkasında: tarayıcı User-Agent'ı yoksa 403 döner.
_BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"


def _decrypt_search(payload: str) -> dict:
    """Decrypt the AES-256-CBC base64 body returned by /api/bg/searchContent."""
    decryptor = Cipher(algorithms.AES(_SEARCH_KEY), modes.CBC(_SEARCH_IV)).decryptor()
    plain = decryptor.update(base64.b64decode(payload)) + decryptor.finalize()
    plain = plain[: -plain[-1]]  # PKCS#7
    return json.loads(plain.decode("utf-8"))


def _search_poster(value: str | None) -> str | None:
    if not value:
        return None
    return value.replace(_AMP_PREFIX, "").replace("/f/f/", "/300/450/")


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
    main_page = {
        f"{main_url}/tum-bolumler": "Son Bölümler",
        f"{main_url}/dublaj-bolumler": "Dublaj Bölümleri",
        f"{main_url}/dizi-turu/aile": "Aile",
        f"{main_url}/dizi-turu/aksiyon": "Aksiyon",
        f"{main_url}/dizi-turu/bilim-kurgu": "Bilim Kurgu",
        f"{main_url}/dizi-turu/romantik": "Romantik",
        f"{main_url}/dizi-turu/komedi": "Komedi",
    }

    @staticmethod
    def _result(node: HTMLHelper, base_url: str, category: str) -> MainPageResult | None:
        title = first_text(node, ("h2", "a"))
        href = absolute(base_url, first_attr(node, ("a",), "href"))
        poster = absolute(base_url, first_attr(node, ("img",), "data-src")) or absolute(base_url, first_attr(node, ("img",), "src"))
        if not title or not href:
            return None
        return MainPageResult(category=category, title=title, url=normalize_url(href, base_url), poster=poster)

    async def get_main_page(self, page: int, url: str, category: str) -> list[MainPageResult]:
        text = await fetch_html(self.httpx, normalize_url(url, self.main_url))
        selector = HTMLHelper(text)
        css = "div.grid-cols-3 a" if "/dizi-turu/" in url else "div.grid a"
        results: list[MainPageResult] = []
        for node in selector.select(css):
            item = self._result(node, self.main_url, category)
            if item:
                results.append(item)
        return results

    async def _search_payload(self, query: str) -> dict | None:
        """POST the search term, retrying over WARP when the direct route is blocked."""
        url = f"{self.main_url}/api/bg/searchContent"
        headers = {
            "Accept": "application/json",
            "X-Requested-With": "XMLHttpRequest",
            "Referer": f"{self.main_url}/",
        }
        clients = [self.httpx]
        warp = get_warp_client()
        if warp is not None:
            clients.append(warp)
        for client in clients:
            try:
                response = await client.post(url, params={"searchterm": query}, headers=headers, timeout=12.0)
                body = response.json().get("response")
                if body:
                    return _decrypt_search(body)
            except Exception:
                continue
        return None

    async def search(self, query: str) -> list[SearchResult]:
        payload = await self._search_payload(query)
        if not payload:
            return []
        results: list[SearchResult] = []
        for item in payload.get("result") or []:
            if not isinstance(item, dict) or item.get("used_type") == "MovieSeries":
                continue
            title = str(item.get("object_name") or "").strip()
            slug = str(item.get("used_slug") or "").strip().lstrip("/")
            if not title or not slug:
                continue
            alternative = str(item.get("object_alternative_name") or "").strip()
            if alternative and alternative.lower() != title.lower():
                title = f"{title} ({alternative})"
            results.append(
                SearchResult(
                    title=title,
                    url=f"{self.main_url}/{slug}",
                    poster=_search_poster(item.get("object_poster_url")),
                )
            )
        return results

    async def load_item(self, url: str) -> SeriesInfo:
        text = await fetch_html(self.httpx, normalize_url(url, self.main_url))
        selector = HTMLHelper(text)
        # h1 içinde başlığın ardına butonu geliyor ve düz metne "Darknetİzle" gibi
        # yapışıyor — son "İzle" ekini at.
        title = re.sub(r"\s*İzle$", "", first_text(selector, ("div.page-top h1", "h1")) or "")
        poster = absolute(self.main_url, first_attr(selector, ("div.page-top img", "img"), "src"))
        description = first_text(selector, ("div.mv-det-p", "div.w-full div.text-base"))
        episodes: list[Episode] = []
        for season in selector.select("div.gap-2 a[href*='-sezon']"):
            season_url = absolute(self.main_url, season.attrs.get("href"))
            if not season_url:
                continue
            season_text = await fetch_html(self.httpx, normalize_url(season_url, self.main_url))
            season_selector = HTMLHelper(season_text)
            for node in season_selector.select("div.episodes div.cursor-pointer, div.dub-episodes div.cursor-pointer"):
                ep_title = first_text(node, ("a",))
                ep_url = absolute(self.main_url, first_attr(node, ("a.opacity-60", "a"), "href"))
                if not ep_title or not ep_url:
                    continue
                ep_url = normalize_url(ep_url, self.main_url)
                season_no, episode_no = season_episode(ep_title)
                episodes.append(Episode(season=season_no, episode=episode_no, title=ep_title, url=ep_url))
        return SeriesInfo(url=normalize_url(url, self.main_url), title=title, poster=poster, description=description, episodes=episodes)

    @staticmethod
    def _secure_data(text: str) -> dict | None:
        """Decrypt the `secureData` blob embedded in the page's __NEXT_DATA__ payload."""
        match = re.search(r'id="__NEXT_DATA__"[^>]*>(.*?)</script>', text, re.S)
        if not match:
            return None
        try:
            blob = json.loads(match.group(1))["props"]["pageProps"]["secureData"]
            return _decrypt_search(blob)
        except Exception:
            return None

    async def load_links(self, url: str) -> list[ExtractResult]:
        page = normalize_url(url, self.main_url)
        text = await fetch_html(self.httpx, page)

        # Oynatıcı artık sunucudan iframe olarak gelmiyor: `div#playerLsDizilla` boş
        # bırakılıp kaynaklar __NEXT_DATA__ içindeki AES'li `secureData` bloğundan
        # (arama ucuyla aynı anahtar) client-side basılıyor. HTML'de iframe aramak
        # bu yüzden hep 0 sonuç veriyordu.
        payload = self._secure_data(text) or {}
        sources = (
            payload.get("RelatedResults", {})
            .get("getEpisodeSources", {})
            .get("result")
            or []
        )

        results: list[ExtractResult] = []
        for source in sources:
            embed = re.search(r'src=["\']([^"\']+)', source.get("source_content") or "")
            if not embed:
                continue
            iframe_url = absolute(self.main_url, embed.group(1))
            if not iframe_url:
                continue
            found = await self._pichive_sources(iframe_url)
            results.extend(found)
            if not found:
                self.collect_results(results, await self.extract(iframe_url, referer=page))
        return self.deduplicate(results)

    async def _pichive_sources(self, iframe_url: str) -> list[ExtractResult]:
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
                        name       = f"{self.name} | {source.get('title') or 'Kaynak'}",
                        url        = file_url.replace("m.php", "master.m3u8"),
                        referer    = iframe_url,
                        user_agent = _BROWSER_UA,
                    )
                )
        return results
