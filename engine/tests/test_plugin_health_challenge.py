# Cloudflare sınaması dönen canlı alan adı "sağlıksız" sayılıp katalogdan düşmemeli (DDizi, 10.11).
import asyncio
import unittest
from unittest import mock

import httpx

from Public.API.v1.Routers import plugin_health


class _Eklenti:
    main_url = "https://www.ddizi.tel"


def _kontrol(status: int, headers: dict) -> dict:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(status, headers=headers)

    gercek = httpx.AsyncClient
    with mock.patch.object(plugin_health.plugin_manager, "select_plugin", return_value=_Eklenti()), \
         mock.patch.object(plugin_health.httpx, "AsyncClient",
                           lambda **kw: gercek(transport=httpx.MockTransport(handler), **kw)), \
         mock.patch.object(plugin_health, "_WARP_PROXY", ""):
        return asyncio.run(plugin_health._check_one("DDizi"))


class PluginHealthChallengeTest(unittest.TestCase):
    def test_cf_sinamasi_canli_sayilir(self):
        self.assertTrue(_kontrol(403, {"cf-mitigated": "challenge"})["ok"])

    def test_duz_403_sagliksiz(self):
        self.assertFalse(_kontrol(403, {})["ok"])


if __name__ == "__main__":
    unittest.main()
