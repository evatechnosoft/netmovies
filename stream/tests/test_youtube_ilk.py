# Resmi YouTube listesi hızlı modda yedek kaynak olur (sona eklenir); listesi olmayan başlık 12 saat sorulmaz.
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

    def test_youtube_adli_kaynak_sona(self):
        k = [{"name": "DDizi · DDizi | YouTube (resmi)"}, {"name": "DDizi · twimg"}, {"name": "HDFC"}]
        self.assertEqual([x["name"] for x in rs.youtube_sona(k)], ["DDizi · twimg", "HDFC", "DDizi · DDizi | YouTube (resmi)"])

    def test_secili_youtube_tam_zincire_dusmez(self):
        cagri = []

        async def sahte(endpoint, params=None, timeout=0, client_headers=None):
            cagri.append(params["mode"])
            return {"sources": [{"plugin": "YouTube", "name": "YouTube | Resmi", "url": "https://yt/m.m3u8"}]}

        istek = mock.Mock(base_url="http://x/")
        istek.state.veri = {"mode": "fast", "plugin": "YouTube", "title": "Suskunlar", "encoded_url": "https://youtube.com/watch?v=a"}
        with mock.patch.object(rs, "fuck_dmca", sahte), mock.patch.object(rs, "get_client_headers", lambda r: {}),              mock.patch.object(rs.kayit, "hazir_kayit", lambda *a: None), mock.patch.object(rs.lang_memo, "kaydet", lambda *a: None):
            asyncio.run(rs.resolve_sources(istek))
        self.assertEqual(cagri, ["fast"])

    def test_secili_youtube_tam_modda_once(self):
        async def sahte(endpoint, params=None, timeout=0, client_headers=None):
            return {"sources": [{"plugin": "DiziAbi", "name": "DiziAbi · pilav", "url": "https://a/m.m3u8"},
                                {"plugin": "YouTube", "name": "YouTube | Resmi", "url": "https://yt/m.m3u8"}]}

        istek = mock.Mock(base_url="http://x/")
        istek.state.veri = {"mode": "full", "plugin": "YouTube", "title": "Suskunlar", "encoded_url": "https://youtube.com/watch?v=a"}
        with mock.patch.object(rs, "fuck_dmca", sahte), mock.patch.object(rs, "get_client_headers", lambda r: {}),              mock.patch.object(rs.kayit, "hazir_kayit", lambda *a: None), mock.patch.object(rs.lang_memo, "kaydet", lambda *a: None):
            sonuc = asyncio.run(rs.resolve_sources(istek))
        self.assertEqual(sonuc["result"]["sources"][0]["plugin"], "YouTube")


if __name__ == "__main__":
    unittest.main()
