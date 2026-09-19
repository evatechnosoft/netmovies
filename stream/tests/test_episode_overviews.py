# Bölüm özeti ucu: TMDB'ye çıkmadan karar verilen iki yol.
#
# 1) Başlık boşsa TMDB'ye HİÇ gidilmez (boş sözlük döner) — kota ve gecikme.
# 2) Türkçe sezonun tüm özetleri boşsa İngilizceye düşülür; biri bile doluysa
#    Türkçe kalır. Bu seçim yanlış olursa kullanıcı İngilizce özet görür.

import asyncio
import unittest

from Public.API.v1.Routers import episode_overviews as mod


class BolumOzetiTest(unittest.TestCase):
    def setUp(self):
        mod._cache.clear()

    def test_bos_baslik_tmdb_ye_gitmez(self):
        self.assertEqual(asyncio.run(mod._sezon("", 1)), {})

    def test_anahtar_yoksa_bos_doner(self):
        eski = mod._API_KEY
        mod._API_KEY = ""
        try:
            self.assertEqual(asyncio.run(mod._sezon("Neagley", 1)), {})
        finally:
            mod._API_KEY = eski

    def test_turkce_bos_ise_ingilizceye_duser(self):
        cagrilar = []

        class SahteYanit:
            def __init__(self, veri):
                self._veri = veri

            def json(self):
                return self._veri

        async def sahte_get(url, params=None):
            dil = (params or {}).get("language")
            if "search" in url:
                return SahteYanit({"results": [{"id": 1}]})
            cagrilar.append(dil)
            if dil == "tr-TR":
                return SahteYanit({"episodes": [{"episode_number": 1, "name": "A", "overview": ""}]})
            return SahteYanit({"episodes": [{"episode_number": 1, "name": "A", "overview": "İngilizce özet"}]})

        eski_key, eski_get = mod._API_KEY, mod._client.get
        mod._API_KEY = "x"
        mod._client.get = sahte_get
        try:
            cikti = asyncio.run(mod._sezon("Dizi", 1))
        finally:
            mod._API_KEY, mod._client.get = eski_key, eski_get

        self.assertEqual(cagrilar, ["tr-TR", "en-US"])
        self.assertEqual(cikti["1"]["overview"], "İngilizce özet")

    def test_turkce_doluysa_ingilizce_istenmez(self):
        cagrilar = []

        class SahteYanit:
            def __init__(self, veri):
                self._veri = veri

            def json(self):
                return self._veri

        async def sahte_get(url, params=None):
            dil = (params or {}).get("language")
            if "search" in url:
                return SahteYanit({"results": [{"id": 1}]})
            cagrilar.append(dil)
            return SahteYanit({"episodes": [{"episode_number": 2, "name": "B", "overview": "Türkçe özet"}]})

        eski_key, eski_get = mod._API_KEY, mod._client.get
        mod._API_KEY = "x"
        mod._client.get = sahte_get
        try:
            cikti = asyncio.run(mod._sezon("Dizi", 1))
        finally:
            mod._API_KEY, mod._client.get = eski_key, eski_get

        self.assertEqual(cagrilar, ["tr-TR"])
        self.assertEqual(cikti["2"]["overview"], "Türkçe özet")


if __name__ == "__main__":
    unittest.main()
