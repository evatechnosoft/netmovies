"""DiziBox provider ported from the Kekik-cloudstream plugin."""

from __future__ import annotations

import base64
import hashlib
import re

from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

from KekikStream.Core import (
    Episode,
    ExtractResult,
    HTMLHelper,
    MainPageResult,
    PluginBase,
    SearchResult,
    SeriesInfo,
)

from Plugins.__dizi_common import absolute, extract_embedded_sources, fetch_html, first_attr, first_text, normalize_url, season_episode
from Plugins.__kekik_domain import discover_main_url


_MAIN_URL = discover_main_url(
    "DiziBox/src/main/kotlin/com/keyiflerolsun/DiziBox.kt",
    "https://www.dizibox.live",
    "DIZIBOX_URL",
)
_COOKIES = {"LockUser": "true", "isTrustedUser": "true", "dbxu": "1722403730363"}
_BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"


def _evp_bytes_to_key(password: bytes, salt: bytes, key_len: int = 32, iv_len: int = 16) -> tuple[bytes, bytes]:
    """OpenSSL EVP_BytesToKey (MD5, 1 tur) — CryptoJS'in parola modunun türetmesi."""
    material = b""
    block    = b""
    while len(material) < key_len + iv_len:
        block     = hashlib.md5(block + password + salt).digest()
        material += block
    return material[:key_len], material[key_len:key_len + iv_len]


def _cryptojs_decrypt(ciphertext: str, password: str) -> str | None:
    """`CryptoJS.AES.decrypt(ct, pw)` karşılığı: "Salted__" + tuz + AES-256-CBC."""
    try:
        raw = base64.b64decode(ciphertext)
        if raw[:8] != b"Salted__":
            return None
        key, iv   = _evp_bytes_to_key(password.encode(), raw[8:16])
        decryptor = Cipher(algorithms.AES(key), modes.CBC(iv)).decryptor()
        plain     = decryptor.update(raw[16:]) + decryptor.finalize()
        return plain[:-plain[-1]].decode("utf-8", "replace")
    except Exception:
        return None


