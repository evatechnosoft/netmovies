"""DDiziMom contract test: offline fixtures trimmed from real ddizimom.net HTML (03.11.2026)."""

import json
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from fake_http import FakeClient
from Plugins.DDiziMom import DDiziMom, alakali, dizi_adresi

_BASE = "https://ddizimom.net"
_CARD = '<a href="{h}" class="group block"><div><img src="{p}" alt="x" loading="eager"></div><h3 class="truncate">{t}</h3></a>'
_HOME = (
    '<h2 class="section-title">Son Eklenen Diziler</h2>'
    + _CARD.format(h=f"{_BASE}/dizi/gibi", p="https://x/gibi.webp", t="Gibi")
    + '<h2 class="section-title">Son Eklenen Bölümler</h2>'
    # episode cards: the same series twice + a series page link; must collapse to the series URL
    + _CARD.format(h=f"{_BASE}/dizi/sevdam-karadeniz/sezon-1/bolum-4", p="https://x/s4.webp", t="Sevdam Karadeniz")
    + _CARD.format(h=f"{_BASE}/dizi/sevdam-karadeniz/sezon-1/bolum-3", p="https://x/s3.webp", t="Sevdam Karadeniz")
    + '<h2 class="section-title">Son Eklenen Filmler</h2>'
    + _CARD.format(h=f"{_BASE}/film/davet", p="https://x/davet.webp", t="Davet")
)
_SEARCH = (
    _CARD.format(h=f"{_BASE}/film/breaking-the-ice", p="https://x/i.webp", t="Breaking the ICE")
    + _CARD.format(h=f"{_BASE}/dizi/breaking-bad", p="https://x/bb.webp", t="Breaking Bad")
    + _CARD.format(h=f"{_BASE}/dizi/duzenbaz-pete", p="https://x/p.webp", t="Düzenbaz Pete")  # matched on description only
)
_BB = (
    '<meta property="og:image" content="https://x/bb-back.webp"><meta name="description" content="Özet">'
    '<h1 class="font-display">Breaking Bad</h1>'
    '<a href="https://ddizimom.net/dizi/breaking-bad?sezon=1#bolumler">1</a>'
    '<a href="https://ddizimom.net/dizi/breaking-bad?sezon=2#bolumler">2</a>'
    '<a href="https://ddizimom.net/dizi/breaking-bad/sezon-1/bolum-1" class="btn-accent">Oynat</a>'
    '<a href="https://ddizimom.net/dizi/breaking-bad/sezon-1/bolum-1" class="group">1</a>'
    '<a href="https://ddizimom.net/dizi/breaking-bad/sezon-1/bolum-2" class="group">2</a>'
    # sidebar: another series' episode must not leak into the list
    '<a href="https://ddizimom.net/dizi/baska-dizi/sezon-1/bolum-9" class="group">9</a>'
)
_BB_S2 = '<a href="https://ddizimom.net/dizi/breaking-bad/sezon-2/bolum-1" class="group">1</a>'
_EP = '<template data-tbx-defer><div data-pv="yEILM0ysEtqZE5fmdNHeeg" class="x"></div></template>'
_PLAYER = "window.__PLAYER__ = " + json.dumps({
    "stream": "https://pilavyerplay.top/api/stream.php?v=1&token=t",
    "subs": [
        {"lang": "en", "label": "İngilizce Altyazı", "src": "https://p/en.vtt"},
        {"lang": "tr", "label": "Türkçe Altyazı", "src": "https://p/tr.vtt"},
    ],
}) + ";"


def _plugin(routes):
    p = DDiziMom.__new__(DDiziMom)
    p.main_url = _BASE
    p.httpx = FakeClient(routes)
    return p


