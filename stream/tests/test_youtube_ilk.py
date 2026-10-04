# Resmi YouTube listesi hızlı modda ilk kaynak olur; listesi olmayan başlık 12 saat sorulmaz.
import asyncio
import os
import sys
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
os.environ.setdefault("AUTH_PASS", "test")

from Public.API.v1.Routers import resolve_sources as rs


class YouTubeIlkTest(unittest.TestCase):
    def setUp(self):
        rs._YT_YOK.clear()

    def test_resmi_kaynak_dondurur_ve_yoksa_hatirlar(self):
        cagri = []

        async def sahte(endpoint, params=None, timeout=0, client_headers=None):
            cagri.append(params["title"])
            if params["title"] == "A.B.İ.":
                return {"sources": [{"plugin": "YouTube", "name": "YouTube | Resmi", "url": "https://yt/m.m3u8"}]}
            return {"sources": []}

        temel = {"mode": "fast", "plugin": "DiziMom", "episode_no": "16"}
        with mock.patch.object(rs, "fuck_dmca", sahte):
            k = asyncio.run(rs._youtube_kaynaklari({**temel, "title": "A.B.İ."}, {}))
            self.assertEqual([x["plugin"] for x in k], ["YouTube"])
            self.assertEqual(asyncio.run(rs._youtube_kaynaklari({**temel, "title": "Lioness"}, {})), [])
            asyncio.run(rs._youtube_kaynaklari({**temel, "title": "Lioness"}, {}))
        self.assertEqual(cagri.count("Lioness"), 1)   # ikinci kez sorulmadı

    def test_film_ve_tam_mod_sorulmaz(self):
        with mock.patch.object(rs, "fuck_dmca", side_effect=AssertionError("sorulmamalı")):
            self.assertEqual(asyncio.run(rs._youtube_kaynaklari({"mode": "fast", "title": "Film", "plugin": "X"}, {})), [])
            self.assertEqual(asyncio.run(rs._youtube_kaynaklari({"mode": "full", "title": "A", "episode_no": "1"}, {})), [])


if __name__ == "__main__":
    unittest.main()
