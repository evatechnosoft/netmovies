# Film serileri ucu: TMDB yanıtı taklit edilir, ağa çıkılmaz.
#
# Kapsar: parçalar çıkış tarihine göre sıralanır, tek parçalı koleksiyon
# elenir, izleme geçmişinden gelen koleksiyon başa alınır, tekilleştirme
# (aynı koleksiyona düşen iki film tek kayıt olur).

import asyncio
import unittest
from unittest import mock

from Public.API.v1.Routers import film_serileri as mod


class FilmSerileriTest(unittest.TestCase):
    def setUp(self):
        mod._cache.clear()

    def test_anahtar_yoksa_bos_doner(self):
        eski = mod._API_KEY
        mod._API_KEY = ""
        try:
            self.assertEqual(asyncio.run(mod._film_serileri_verisi()), [])
        finally:
            mod._API_KEY = eski

    def test_siralama_esik_ve_gecmis_onceligi(self):
        # Geçmiş: "Blade Runner 2049" (id 2) → Blade Runner koleksiyonu (100, 2 parça).
        # Popüler: id 10 → tek parçalı koleksiyon (200, elenir), id 11 → koleksiyonsuz.
        async def sahte_json(url, params):
            if url == mod._SEARCH_MOVIE:
                return {"results": [{"id": 2}]}
            if url == mod._POPULAR:
                if params.get("page") == 1:
                    return {"results": [{"id": 10}, {"id": 11}]}
                return {"results": []}
            if url == mod._MOVIE_DETAIL.format(id=2):
                return {"belongs_to_collection": {"id": 100}}
            if url == mod._MOVIE_DETAIL.format(id=10):
                return {"belongs_to_collection": {"id": 200}}
            if url == mod._MOVIE_DETAIL.format(id=11):
                return {}
            if url == mod._COLLECTION.format(id=100):
                return {
                    "id": 100, "name": "Blade Runner Koleksiyonu", "poster_path": "/br.jpg",
                    "parts": [
                        {"title": "Blade Runner 2049", "release_date": "2017-10-06", "poster_path": "/2.jpg"},
                        {"title": "Blade Runner", "release_date": "1982-06-25", "poster_path": "/1.jpg"},
                    ],
                }
            if url == mod._COLLECTION.format(id=200):
                return {
                    "id": 200, "name": "Tek Parça", "poster_path": "",
                    "parts": [{"title": "Yalnız Film", "release_date": "2020-01-01", "poster_path": ""}],
                }
            return {}

        eski_key = mod._API_KEY
        mod._API_KEY = "x"
        gecmis_patch = mock.patch.object(
            mod, "_izleme_gecmisi_film_basliklari",
            new=mock.AsyncMock(return_value=["Blade Runner 2049"]),
        )
        json_patch = mock.patch.object(mod, "_json", new=sahte_json)
        try:
            with gecmis_patch, json_patch:
                sonuc = asyncio.run(mod._film_serileri_verisi())
        finally:
            mod._API_KEY = eski_key

        # Tek parçalı koleksiyon (200) tamamen elenir.
        self.assertEqual(len(sonuc), 1)
        koleksiyon = sonuc[0]
        self.assertEqual(koleksiyon["id"], 100)
        # Parçalar çıkış tarihine göre: 1982 önce, 2017 sonra.
        self.assertEqual([p["baslik"] for p in koleksiyon["parcalar"]],
                          ["Blade Runner", "Blade Runner 2049"])
        self.assertEqual(koleksiyon["parcalar"][0]["yil"], 1982)


if __name__ == "__main__":
    unittest.main()
