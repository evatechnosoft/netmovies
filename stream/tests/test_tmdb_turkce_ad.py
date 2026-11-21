# Özgün adla arama boş dönünce Türkçe ada çevrilir ("whalefall" → "Balina Düşüşü").

import unittest

from Public.Home.Routers import tmdb


class _Yanit:
    status_code = 200

    def __init__(self, sonuclar: list[dict]):
        self._sonuclar = sonuclar

    def json(self) -> dict:
        return {"results": self._sonuclar}


class _Istemci:
    def __init__(self, sonuclar: list[dict]):
        self.sonuclar, self.cagri = sonuclar, 0

    async def get(self, url: str, params: dict) -> _Yanit:
        self.cagri += 1
        return _Yanit(self.sonuclar)


class TurkceAdTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self._eski = (tmdb._client, tmdb.TMDB_API_KEY)
        tmdb.TMDB_API_KEY = "x"
        tmdb._tr_ad_cache.clear()

    def tearDown(self):
        tmdb._client, tmdb.TMDB_API_KEY = self._eski

    async def test_ilk_film_ya_da_dizi_adi_ve_cache(self):
        tmdb._client = _Istemci([{"media_type": "person", "name": "Kisi"}, {"media_type": "movie", "title": "Balina Düşüşü"}])
        self.assertEqual(await tmdb.turkce_ad("whalefall"), "Balina Düşüşü")
        self.assertEqual(await tmdb.turkce_ad("Whalefall"), "Balina Düşüşü")
        self.assertEqual(tmdb._client.cagri, 1)

    async def test_sonuc_yoksa_none(self):
        tmdb._client = _Istemci([])
        self.assertIsNone(await tmdb.turkce_ad("olmayan film"))

    async def test_anahtar_yoksa_istek_atilmaz(self):
        tmdb.TMDB_API_KEY = ""
        tmdb._client = _Istemci([{"media_type": "movie", "title": "X"}])
        self.assertIsNone(await tmdb.turkce_ad("whalefall"))
        self.assertEqual(tmdb._client.cagri, 0)
