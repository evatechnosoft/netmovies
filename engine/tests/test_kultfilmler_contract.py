"""KultFilmler contract test: a.mcard cards, search, film and series (a.ep) detail."""

import html
import json
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from fake_http import FakeClient
from Plugins.KultFilmler import KultFilmler

# selector: a.mcard[href] > h3 (title) + img (poster)
_CARDS = '<a class="mcard" href="/film/kult-bir/"><h3>Kült Bir</h3><img class="pimg" src="/p/k.jpg"></a>'
# selector: h1.vtitle, p.desc, div.genres a, a[href*='/yapim/']
_FILM = """
<h1 class="vtitle">Kült Bir</h1><p class="desc">Özet</p>
<div class="genres"><a>Dram</a></div><a href="/yapim/2001">2001</a>
"""
# selector: a.ep[href] > h4 ("<n>. Sezon <m>. Bölüm")
_SERIES = _FILM + '<a class="ep" href="/dizi/x/s2b3/"><h4>2. Sezon 3. Bölüm</h4></a>'


def _plugin(routes):
    p = KultFilmler.__new__(KultFilmler)
    p.main_url = "https://kultfilmler.net"
    p.httpx = FakeClient(routes)
    return p


class KultFilmlerContract(unittest.IsolatedAsyncioTestCase):
    async def test_main_page_cards(self):
        r = await _plugin([("kultfilmler.net", _CARDS)]).get_main_page(1, "https://kultfilmler.net/", "Yeni Filmler")
        self.assertEqual([(x.title, x.url, x.poster) for x in r],
                         [("Kült Bir", "https://kultfilmler.net/film/kult-bir/", "https://kultfilmler.net/p/k.jpg")])

    async def test_search(self):
        r = await _plugin([("?s=kult", _CARDS)]).search("kult")
        self.assertEqual([x.title for x in r], ["Kült Bir"])

    async def test_load_item_film(self):
        info = await _plugin([("/film/", _FILM)]).load_item("https://kultfilmler.net/film/kult-bir/")
        self.assertEqual((info.title, info.description, str(info.year)), ("Kült Bir", "Özet", "2001"))
        self.assertFalse(hasattr(info, "episodes"))

    async def test_load_item_series_episodes(self):
        info = await _plugin([("/dizi/", _SERIES)]).load_item("https://kultfilmler.net/dizi/x/")
        self.assertEqual([(b.season, b.episode, b.url) for b in info.episodes],
                         [(2, 3, "https://kultfilmler.net/dizi/x/s2b3/")])

    async def test_okru_embed_resolves_hls(self):
        # selector: ok.ru `data-options` (HTML-escaped JSON) → flashvars.metadata (JSON string) → ondemandHls
        meta = json.dumps({"ondemandHls": "https://ok6.vkuser.net/m.m3u8"})
        options = html.escape(json.dumps({"flashvars": {"metadata": meta}}))
        page = '<iframe src="https://ok.ru/videoembed/1?nochat=1"></iframe>'
        plugin = _plugin([("/film/", page), ("ok.ru/videoembed", f'<div data-options="{options}"></div>')])
        links = await plugin.load_links("https://kultfilmler.net/film/kult-bir/")
        self.assertEqual([x.url for x in links], ["https://ok6.vkuser.net/m.m3u8"])


if __name__ == "__main__":
    unittest.main()
