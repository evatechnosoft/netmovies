"""DiziPal provider — dizi + film, tek oynatıcı zinciri.

Upstream .kt bayat. Kasım 2026'da site Next.js uygulamasına taşındı (eski
dizipalNNNN adresleri dizipal1824.com'a 301 atıyor): kartlar `a.tc` / `div.ec`,
arama `GET /arama-yap?s=`, oynatıcı bilgisi Next.js flight verisinde. Zincir:

    bölüm sayfası  -> "player":{sources[], enc:{ciphertext,iv,salt}, k}
      -> PBKDF2-SHA512(k, salt, 999) + AES-CBC  (anahtar artık sayfada)
      -> oynatıcı iframe -> window.NP_CONFIG.sources[].master  (HLS)

Bölüm listesi HTML yerine sayfadaki JSON-LD'den okunur: site kabuğu sık
değişiyor ama schema.org bloğu duruyor.
"""

from __future__ import annotations

import asyncio
import base64
import hashlib
import time
import html as html_lib
import json
import os
import re
import urllib.request

import httpx
from Crypto.Cipher import AES
from KekikStream.Core import Episode, ExtractResult, HTMLHelper, MainPageResult, MovieInfo, PluginBase, SearchResult, SeriesInfo, Subtitle
from Plugins.__dizi_common import _DEFAULT_UA, absolute, fetch_html, first_attr, first_text, get_warp_client, normalize_url, poster_attr


# Terk edilmiş dizipalNNNN adresleri ölmüyor: 200 dönen boş bir kabuk sayfası
# sunuyorlar. Sadece HTTP durumuna bakan bir keşif katalogu sessizce boşaltır —
# adres ancak kart işaretini gerçekten döndürüyorsa kabul edilir.
_SIGNATURE = 'class="tc"'
_DEFAULT_URL = "https://dizipal1824.com"


def _content_root(url: str) -> str | None:
    """Return the post-redirect root if `url` serves real cards, else None.

    Old numbers 301 to the current one; keeping the redirecting host turned
    POSTs into GETs and added a hop per request. Direct connections hit the ISP
    SNI block, so the WARP proxy is tried first.
    """
    req = urllib.request.Request(f"{url}/diziler", headers={"User-Agent": "Mozilla/5.0"})
    warp = os.getenv("WARP_PROXY", "http://warp:8080")
    openers = [urllib.request.build_opener(urllib.request.ProxyHandler({"http": warp, "https": warp})),
               urllib.request.build_opener()]
    for opener in openers:
        try:
            with opener.open(req, timeout=10) as resp:
                if _SIGNATURE in resp.read(400_000).decode("utf-8", "ignore"):
                    return re.sub(r"/diziler/?$", "", resp.geturl().split("?")[0]).rstrip("/")
        except Exception:
            continue
    return None


def _discover() -> str:
    manual = os.getenv("DIZIPAL_URL")
    if manual:
        return manual.rstrip("/")
    fallback = _DEFAULT_URL
    if os.getenv("AUTO_DISCOVER_DOMAINS", "0").lower() not in ("1", "true", "yes"):
        return fallback
    # Old numbers redirect to the live one; the target is checked by signature.
    for candidate in (fallback, "https://dizipal2221.com"):
        root = _content_root(candidate)
        if root and re.match(r"^https?://dizipal\d+\.com$", root):
            return root
    return fallback


_MAIN_URL = _discover()
# Flight data is backslash-escaped JSON; a bare "k" search grabs the wrong field.
_ENC_RE = re.compile(r'"enc":(\{"ciphertext":"[^"]+","iv":"[^"]+","salt":"[^"]+"\}),"k":"([^"]+)"')
_SOURCES_RE = re.compile(r'"player":\{[^{}]*?"sources":(\[[^\]]*\])')
_NP_CONFIG_RE = re.compile(r"window\.NP_CONFIG\s*=\s*(\{.*?\});\s*\n", re.S)


