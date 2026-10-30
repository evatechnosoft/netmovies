"""DiziAbi contract test: offline fixtures for cards, search and episode parsing."""

import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from fake_http import FakeClient
from Plugins.DiziAbi import DiziAbi

# selector: a.group.block > h3 + img[src]; only hrefs containing /dizi/ (films are dropped)
_CARDS = """
<a class="group block" href="/dizi/lioness"><h3>Lioness</h3><img src="/p/lioness.jpg"></a>
<a class="group block" href="/film/bir-film"><h3>Bir Film</h3><img src="/p/f.jpg"></a>
"""
# selector: meta[property=og:*]; season links "?sezon=N#bolumler"; episodes a.group with href "-S-sezon-E-bolum"
_DETAIL = """
<meta property="og:title" content="Lioness | DiziAbi">
<meta property="og:image" content="https://x/lioness.jpg">
<meta property="og:description" content="Özet">
<a href="?sezon=1#bolumler">1</a><a href="?sezon=2#bolumler">2</a>
"""
_SEASON1 = '<a class="group" href="/bolum/lioness-1-sezon-1-bolum">x</a><a class="group" href="/bolum/lioness-2-sezon-9-bolum">y</a>'
_SEASON2 = '<a class="group" href="/bolum/lioness-2-sezon-3-bolum">x</a>'


def _plugin(routes):
    p = DiziAbi.__new__(DiziAbi)
    p.main_url = "https://www.diziabi.net"
    p.httpx = FakeClient(routes)
    return p


class DiziAbiContract(unittest.IsolatedAsyncioTestCase):
    async def test_main_page_cards(self):
        r = await _plugin([("yabanci-dizi", _CARDS)]).get_main_page(
            1, "https://www.diziabi.net/yabanci-dizi-izle/?page=SAYFA", "Yabancı Diziler")
        self.assertEqual([(x.title, x.url, x.poster) for x in r],
                         [("Lioness", "https://www.diziabi.net/dizi/lioness", "/p/lioness.jpg")])

    async def test_search(self):
        r = await _plugin([("/ara?q=lioness", _CARDS)]).search("lioness")
        self.assertEqual([x.title for x in r], ["Lioness"])

    async def test_load_item_episodes(self):
        info = await _plugin([("?sezon=1", _SEASON1), ("?sezon=2", _SEASON2), ("/dizi/lioness", _DETAIL)]).load_item(
            "https://www.diziabi.net/dizi/lioness")
        self.assertEqual((info.title, info.poster), ("Lioness", "https://x/lioness.jpg"))
        # the season-2 link on the season-1 page is filtered out
        self.assertEqual([(b.season, b.episode) for b in info.episodes], [(1, 1), (2, 3)])


if __name__ == "__main__":
    unittest.main()