class DiziBox(PluginBase):
    name = "DiziBox"
    language = "tr"
    main_url = _MAIN_URL
    favicon = f"https://www.google.com/s2/favicons?domain={_MAIN_URL}&sz=64"
    description = "DiziBox — yabancı dizi bölümleri ve alternatif oynatıcılar."
    main_page = {
        f"{main_url}/dizi-arsivi/page/SAYFA/?ulke[]=turkiye&yil=&imdb": "Yerli Diziler",
        f"{main_url}/dizi-arsivi/page/SAYFA/?tur[0]=dram&yil&imdb": "Dram",
        f"{main_url}/dizi-arsivi/page/SAYFA/?tur[0]=aksiyon&yil&imdb": "Aksiyon",
        f"{main_url}/dizi-arsivi/page/SAYFA/?tur[0]=komedi&yil&imdb": "Komedi",
        f"{main_url}/dizi-arsivi/page/SAYFA/?tur[0]=korku&yil&imdb": "Korku",
    }

    async def _get(self, url: str) -> str:
        return await fetch_html(self.httpx, normalize_url(url, self.main_url), cookies=_COOKIES)

    @staticmethod
    def _card(node: HTMLHelper, base_url: str, category: str) -> MainPageResult | None:
        title = first_text(node, ("h3 a", "h3", "a"))
        href = absolute(base_url, first_attr(node, ("h3 a", "a"), "href"))
        poster = absolute(base_url, first_attr(node, ("img",), "src"))
        if not title or not href:
            return None
        return MainPageResult(category=category, title=title, url=normalize_url(href, base_url), poster=poster)

    async def get_main_page(self, page: int, url: str, category: str) -> list[MainPageResult]:
        text = await self._get(url.replace("SAYFA", str(page or 1)))
        selector = HTMLHelper(text)
        return [item for node in selector.select("article.detailed-article") if (item := self._card(node, self.main_url, category))]

    async def search(self, query: str) -> list[SearchResult]:
        text = await self._get(f"{self.main_url}/?s={query}")
        selector = HTMLHelper(text)
        results: list[SearchResult] = []
        for node in selector.select("article.detailed-article"):
            item = self._card(node, self.main_url, "")
            if item:
                results.append(SearchResult(title=item.title, url=item.url, poster=item.poster))
        return results

    async def load_item(self, url: str) -> SeriesInfo:
        text = await self._get(url)
        selector = HTMLHelper(text)
        title = first_text(selector, ("div.tv-overview h1 a", "h1")) or ""
        poster = absolute(self.main_url, first_attr(selector, ("div.tv-overview figure img", "img"), "src"))
        episodes: list[Episode] = []
        for season_link in selector.select("div#seasons-list a"):
            season_url = season_link.attrs.get("href")
            if not season_url:
                continue
            season_text = await self._get(absolute(self.main_url, season_url) or season_url)
            season_selector = HTMLHelper(season_text)
            for node in season_selector.select("article.grid-box"):
                ep_title = first_text(node, ("div.post-title a", "a"))
                ep_url = absolute(self.main_url, first_attr(node, ("div.post-title a", "a"), "href"))
                if not ep_title or not ep_url:
                    continue
                ep_url = normalize_url(ep_url, self.main_url)
                season, episode = season_episode(ep_title)
                episodes.append(Episode(season=season, episode=episode, title=ep_title, url=ep_url))
        return SeriesInfo(url=normalize_url(url, self.main_url), title=title, poster=poster, episodes=episodes)

    async def load_links(self, url: str) -> list[ExtractResult]:
        text = await self._get(url)
        selector = HTMLHelper(text)
        pages = [url]
        pages.extend(
            absolute(self.main_url, node.attrs.get("value"))
            for node in selector.select("div.video-toolbar option[value]")
            if node.attrs.get("value")
        )
        results: list[ExtractResult] = []
        for page in pages:
            if not page:
                continue
            page_text = await self._get(page)
            page_selector = HTMLHelper(page_text)
            iframe = first_attr(page_selector, ("div#video-area iframe", "iframe"), "src")
            if not iframe:
                continue
            iframe_url = absolute(page, iframe)
            if not iframe_url:
                continue
            iframe_text = await fetch_html(self.httpx, iframe_url, headers={"Referer": page})

            # king.php yalnızca bir SARMALAYICI: gerçek oynatıcı içindeki ikinci
            # iframe'de (molystream). Eskiden bu katman atlandığı için load_links
            # hep boş dönüyordu ("Oynatılacak kaynak bulunamadı").
            nested = absolute(iframe_url, first_attr(HTMLHelper(iframe_text), ("iframe",), "src"))
            if nested and nested != iframe_url:
                results.extend(await self._molystream(nested, referer=iframe_url))
                if not results:
                    nested_text = await fetch_html(self.httpx, nested, headers={"Referer": iframe_url})
                    results.extend(extract_embedded_sources(nested_text, nested, self.name))
                    self.collect_results(results, await self.extract(nested, referer=iframe_url))

            results.extend(extract_embedded_sources(iframe_text, iframe_url, self.name))
            self.collect_results(results, await self.extract(iframe_url, referer=page))
        return self.deduplicate(results)

    async def _molystream(self, embed_url: str, referer: str) -> list[ExtractResult]:
        """MolyStream embed'ini çözer: sayfa gövdesi CryptoJS ile şifreli.

        `CryptoJS.AES.decrypt("<ct>", "<pw>")` çağrısındaki iki argüman sayfada düz
        duruyor; parola OpenSSL parola modu (MD5 KDF) ile kullanılıyor. Çözülen HTML
        içindeki jwplayer `file:` alanı doğrudan HLS master playlist'i veriyor.
        """
        text  = await fetch_html(self.httpx, embed_url, headers={"Referer": referer})
        match = re.search(r'CryptoJS\.AES\.decrypt\("([^"]+)"\s*,\s*"([^"]+)"\)', text, re.S)
        if not match:
            return []

        player = _cryptojs_decrypt(match.group(1), match.group(2))
        if not player:
            return []

        file_match = re.search(r"""file\s*:\s*['"]([^'"]+)['"]""", player)
        if not file_match:
            return []

        return [
            ExtractResult(
                name       = f"{self.name} | MolyStream",
                url        = absolute(embed_url, file_match.group(1)) or file_match.group(1),
                referer    = embed_url,
                user_agent = _BROWSER_UA,
            )
        ]
