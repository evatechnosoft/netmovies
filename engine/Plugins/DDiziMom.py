# NetMovies — DDiziMom eklentisi (ddizimom.net)
# DiziMom'un tüm adresleri 03.11.2026'da park oldu; ddizimom.net farklı altyapı
# (Laravel). Oynatıcı DiziAbi'ninkiyle aynı pilavyerplay ailesi.
# Zincir: bölüm sayfası `<template data-tbx-defer>` içinde `data-pv=<slug>` →
# `pilavyerplay.top/assets/js/s.php?s=<slug>` (Referer ŞART) → `window.__PLAYER__`.
# Token 7 gün geçerli ama her load_links'te taze embed çekilir.

from __future__ import annotations

import json
import re

from urllib.parse import quote

from KekikStream.Core import Episode, ExtractResult, HTMLHelper, MainPageResult, MovieInfo, PluginBase, SearchResult, SeriesInfo, Subtitle
from Plugins.__dizi_common import fetch_html, normalize_url
from Plugins.__kekik_domain import discover_main_url

_MAIN_URL = discover_main_url("DDiziMom/none.kt", "https://ddizimom.net", "DDIZIMOM_URL", signature="ddizimom")
_PLAYER   = "https://pilavyerplay.top"

# Numara adresten: sıra değil (bölüm listesi farklı yerden başlayabilir).
_BOLUM  = re.compile(r"/sezon-(\d+)/bolum-(\d+)")
_SEZON  = re.compile(r"\?sezon=(\d+)#bolumler")
_PV     = re.compile(r'data-pv="([^"]+)"')
_PLAYER_JSON = re.compile(r"window\.__PLAYER__\s*=\s*(\{.*?\});", re.S)
# `data-player-type="none"` → sayfada video yok.
_PV_NONE = re.compile(r'data-player-type="none"')
_TR = str.maketrans("çğıöşüÇĞİÖŞÜâîû", "cgiosucgiosuaiu")
_YOK_KELIME = re.compile(r"\W+")


def _sade(metin: str) -> str:
    return _YOK_KELIME.sub(" ", metin.translate(_TR).lower()).strip()


def alakali(sorgu: str, baslik: str) -> bool:
    """Arama açıklamada da eşleşiyor ("teşkilat" → 20 alakasız): sorgunun her kelimesi başlıkta olmalı."""
    kelimeler = _sade(sorgu).split()
    hedef = f" {_sade(baslik)} "
    return bool(kelimeler) and all(f" {k}" in hedef for k in kelimeler)


def dizi_adresi(href: str) -> str:
    """Bölüm kartı (`/dizi/x/sezon-1/bolum-4`) → dizi sayfası (`/dizi/x`); card-may-be-episode-page."""
    return re.split(r"/sezon-\d+/bolum-\d+", href.split("?")[0])[0].rstrip("/")


class DDiziMom(PluginBase):
    name        = "DDiziMom"
    language    = "tr"
    main_url    = _MAIN_URL
    favicon     = f"https://www.google.com/s2/favicons?domain={_MAIN_URL}&sz=64"
    description = "DDiziMom — yabancı/yerli diziler ve filmler, Türkçe dublaj + altyazı."

    main_page = {
        f"{main_url}/#son-bolumler"      : "Son Bölümler",
        f"{main_url}/diziler?page=SAYFA" : "Diziler",
        f"{main_url}/filmler?page=SAYFA" : "Filmler",
    }

    def _kartlar(self, html: str) -> list[tuple[str, str, str | None]]:
        """(başlık, adres, poster). Anime elenir (Dean istemiyor); bölüm kartları diziye çevrilir."""
        out: dict[str, tuple[str, str, str | None]] = {}
        for a in HTMLHelper(html).select("a.group.block"):
            href  = a.attrs.get("href") or ""
            title = a.select_text("h3")
            if not title or not re.search(r"/(dizi|film)/", href):
                continue
            adres = normalize_url(dizi_adresi(href), self.main_url)
            # Poster `src`te (lazy yok); bölüm kartında kare görüntü, dizi posteri değil — yine de kart görseli.
            out.setdefault(adres, (title.strip(), adres, a.select_attr("img", "src")))
        return list(out.values())

    @staticmethod
    def _raf(html: str, baslik: str) -> str:
        """`h2.section-title` ile sonraki h2 arasındaki bölüm."""
        i = html.find(f">{baslik}</h2>")
        if i < 0:
            return ""
        j = html.find('<h2 class="section-title"', i)
        return html[i:j if j > 0 else None]

    async def get_main_page(self, page: int, url: str, category: str) -> list[MainPageResult]:
        if "#son-bolumler" in url:
            if page > 1:
                return []
            html = self._raf(await fetch_html(self.httpx, url.split("#")[0]), "Son Eklenen Bölümler")
        else:
            html = await fetch_html(self.httpx, url.replace("SAYFA", str(page or 1)))
        return [MainPageResult(category=category, title=t, url=u, poster=p) for t, u, p in self._kartlar(html)]

    async def search(self, query: str) -> list[SearchResult]:
        html = await fetch_html(self.httpx, f"{self.main_url}/ara?q={quote(query)}")
        return [SearchResult(title=t, url=u, poster=p) for t, u, p in self._kartlar(html) if alakali(query, t)]

    async def load_item(self, url: str) -> SeriesInfo | MovieInfo:
        url    = dizi_adresi(normalize_url(url, self.main_url))
        text   = await fetch_html(self.httpx, url)
        secici = HTMLHelper(text)
        title  = (secici.select_text("h1") or url.rsplit("/", 1)[-1]).strip()
        poster = secici.select_attr("meta[property='og:image']", "content")
        ozet   = secici.select_attr("meta[name='description']", "content")

        if "/film/" in url:
            return MovieInfo(url=url, title=title, poster=poster, description=ozet)

        # Sayfa yalnız seçili (1.) sezonu listeler; diğerleri `?sezon=N`.
        sezonlar = sorted({int(n) for n in _SEZON.findall(text)}) or [1]
        episodes: dict[tuple[int, int], Episode] = {}
        for sezon in sezonlar:
            sayfa = text if sezon == sezonlar[0] else await fetch_html(self.httpx, f"{url}?sezon={sezon}")
            for a in HTMLHelper(sayfa).select("a.group"):
                href = a.attrs.get("href") or ""
                m    = _BOLUM.search(href)
                # Başka dizinin bölümü (öneri/kenar çubuğu) listeye girmesin.
                if not m or dizi_adresi(href).rsplit("/", 1)[-1] != url.rsplit("/", 1)[-1]:
                    continue
                key = (int(m.group(1)), int(m.group(2)))
                episodes.setdefault(key, Episode(season=key[0], episode=key[1], title=f"{key[1]}. Bölüm", url=normalize_url(href, self.main_url)))
        return SeriesInfo(url=url, title=title, poster=poster, description=ozet, episodes=sorted(episodes.values(), key=lambda e: (e.season, e.episode)))

    async def load_links(self, url: str) -> list[ExtractResult]:
        text = await fetch_html(self.httpx, normalize_url(url, self.main_url))
        pv   = _PV.search(text)
        if not pv or _PV_NONE.search(text):
            return []
        # Referer olmadan embed 403 verir.
        embed = await fetch_html(self.httpx, f"{_PLAYER}/assets/js/s.php?s={pv.group(1)}", headers={"Referer": f"{self.main_url}/"})
        m = _PLAYER_JSON.search(embed)
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
