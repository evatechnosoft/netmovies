# Bayat keep-alive bağlantısı kullanıcıya "kaynak bulunamadı" göstermemeli.
#
# Motor, havuzdaki bağlantıyı biz kullanmadan kapatırsa httpx gövdesiz düşer
# (RemoteProtocolError). İçerik sağlamdır; tek yapılması gereken taze bağlantıyla
# tekrar denemektir. Gerçek olayı chain_scan yakaladı (18 Eylül, KultFilmler → 500).

import asyncio
import sys
import unittest
from pathlib import Path

STREAM_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(STREAM_ROOT))

import httpx

import Public.API.v1.Libs as libs


class ProviderRetryTest(unittest.TestCase):
    def _fetch_with(self, handler) -> object:
        onceki = libs._client
        libs._client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
        try:
            return asyncio.run(libs._fetch("http://engine:3310", "/load_item", None, 30.0))
        finally:
            asyncio.run(libs._client.aclose())
            libs._client = onceki

    def test_stale_connection_is_retried_once(self) -> None:
        cagri = {"n": 0}

        def handler(request: httpx.Request) -> httpx.Response:
            cagri["n"] += 1
            if cagri["n"] == 1:
                raise httpx.RemoteProtocolError("Server disconnected without sending a response.")
            return httpx.Response(200, json={"result": {"title": "Davet"}})

        self.assertEqual({"title": "Davet"}, self._fetch_with(handler))
        self.assertEqual(2, cagri["n"])

    def test_second_disconnect_surfaces_the_error(self) -> None:
        cagri = {"n": 0}

        def handler(request: httpx.Request) -> httpx.Response:
            cagri["n"] += 1
            raise httpx.RemoteProtocolError("Server disconnected without sending a response.")

        with self.assertRaises(ValueError):
            self._fetch_with(handler)
        self.assertEqual(2, cagri["n"])


if __name__ == "__main__":
    unittest.main()
