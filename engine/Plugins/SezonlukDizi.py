"""SezonlukDizi provider ported from the Kekik-cloudstream plugin.

Adds a second/third source for series that already exist on DiziBox, DiziYou or
DiziMom: the same episode is often dead on one site and alive on another, and
resolve_sources walks ALTERNATIVE_ORDER until one of them yields a link.

Two site quirks drive the code below:
  * pages are served as windows-1254 with no charset header -> explicit decode,
  * the site is unreachable from a Turkish ISP -> every request goes over WARP.
"""

from __future__ import annotations

import asyncio
import json
import os
import re
from urllib.parse import quote_plus

import httpx
from KekikStream.Core import Episode, ExtractResult, HTMLHelper, MainPageResult, PluginBase, SearchResult, SeriesInfo
from Plugins.__dizi_common import (
    absolute,
    decode_body,
    extract_embedded_sources,
    fetch_html,
    first_attr,
    first_text,
    get_warp_client,
    normalize_url,
)
from Plugins.__kekik_domain import discover_main_url

# Domain zinciri: sezonlukdizi6.com -> sezonlukdizi.cc. Upstream .kt hala eski
# .com'u gosteriyor ve o adres yalniz 301 donuyor; kesif eski adresi bulursa
# dogrulanmis son adrese cekilir (DiziMom'daki ayni tuzak).
_DISCOVERED_URL = discover_main_url(
    "SezonlukDizi/src/main/kotlin/com/keyiflerolsun/SezonlukDizi.kt", "https://sezonlukdizi.cc", "SEZONLUKDIZI_URL"
)
_MAIN_URL = os.getenv("SEZONLUKDIZI_URL") or (
    "https://sezonlukdizi.cc" if re.search(r"sezonlukdizi\d*\.com", _DISCOVERED_URL) else _DISCOVERED_URL
)

_ENCODING = "windows-1254"
_DILLER = (("0", "Dublaj"), ("1", "Altyazı"))
_SOURCE_TIMEOUT = 12.0


def _first_int(value: str | None) -> int | None:
    match = re.search(r"\d+", value or "")
    return int(match.group()) if match else None


def _json_sources(payload: str) -> list[dict]:
    """{"status":"success","data":[{"id":..,"baslik":".."}]} -> data listesi."""
    try:
        parsed = json.loads(payload)
    except Exception:
        return []
    if not isinstance(parsed, dict) or parsed.get("status") != "success":
        return []
    return [item for item in (parsed.get("data") or []) if item.get("id")]


