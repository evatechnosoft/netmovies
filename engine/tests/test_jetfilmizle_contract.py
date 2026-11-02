"""JetFilmizle contract test: JSON-LD Movie blocks for cards, search and film info."""

import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from fake_http import FakeClient
from Plugins.JetFilmizle import JetFilmizle

# selector: JSON-LD "@type":"Movie" block (name, url .../film/..., plain or ImageObject image, datePublished)
_LD = (
    r'<script type="application/ld+json">{"@type":"Movie","name":"Bug\u00fcn G\u00fczel",'
    r'"url":"https://jetfilmizle.net/film/bugun-guzel","image":"https:\/\/x\/p1.jpg","datePublished":"2025-03-01"},'
    r'{"@type":"Movie","position":2,"name":"Ikinci","url":"https://jetfilmizle.net/film/ikinci",'
    r'"image":{"@type":"ImageObject","url":"https:\/\/x\/p2.jpg"},"datePublished":"2024-01-01"}</script>'
)
# selector: <title>, meta og:description, /tur/<name> links
_DETAIL = _LD + '<title>Bugün Güzel izle - JetFilmizle</title><meta property="og:description" content="Özet"><a href="/tur/komedi">k</a>'


def _plugin(routes):
    p = JetFilmizle.__new__(JetFilmizle)
    p.main_url = "https://jetfilmizle.net"
    p.httpx = FakeClient(routes)
    return p


class JetFilmizleContract(unittest.IsolatedAsyncioTestCase):
    async def test_main_page_cards(self):
        r = await _plugin([("/filmler", _LD)]).get_main_page(1, "https://jetfilmizle.net/filmler", "Yeni Filmler")
        self.assertEqual([(x.title, x.url, x.poster) for x in r], [
            ("Bugün Güzel", "https://jetfilmizle.net/film/bugun-guzel", "https://x/p1.jpg"),
            ("Ikinci", "https://jetfilmizle.net/film/ikinci", "https://x/p2.jpg"),
        ])

    async def test_search(self):
        r = await _plugin([("/arama?q=ikinci", _LD)]).search("ikinci")
        self.assertEqual(len(r), 2)

    async def test_load_item(self):
        info = await _plugin([("/film/bugun-guzel", _DETAIL)]).load_item("https://jetfilmizle.net/film/bugun-guzel")
        self.assertEqual((info.title, str(info.year), info.poster), ("Bugün Güzel", "2025", "https://x/p1.jpg"))
        self.assertEqual(info.description, "Özet")
        self.assertIn("komedi", info.tags)


if __name__ == "__main__":
    unittest.main()