class DDiziMomContract(unittest.IsolatedAsyncioTestCase):
    async def test_son_bolumler_rafi_diziye_cevrilir(self):
        r = await _plugin([("ddizimom.net/", _HOME)]).get_main_page(1, f"{_BASE}/#son-bolumler", "Son Bölümler")
        self.assertEqual([(x.title, x.url) for x in r], [("Sevdam Karadeniz", f"{_BASE}/dizi/sevdam-karadeniz")])

    async def test_diziler_sayfasi_kartlari(self):
        r = await _plugin([("/diziler?page=2", _HOME)]).get_main_page(2, f"{_BASE}/diziler?page=SAYFA", "Diziler")
        self.assertEqual(r[0].poster, "https://x/gibi.webp")

    async def test_arama_alaka_suzgeci(self):
        r = await _plugin([("/ara?q=breaking%20bad", _SEARCH)]).search("breaking bad")
        self.assertEqual([x.title for x in r], ["Breaking Bad"])

    async def test_load_item_sezon_ve_bolum(self):
        info = await _plugin([("?sezon=2", _BB_S2), ("/dizi/breaking-bad", _BB)]).load_item(f"{_BASE}/dizi/breaking-bad")
        self.assertEqual((info.title, info.poster), ("Breaking Bad", "https://x/bb-back.webp"))
        self.assertEqual([(e.season, e.episode) for e in info.episodes], [(1, 1), (1, 2), (2, 1)])

    async def test_bolum_adresinden_load_item_diziye_gecer(self):
        info = await _plugin([("/dizi/breaking-bad", _BB)]).load_item(f"{_BASE}/dizi/breaking-bad/sezon-1/bolum-2")
        self.assertEqual(info.url, f"{_BASE}/dizi/breaking-bad")

    async def test_load_links_referer_ve_altyazi(self):
        seen = {}
        p = _plugin([("s.php?s=yEILM0ysEtqZE5fmdNHeeg", _PLAYER), ("/bolum-1", _EP)])
        orijinal = p.httpx.get

        async def get(url, **kw):
            seen[url] = kw.get("headers") or {}
            return await orijinal(url, **kw)

        p.httpx.get = get
        r = await p.load_links(f"{_BASE}/dizi/breaking-bad/sezon-1/bolum-1")
        self.assertEqual(seen["https://pilavyerplay.top/assets/js/s.php?s=yEILM0ysEtqZE5fmdNHeeg"]["Referer"], f"{_BASE}/")
        self.assertEqual(r[0].url, "https://pilavyerplay.top/api/stream.php?v=1&token=t")
        self.assertEqual([s.name for s in r[0].subtitles], ["Türkçe Altyazı", "İngilizce Altyazı"])

    async def test_video_yoksa_bos(self):
        html = '<div data-player data-player-type="none"></div><template data-tbx-defer><div data-pv="abc"></div></template>'
        self.assertEqual(await _plugin([("/bolum-1", html)]).load_links(f"{_BASE}/dizi/x/sezon-1/bolum-1"), [])


class DDiziMomYardimcilar(unittest.TestCase):
    def test_alakali_turkce_ve_kelime_basi(self):
        self.assertTrue(alakali("teskilat", "Teşkilat"))
        self.assertFalse(alakali("teşkilat", "Düzenbaz Pete"))
        self.assertTrue(alakali("breaking", "Breaking Bad"))

    def test_kaynak_adi_gercek_ses_dilinden(self):
        from Plugins.__dizi_common import pilavyer_kaynak_adi as ad
        # Dexter S2B10 (12.11.2026): tek ses, İngilizce; dublaj sayılmamalı.
        self.assertEqual(ad("DDiziMom", [{"name": "a0", "lang": "en", "label": "İngilizce Dublaj"}]), "DDiziMom | Orijinal Dil")
        self.assertEqual(ad("DDiziMom", [{"lang": "tr"}]), "DDiziMom | Türkçe Dublaj")
        self.assertEqual(ad("DDiziMom", [{"lang": "tr"}, {"lang": "en"}]), "DDiziMom | Türkçe Dublaj + Orijinal")
        self.assertEqual(ad("DDiziMom", None), "DDiziMom")

    def test_dizi_adresi(self):
        self.assertEqual(dizi_adresi(f"{_BASE}/dizi/a/sezon-2/bolum-5?x=1"), f"{_BASE}/dizi/a")
        self.assertEqual(dizi_adresi(f"{_BASE}/film/davet"), f"{_BASE}/film/davet")


if __name__ == "__main__":
    unittest.main()