class SezonlukDizi(PluginBase):
    name = "SezonlukDizi"
    language = "tr"
    main_url = _MAIN_URL
    favicon = f"https://www.google.com/s2/favicons?domain={_MAIN_URL}&sz=64"
    description = "SezonlukDizi - yerli ve yabanci diziler, dublaj + altyazi secenekli."
    # Asya/anime kategorileri bilerek disarida: bu kurulumda istenmiyor.
    main_page = {
        f"{main_url}/diziler.asp?siralama_tipi=id&s=": "Son Eklenen Diziler",
        f"{main_url}/diziler.asp?siralama_tipi=id&kat=2&s=": "Yerli Diziler",
        f"{main_url}/diziler.asp?siralama_tipi=id&kat=1&s=": "Yabancı Diziler",
        f"{main_url}/diziler.asp?siralama_tipi=id&tur=mini&s=": "Mini Diziler",
    }

    def _client(self) -> httpx.AsyncClient:
        """Site bu ulkeden dogrudan acilmiyor; WARP yoksa yine de dene."""
        return get_warp_client() or self.httpx

    async def _html(self, url: str) -> str:
        return await fetch_html(self._client(), url, encoding=_ENCODING)

    async def _post(self, url: str, data: dict) -> str:
        resp = await self._client().post(
            url,
            data=data,
            headers={"X-Requested-With": "XMLHttpRequest", "Referer": f"{self.main_url}/"},
        )
        return decode_body(resp, _ENCODING)

    def _result(self, node, category: str) -> MainPageResult | None:
        title = first_text(node, ("div.description",))
        href = absolute(self.main_url, node.attrs.get("href"))
        poster = absolute(self.main_url, first_attr(node, ("img",), "data-src"))
        if not title or not href:
            return None
        return MainPageResult(category=category, title=title.split(" izle")[0].strip(), url=href, poster=poster)

    async def get_main_page(self, page: int, url: str, category: str) -> list[MainPageResult]:
        target = normalize_url(url, self.main_url) + str(page or 1)
        selector = HTMLHelper(await self._html(target))
        results = [self._result(node, category) for node in selector.select("div.afis a")]
        return [item for item in results if item]

    async def search(self, query: str) -> list[SearchResult]:
        # Iki tuzak: yalniz `adi=` ile sorulunca site WAF'i 403 veriyor (tam
        # parametre seti gerekiyor) ve sorgu windows-1254 ile kodlanmali —
        # utf-8'de "Sicak Kafa" katalogda dururken aramada hic cikmiyordu.
        aranan = quote_plus(query, encoding=_ENCODING, errors="ignore")
        target = f"{self.main_url}/diziler.asp?siralama_tipi=id&adi={aranan}&s=1"
        selector = HTMLHelper(await self._html(target))
        results: list[SearchResult] = []
        for node in selector.select("div.afis a"):
            item = self._result(node, "")
            if item:
                results.append(SearchResult(title=item.title, url=item.url, poster=item.poster))
        return results

    async def load_item(self, url: str) -> SeriesInfo:
        target = normalize_url(url, self.main_url)
        selector = HTMLHelper(await self._html(target))
        title = (first_text(selector, ("div.header", "h1")) or "").split(" izle")[0].strip()
        poster = absolute(self.main_url, first_attr(selector, ("div.image img", "img"), "data-src"))
        description = first_text(selector, ("span#tartismayorum-konu",))

        # Bolumler ayri sayfada: /bolumler/<slug>
        slug = target.rstrip("/").split("/")[-1]
        episodes_html = await self._html(f"{self.main_url}/bolumler/{slug}")
        episodes: list[Episode] = []
        for row in HTMLHelper(episodes_html).select("table.unstackable tbody tr"):
            ep_url = absolute(self.main_url, first_attr(row, ("td:nth-of-type(4) a",), "href"))
            if not ep_url:
                continue
            ep_title = first_text(row, ("td:nth-of-type(4) a",))
            episodes.append(
                Episode(
                    season=_first_int(first_text(row, ("td:nth-of-type(2)",))) or 1,
                    episode=_first_int(first_text(row, ("td:nth-of-type(3)",))),
                    title=ep_title,
                    url=ep_url,
                )
            )
        return SeriesInfo(url=url, title=title, poster=poster, description=description, episodes=episodes)

    async def _asp_versions(self) -> tuple[str, str]:
        """Ajax uc adlarindaki surum eki (dataAlternatif22.asp) site JS'inden gelir."""
        try:
            js = await self._html(f"{self.main_url}/js/site.min.js")
        except Exception:
            return "", ""
        alternatif = re.search(r"dataAlternatif(.*?)\.asp", js)
        embed = re.search(r"dataEmbed(.*?)\.asp", js)
        return (alternatif.group(1) if alternatif else ""), (embed.group(1) if embed else "")

    async def load_links(self, url: str) -> list[ExtractResult]:
        target = normalize_url(url, self.main_url)
        selector = HTMLHelper(await self._html(target))
        bid = first_attr(selector, ("div#dilsec",), "data-id")
        if not bid:
            return []

        alternatif, embed = await self._asp_versions()

        # dil=0 dublaj, dil=1 altyazi - ikisi de ayri oynatici listesi doner.
        listeler = await asyncio.gather(
            *(
                self._post(f"{self.main_url}/ajax/dataAlternatif{alternatif}.asp", {"bid": bid, "dil": dil})
                for dil, _ in _DILLER
            ),
            return_exceptions=True,
        )

        # Her dilde 6 oynatici var: 12 embed+iframe istegi ardisik yapilinca
        # resolve_sources'in 25 sn'lik alternatif butcesi dolup kaynak hic
        # kullanilamiyordu. Hepsi paralel gider.
        isler = [
            self._source_links(f"{self.main_url}/ajax/dataEmbed{embed}.asp", source, etiket, target)
            for (_, etiket), payload in zip(_DILLER, listeler)
            if isinstance(payload, str)
            for source in _json_sources(payload)
        ]
        results: list[ExtractResult] = []
        # resolve_sources alternatif basina 25 sn veriyor; yavas bir oynatici
        # tum kaynagi bu butcenin disina itmesin.
        gecikmeli = [asyncio.wait_for(is_, timeout=_SOURCE_TIMEOUT) for is_ in isler]
        for batch in await asyncio.gather(*gecikmeli, return_exceptions=True):
            if isinstance(batch, list):
                results.extend(batch)
        return self.deduplicate(results)

    async def _source_links(self, embed_url: str, source: dict, etiket: str, referer: str) -> list[ExtractResult]:
        """Tek bir oynaticinin iframe'ini cozer; hata veren oynatici digerlerini dusurmez."""
        try:
            embed_html = await self._post(embed_url, {"id": source["id"]})
        except Exception:
            return []
        iframe = first_attr(HTMLHelper(embed_html), ("iframe",), "src")
        iframe_url = absolute(self.main_url, iframe)
        # reCAPTCHA arkasindaki oynatici (Pixel) tarayicisiz cozulmuyor.
        if not iframe_url or "reCAPTCHA" in iframe_url:
            return []
        try:
            iframe_html = await fetch_html(self._client(), iframe_url)
        except Exception:
            return []
        found = extract_embedded_sources(iframe_html, iframe_url, self.name)
        for item in found:
            item.name = f"{self.name} | {etiket} · {source.get('baslik') or 'Kaynak'}"
        try:
            self.collect_results(found, await self.extract(iframe_url, referer=referer))
        except Exception:
            pass
        return found
