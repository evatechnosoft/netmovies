# Kaos Show (8 Nov): episode videos opened one by one were saved as separate movie
# cards; opening one had no next episode. A video inside a playlist the user keeps
# must map to that series with its S/E ref.

import asyncio
import unittest

from Public.API.v1.Libs import youtube_dizi as yd

LISTE = "https://www.youtube.com/playlist?list=PLx"


class YoutubeDiziTest(unittest.TestCase):
    def setUp(self) -> None:
        self.eski = (yd.fuck_dmca, yd._oynatma_listeleri)
        yd._onbellek.clear()

        async def sahte(endpoint, params=None, **_):
            return {"title": "Kaos Show", "episodes": [
                {"season": 2, "episode": 16, "url": "https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3Dzd0abwy2Xxk"},
            ]}

        yd.fuck_dmca = sahte
        yd._oynatma_listeleri = lambda: [{"plugin": "YouTube", "title": "Kaos Show", "content_url": LISTE, "poster": "p"}]

    def tearDown(self) -> None:
        yd.fuck_dmca, yd._oynatma_listeleri = self.eski
        yd._onbellek.clear()

    def test_listedeki_video_diziye_baglanir(self) -> None:
        d = asyncio.run(yd.diziye_bagla("https://www.youtube.com/watch?v=zd0abwy2Xxk"))
        self.assertEqual({"title": "Kaos Show", "content_url": LISTE, "poster": "p", "episode": "S2B16"}, d)

    def test_listede_olmayan_video_baglanmaz(self) -> None:
        self.assertIsNone(asyncio.run(yd.diziye_bagla("https://www.youtube.com/watch?v=AAAAAAAAAAA")))

    def test_oynatma_listesi_adresi_dokunulmaz(self) -> None:
        self.assertIsNone(asyncio.run(yd.diziye_bagla(LISTE + "&v=zd0abwy2Xxk")))


if __name__ == "__main__":
    unittest.main()