class DiziPal(PluginBase):
    name = "DiziPal"
    language = "tr"
    main_url = _MAIN_URL
    favicon = f"https://www.google.com/s2/favicons?domain={_MAIN_URL}&sz=64"
    description = "DiziPal — dizi ve film, tek kaynaktan geniş katalog."
    main_page = {
        f"{main_url}/yeni-eklenen-bolumler": "Son Bölümler",
        f"{main_url}/diziler": "Yeni Diziler",
        f"{main_url}/filmler": "Popüler Filmler",
    }

    def _client(self) -> httpx.AsyncClient:
        return get_warp_client() or self.httpx

    async def _html(self, url: str) -> str:
        # Bölüm sayfası da aynı sınırlayıcıya takılıyor (429 → "oynatılabilir kaynak
        # vermedi"); tüm DiziPal istekleri tek sıradan, aralıklı gider.
        return await self._sirayla(lambda: fetch_html(self._client(), url))

    async def _sirayla(self, istek):
        """DiziPal'e giden her isteği tek sıraya alır, 3 sn aralık bırakır, 429'da
        4 sn bekleyip bir kez yineler. `istek`: yanıt döndüren async fabrika."""
        if DiziPal._arama_kilidi is None:
            DiziPal._arama_kilidi = asyncio.Lock()
        async with DiziPal._arama_kilidi:
            for deneme in range(2):
                bekle = self._ARAMA_ARALIK_SN - (time.monotonic() - DiziPal._arama_son_istek)
                if bekle > 0:
                    await asyncio.sleep(bekle)
                try:
                    sonuc = await istek()
                finally:
                    DiziPal._arama_son_istek = time.monotonic()
                if deneme == 0 and getattr(sonuc, "status_code", None) == 429:
                    await asyncio.sleep(4.0)
                    continue
                return sonuc
        return sonuc

    # ------------------------------------------------------------------ listeler
    @staticmethod
    def _cards(selector: HTMLHelper, base_url: str, category: str) -> list[MainPageResult]:
        """`a.tc` is a series/film card, `div.ec` an episode card (link in inner `a.ecl`)."""
        results: list[MainPageResult] = []
        for node in selector.select("a.tc"):
            href = absolute(base_url, node.attrs.get("href"))
            title = first_text(node, ("span.tt",)) or (node.attrs.get("aria-label") or "").split(",")[0]
            if href and title:
                results.append(MainPageResult(category=category, title=title.strip(), url=href,
                                              poster=poster_attr(node, ("img",))))
        for node in selector.select("div.ec"):
            href = absolute(base_url, first_attr(node, ("a.ecl",), "href"))
            title = first_text(node, ("span.tn",))
            if not href or not title:
                continue
            ep = re.search(r"/(\d+)-sezon/(\d+)-bolum", href)
            label = f"{title.strip()} {ep.group(1)}. Sezon {ep.group(2)}. Bölüm" if ep else title.strip()
            results.append(MainPageResult(category=category, title=label, url=href, poster=poster_attr(node, ("img",))))
        return results

    async def get_main_page(self, page: int, url: str, category: str) -> list[MainPageResult]:
        target = normalize_url(url, self.main_url)
        if page and page > 1:
            target = f"{target}?page={page}"
        return self._cards(HTMLHelper(await self._html(target)), self.main_url, category)

    # Arama ucu IP başına sıkı hız sınırı koyuyor (art arda 2. istek 429):
    # zincir her çözümde fast+full × varyant kadar arıyor, çoğu 429'a düşüp
    # "DiziPal sonuç yok" oluyordu. Sonuç 10 dk hatırlanır, 429'da bir kez beklenip
    # yeniden denenir.
    _arama_onbellek: dict[str, tuple[float, list[SearchResult]]] = {}
    _ARAMA_TTL_SN = 600.0
    # Ölçüm: art arda istek 429, 4 sn aralıkla 200. İstekler tek sıraya alınır ve
    # aralarında en az 3 sn bırakılır; fast+full ile paralel gelen zincirler
    # birbirini 429'a düşürmesin.
    _arama_kilidi: asyncio.Lock | None = None
    _arama_son_istek = 0.0
    _ARAMA_ARALIK_SN = 3.0

    async def search(self, query: str) -> list[SearchResult]:
        # Next.js sürümünde arama düz sayfa: GET /arama-yap?s=<sorgu>, sonuçlar `a.tc` kartı.
        key = query.strip().lower()
        cached = self._arama_onbellek.get(key)
        if cached and time.monotonic() - cached[0] < self._ARAMA_TTL_SN:
            return list(cached[1])
        try:
            resp = await self._sirayla(lambda: self._client().get(
                f"{self.main_url}/arama-yap", params={"s": query}, headers={"Referer": f"{self.main_url}/"},
            ))
            if resp.status_code != 200:
                return []
        except Exception:
            return []
        results = [SearchResult(title=card.title, url=card.url, poster=card.poster)
                   for card in self._cards(HTMLHelper(resp.text), self.main_url, "")]
        self._arama_onbellek[key] = (time.monotonic(), list(results))
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
        # Episode pages (feed cards) carry no season list; their h1 links to the series page.
        series_link = first_attr(selector, ("h1 a[href*='/dizi/']",), "href")
        if not episodes and series_link and re.search(r"/\d+-sezon/\d+-bolum", target):
            return await self.load_item(absolute(self.main_url, series_link))
        if episodes:
            return SeriesInfo(url=url, title=title, poster=poster, description=description, episodes=episodes)
        return MovieInfo(url=url, title=title, poster=poster, description=description)

    # ------------------------------------------------------------------ oynatma
    async def load_links(self, url: str) -> list[ExtractResult]:
        target = normalize_url(url, self.main_url)
        page = _unescape_flight(await self._html(target))

        # Subtitled and dubbed versions are separate pages (?player=<hash>);
        # the active one's cipher is on this page.
        pages: list[tuple[str, str]] = [("", page)]
        for source in _player_sources(page):
            label = str(source.get("label") or "").split("·")[-1].strip()
            if source.get("on"):
                pages[0] = (label, page)
            elif source.get("href"):
                other = await self._html(absolute(self.main_url, source["href"]) or "")
                pages.append((label, _unescape_flight(other)))

        results: list[ExtractResult] = []
        for label, body in pages:
            results.extend(await self._links_from_page(body, label))
        return self.deduplicate(results)

    async def _links_from_page(self, page: str, label: str) -> list[ExtractResult]:
        enc = _ENC_RE.search(page)
        player_url = _decrypt_player_url(enc.group(1), enc.group(2)) if enc else None
        if not player_url:
            return []
        if player_url.startswith("//"):
            player_url = f"https:{player_url}"
        host = re.match(r"^(https?://[^/]+)", player_url).group(1)

        # Player host answers a Referer-less request with a Cloudflare page.
        iframe_html = await fetch_html(self._client(), player_url, headers={"Referer": f"{self.main_url}/"})
        config_match = _NP_CONFIG_RE.search(iframe_html or "")
        try:
            config = json.loads(config_match.group(1)) if config_match else {}
        except ValueError:
            return []

        subtitles = [
            Subtitle(name=str(sub.get("label") or sub.get("lang") or "Altyazı"), url=absolute(host, sub["url"]))
            for sub in config.get("subtitles") or []
            if isinstance(sub, dict) and sub.get("url")
        ]
        results: list[ExtractResult] = []
        for source in config.get("sources") or []:
            master = str(source.get("master") or "") if isinstance(source, dict) else ""
            if not master:
                continue
            name = " · ".join(part for part in (label, source.get("title")) if part) or "Kaynak"
            results.append(ExtractResult(name=f"{self.name} | {name}", url=absolute(host, master) or master,
                                         referer=f"{host}/", user_agent=_DEFAULT_UA, subtitles=subtitles))
        return results


def _unescape_flight(page: str) -> str:
    return (page or "").replace('\\"', '"')


def _player_sources(page: str) -> list[dict]:
    match = _SOURCES_RE.search(page)
    try:
        return _as_list(json.loads(match.group(1))) if match else []
    except ValueError:
        return []


def _decrypt_player_url(raw_json: str, passphrase: str) -> str | None:
    """CryptoJS equivalent: PBKDF2-SHA512(999, 32 bytes) + AES-CBC. Key: flight-data `k`."""
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
