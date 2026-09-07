"""DiziPal provider — dizi + film, tek oynatıcı zinciri.

Upstream .kt bayat: site v57 ile tamamen yeniden yazıldı (dp-card/dp-episode
sınıfları, /yeni-eklenen-bolumler yolu) ve oynatıcı adresi artık sayfaya AES ile
şifreli gömülüyor. Zincir:

    bölüm sayfası  -> [data-rm-k] {ciphertext,iv,salt}
      -> PBKDF2-SHA512(passphrase, salt, 999) + AES-CBC  (passphrase pageload.js'te)
      -> oynatıcı iframe -> openPlayer('<blob>')
      -> source2.php?v=<blob> -> playlist[].sources[].file
      -> m.php yerine master.m3u8

Bölüm listesi HTML yerine sayfadaki JSON-LD'den okunur: site kabuğu sık
değişiyor ama schema.org bloğu duruyor.
"""

from __future__ import annotations

import base64
import hashlib
import html as html_lib
import json
import os
import re
import urllib.request

import httpx
from Crypto.Cipher import AES
from KekikStream.Core import Episode, ExtractResult, HTMLHelper, MainPageResult, MovieInfo, PluginBase, SearchResult, SeriesInfo
from Plugins.__dizi_common import absolute, fetch_html, first_attr, get_warp_client, normalize_url


# Terk edilmiş dizipalNNNN adresleri ölmüyor: 200 dönen boş bir kabuk sayfası
# sunuyorlar. Sadece HTTP durumuna bakan bir keşif katalogu sessizce boşaltır —
# adres ancak kart işaretini gerçekten döndürüyorsa kabul edilir.
_SIGNATURE = "dp-card"


def _serves_content(url: str) -> bool:
    try:
        req = urllib.request.Request(f"{url}/diziler", headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=8) as resp:
            return _SIGNATURE in resp.read(200_000).decode("utf-8", "ignore")
    except Exception:
        return False


def _discover() -> str:
    manual = os.getenv("DIZIPAL_URL")
    if manual:
        return manual.rstrip("/")
    fallback = "https://dizipal2220.com"
    if os.getenv("AUTO_DISCOVER_DOMAINS", "0").lower() not in ("1", "true", "yes"):
        return fallback
    if _serves_content(fallback):
        return fallback
    # Numara ilerledikçe eski adresler yenisine 301 atıyor; hedefi imzayla doğrula.
    try:
        req = urllib.request.Request("https://dizipal2206.com", headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=8) as resp:
            hedef = resp.geturl().rstrip("/")
        if re.match(r"^https?://dizipal\d+\.com$", hedef) and _serves_content(hedef):
            return hedef
    except Exception:
        pass
    return fallback


_MAIN_URL = _discover()
_PLAYER_HOST_RE = re.compile(r"openPlayer\('([^']+)'")


