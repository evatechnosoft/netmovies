# Çözümleme sonucu 180sn cache'lenir. Proxy sarmalaması cache'teki kaydı
# değiştirirse her çağrı bir kat daha sarar ve üçüncü denemede 403 gelir.

import unittest

from Public.API.v1.Libs.source_proxy import route_through_proxy


class ResolveCacheIsolationTest(unittest.TestCase):
    def _kaynak(self):
        return {"plugin": "FilmMakinesi", "url": "https://cdn.example/index.m3u8", "referer": "https://e/", "user_agent": "UA"}

    def test_sarma_girdiyi_degistirmez(self):
        cachelenen = {"sources": [self._kaynak()]}
        kopya      = {**cachelenen}
        kopya["sources"] = route_through_proxy(cachelenen["sources"], "http://localhost:3310")

        self.assertTrue(kopya["sources"][0]["url"].startswith("http://localhost:3310/proxy/video?url="))
        # Cache'teki kayıt ham URL'iyle kalmalı — yoksa sonraki çağrı üstüne sarar.
        self.assertEqual(cachelenen["sources"][0]["url"], "https://cdn.example/index.m3u8")

    def test_tekrarlanan_sarma_tek_kat_kalir(self):
        cachelenen = {"sources": [self._kaynak()]}
        ilk  = route_through_proxy([{**s} for s in cachelenen["sources"]], "http://localhost:3310")
        ikinci = route_through_proxy([{**s} for s in cachelenen["sources"]], "http://localhost:3310")
        self.assertEqual(ilk[0]["url"].count("/proxy/video?"), 1)
        self.assertEqual(ikinci[0]["url"].count("/proxy/video?"), 1)


if __name__ == "__main__":
    unittest.main()
