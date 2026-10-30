"""FilmMakinesi contract test: offline fixtures for cards, search and film info."""

import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from fake_http import FakeClient
from Plugins.FilmMakinesi import FilmMakinesi

# selector: <a href title class="poster"> with a poster-lang badge in the body + <img src>; badge-less card is dropped
_CARDS = """
<a href="/film/ballerina/" title="Ballerina" class="poster"><span class="poster-lang">DUAL</span><img src="/p/b.jpg"></a>
<a href="/film/yakinda/" title="Yakında" class="poster"><img src="/p/y.jpg"></a>
"""
# selector: h1 ("<name> izle <small>(year)</small>"), meta og:*, a[href*='-filmleri-hd-izle']
_DETAIL = """
<meta property="og:image" content="/p/b.jpg"><meta property="og:description" content="Özet">
<h1>Ballerina 2026 izle <small>(2025)</small></h1>
<a href="/aksiyon-filmleri-hd-izle/">Aksiyon</a>
"""


def _plugin(routes):
    p = FilmMakinesi.__new__(FilmMakinesi)
    p.main_url = "https://filmmakinesi.co"
    p.httpx = FakeClient(routes)
    return p


class FilmMakinesiContract(unittest.IsolatedAsyncioTestCase):
    async def test_main_page_cards_badgeless_dropped(self):
        r = await _plugin([("film-arsivi", _CARDS)]).get_main_page(1, "https://filmmakinesi.co/film-arsivi/", "Yeni Filmler")
        self.assertEqual([(x.title, x.url, x.poster) for x in r],
                         [("Ballerina", "https://filmmakinesi.co/film/ballerina/", "https://filmmakinesi.co/p/b.jpg")])

    async def test_search(self):
        r = await _plugin([("?s=ballerina", _CARDS)]).search("ballerina")
        self.assertEqual([x.title for x in r], ["Ballerina"])

    async def test_load_item(self):
        info = await _plugin([("/film/ballerina/", _DETAIL)]).load_item("https://filmmakinesi.co/film/ballerina/")
        self.assertEqual((info.title, str(info.year), info.description), ("Ballerina 2026", "2025", "Özet"))
        self.assertIn("Aksiyon", info.tags)


if __name__ == "__main__":
    unittest.main()
