# WARP proxy 503 verdiğinde zincir donuyordu: DiziPal fetch_html'e zaten WARP
# istemcisini geçiyor, eski akış ise "client = doğrudan" varsayıp fallback olarak
# yine WARP'ı deniyordu — üç deneme de aynı bozuk yoldan gidip ~18sn yiyordu.

import asyncio
import unittest

import httpx

from Plugins import __dizi_common as ortak


class SahteIstemci:
    def __init__(self, hata: Exception | None = None, govde: str = ""):
        self.hata = hata
        self.govde = govde
        self.cagri = 0

    async def get(self, url, headers=None, cookies=None, timeout=None):
        self.cagri += 1
        if self.hata:
            raise self.hata
        return httpx.Response(200, text=self.govde, request=httpx.Request("GET", url))


class FetchHtmlFallbackTest(unittest.TestCase):
    def setUp(self):
        self._warp = ortak._warp_client
        self._plain = ortak._plain_client

    def tearDown(self):
        ortak._warp_client = self._warp
        ortak._plain_client = self._plain

    def test_warp_503_dogrudan_istemciye_duser(self):
        warp = SahteIstemci(hata=httpx.ProxyError("503 Service Unavailable"))
        plain = SahteIstemci(govde="x" * 500)
        ortak._warp_client, ortak._plain_client = warp, plain

        # DiziPal'in yaptığı: client OLARAK warp geçiliyor.
        sonuc = asyncio.run(ortak.fetch_html(warp, "https://ornek.test/film"))

        self.assertEqual(sonuc, "x" * 500)
        self.assertEqual(warp.cagri, 1, "aynı bozuk yol birden çok kez denenmemeli")
        self.assertEqual(plain.cagri, 1)

    def test_hepsi_dusunce_hata_yukselir(self):
        warp = SahteIstemci(hata=httpx.ProxyError("503"))
        plain = SahteIstemci(hata=httpx.ConnectError("dns"))
        ortak._warp_client, ortak._plain_client = warp, plain

        with self.assertRaises(httpx.ConnectError):
            asyncio.run(ortak.fetch_html(warp, "https://ornek.test/film"))


if __name__ == "__main__":
    unittest.main()