class DiziPal(PluginBase):
    name = "DiziPal"
    language = "tr"
    main_url = _MAIN_URL
    favicon = f"https://www.google.com/s2/favicons?domain={_MAIN_URL}&sz=64"
    description = "DiziPal — dizi ve film, tek kaynaktan geniş katalog."
    main_page = {
        f"{main_url}/yeni-eklenen-bolumler": "Son Bölümler",
        f"{main_url}/diziler": "Yeni Diziler",
        f"{main_url}/filmler": "Yeni Filmler",
    }

    _passphrase: str | None = None

    def _client(self) -> httpx.AsyncClient:
        return get_warp_client() or self.httpx

    async def _html(self, url: str) -> str:
        return await fetch_html(self._client(), url)

    # ------------------------------------------------------------------ listeler
    @staticmethod
    def _cards(selector: HTMLHelper, base_url: str, category: str) -> list[MainPageResult]:
        results: list[MainPageResult] = []
        for node in selector.select("a.dp-poster, a.dp-episode"):
            href = absolute(base_url, node.attrs.get("href"))
            title = first_attr(node, ("img",), "alt")
            poster = first_attr(node, ("img",), "data-src")
            if not href or not title:
                continue
            results.append(MainPageResult(category=category, title=title.strip(), url=href, poster=poster))
        return results

    async def get_main_page(self, page: int, url: str, category: str) -> list[MainPageResult]:
        target = normalize_url(url, self.main_url)
        if page and page > 1:
            # Yalnız /diziler sayfalanabiliyor; digerleri /2 icin 404 veriyor.
            if not target.endswith("/diziler"):
                return []
            target = f"{target}/{page}"
        return self._cards(HTMLHelper(await self._html(target)), self.main_url, category)

    async def search(self, query: str) -> list[SearchResult]:
        try:
            resp = await self._client().post(
                f"{self.main_url}/api/search-autocomplete",
                data={"query": query},
                headers={"X-Requested-With": "XMLHttpRequest", "Referer": f"{self.main_url}/"},
            )
            payload = resp.json()
        except Exception:
            return []
        results: list[SearchResult] = []
        for item in (payload or {}).values() if isinstance(payload, dict) else []:
            if not isinstance(item, dict) or not item.get("url"):
                continue
            results.append(
                SearchResult(
                    title=item.get("title") or "",
                    url=absolute(self.main_url, item["url"]) or "",
                    poster=item.get("poster"),
                )
            )
        return results

    # ------------------------------------------------------------------ detay
    async def load_item(self, url: str):
        target = normalize_url(url, self.main_url)
        page = await self._html(target)
        selector = HTMLHelper(page)
        title = re.sub(r"\s+İzle$", "", html_lib.unescape((selector.select_text("h1") or "").strip()))
        poster = first_attr(selector, ("meta[property='og:image']",), "content")
        description = selector.select_text("div.summary p") or selector.select_text("meta[name='description']")

        episodes = _episodes_from_jsonld(page, self.main_url)
        if episodes:
            return SeriesInfo(url=url, title=title, poster=poster, description=description, episodes=episodes)
        return MovieInfo(url=url, title=title, poster=poster, description=description)

    # ------------------------------------------------------------------ oynatma
    async def _get_passphrase(self) -> str | None:
        """Çözme parolası site JS'inde açık duruyor; sürüm değişirse yeniden okunur."""
        if self._passphrase:
            return self._passphrase
        try:
            js = await self._html(f"{self.main_url}/assets/js-dizipal/pageload.js")
        except Exception:
            return None
        match = re.search(r"oyunculistdc\('([^']+)'", js)
        self._passphrase = match.group(1) if match else None
        return self._passphrase

    async def load_links(self, url: str) -> list[ExtractResult]:
        target = normalize_url(url, self.main_url)
        page = await self._html(target)

        blob = re.search(r'data-rm-k="true">([^<]+)<', page)
        passphrase = await self._get_passphrase()
        if not blob or not passphrase:
            return []
        player_url = _decrypt_player_url(html_lib.unescape(blob.group(1)), passphrase)
        if not player_url:
            return []
        if player_url.startswith("//"):
            player_url = f"https:{player_url}"

        # Oynatıcı host'u Referer'sız isteği Cloudflare sayfasıyla karşılıyor.
        iframe_html = await fetch_html(self._client(), player_url, headers={"Referer": f"{self.main_url}/"})
        playlist_blob = _PLAYER_HOST_RE.search(iframe_html)
        if not playlist_blob:
            return []

        host = re.match(r"^(https?://[^/]+)", player_url).group(1)
        try:
            resp = await self._client().get(
                f"{host}/source2.php", params={"v": playlist_blob.group(1)}, headers={"Referer": f"{host}/"}
            )
            payload = resp.json()
        except Exception:
            return []

        results: list[ExtractResult] = []
        for entry in payload.get("playlist") or []:
            for source in entry.get("sources") or []:
                file_url = str(source.get("file") or "")
                if not file_url:
                    continue
                # Oynatici m.php'yi master.m3u8 ile degistiriyor; ham m.php oynamaz.
                results.append(
                    ExtractResult(
                        name=f"{self.name} | {source.get('title') or 'Kaynak'}",
                        url=file_url.replace("m.php", "master.m3u8"),
                        referer=f"{host}/",
                    )
                )
        return self.deduplicate(results)


def _decrypt_player_url(raw_json: str, passphrase: str) -> str | None:
    """CryptoJS eşleniği: PBKDF2-SHA512(999, 32 bayt) + AES-CBC."""
    try:
        obj = json.loads(raw_json)
        key = hashlib.pbkdf2_hmac("sha512", passphrase.encode(), bytes.fromhex(obj["salt"]), 999, 32)
        plain = AES.new(key, AES.MODE_CBC, bytes.fromhex(obj["iv"])).decrypt(base64.b64decode(obj["ciphertext"]))
        return plain[: -plain[-1]].decode("utf-8", "ignore").strip() or None
    except Exception:
        return None


def _episodes_from_jsonld(page: str, base_url: str) -> list[Episode]:
    """Bölümleri schema.org bloğundan okur — site kabuğu değişse de duruyor."""
    episodes: list[Episode] = []
    seen: set[str] = set()
    for block in re.findall(r'<script[^>]*type="application/ld\+json"[^>]*>(.*?)</script>', page, re.S):
        try:
            data = json.loads(block)
        except Exception:
            continue
        for season in _as_list(_dig(data, "containsSeason")):
            season_no = _first_int(str(season.get("url", ""))) or 1
            for episode in _as_list(season.get("episode")):
                ep_url = str(episode.get("url") or "")
                if not ep_url or ep_url in seen:
                    continue
                seen.add(ep_url)
                episodes.append(
                    Episode(
                        season=season_no,
                        episode=episode.get("episodeNumber"),
                        title=episode.get("name"),
                        url=normalize_url(ep_url, base_url),
                    )
                )
    return episodes


def _dig(data, key):
    if isinstance(data, dict):
        if key in data:
            return data[key]
        for value in data.values():
            found = _dig(value, key)
            if found is not None:
                return found
    elif isinstance(data, list):
        for item in data:
            found = _dig(item, key)
            if found is not None:
                return found
    return None


def _as_list(value) -> list[dict]:
    if isinstance(value, dict):
        return [value]
    return [item for item in (value or []) if isinstance(item, dict)]


def _first_int(value: str) -> int | None:
    match = re.search(r"(\d+)-sezon", value) or re.search(r"\d+", value)
    return int(match.group(1) if match.lastindex else match.group()) if match else None
