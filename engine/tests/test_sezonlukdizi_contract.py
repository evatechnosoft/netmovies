"""SezonlukDizi contract test: windows-1254 bodies for cards, search and the episode table."""

import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from fake_http import FakeClient
from Plugins.SezonlukDizi import SezonlukDizi

# selector: div.afis a > div.description + img[data-src]; the " izle" suffix is stripped from titles
_CARDS = '<div class="afis"><a href="/sicak-kafa"><div class="description">Sıcak Kafa izle</div><img data-src="/p/s.jpg"></a></div>'
# selector: div.header, div.image img[data-src], span#tartismayorum-konu
_DETAIL = '<div class="header">Sıcak Kafa izle</div><div class="image"><img data-src="/p/s.jpg"></div><span id="tartismayorum-konu">Özet</span>'
# selector: table.unstackable tbody tr; td2 = season, td3 = episode, td4 a = episode link
_EPISODES = """<table class="unstackable"><tbody>
<tr><td>x</td><td>1. Sezon</td><td>2. Bölüm</td><td><a href="/sicak-kafa/1-sezon-2-bolum">Pilot</a></td></tr>
</tbody></table>"""


def _plugin(routes):
    p = SezonlukDizi.__new__(SezonlukDizi)
    p.main_url = "https://sezonlukdizi.cc"
    fake = FakeClient(routes, encoding="cp1254")
    p._client = lambda: fake  # replaces the WARP client
    return p


class SezonlukDiziContract(unittest.IsolatedAsyncioTestCase):
    async def test_main_page_cards(self):
        r = await _plugin([("diziler.asp", _CARDS)]).get_main_page(
            1, "https://sezonlukdizi.cc/diziler.asp?siralama_tipi=id&s=", "Son Eklenen Diziler")
        self.assertEqual([(x.title, x.url, x.poster) for x in r],
                         [("Sıcak Kafa", "https://sezonlukdizi.cc/sicak-kafa", "https://sezonlukdizi.cc/p/s.jpg")])

    async def test_search(self):
        r = await _plugin([("adi=", _CARDS)]).search("Sıcak")
        self.assertEqual([x.title for x in r], ["Sıcak Kafa"])

    async def test_load_item_episodes(self):
        info = await _plugin([("/bolumler/sicak-kafa", _EPISODES), ("/sicak-kafa", _DETAIL)]).load_item(
            "https://sezonlukdizi.cc/sicak-kafa")
        self.assertEqual((info.title, info.description), ("Sıcak Kafa", "Özet"))
        self.assertEqual([(b.season, b.episode, b.title) for b in info.episodes], [(1, 2, "Pilot")])


if __name__ == "__main__":
    unittest.main()
