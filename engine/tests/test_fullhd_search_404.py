"""FullHDFilmizlesene: the site answers an empty search with 404 and the client raises on it."""

import os
import sys
import unittest

import httpx

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from Plugins.FullHDFilmizlesene import FullHDFilmizlesene


class _Raises:
    def __init__(self, status: int):
        self.status = status

    async def get(self, url, **kw):
        istek = httpx.Request("GET", url)
        raise httpx.HTTPStatusError("Non-2xx response", request=istek, response=httpx.Response(self.status, request=istek))


def _plugin(status: int) -> FullHDFilmizlesene:
    p = FullHDFilmizlesene.__new__(FullHDFilmizlesene)
    p.main_url = "https://example.invalid"
    p.httpx = _Raises(status)
    return p


class FullHDSearch404(unittest.IsolatedAsyncioTestCase):
    async def test_404_is_empty(self):
        self.assertEqual(await _plugin(404).search("whalefall"), [])

    async def test_other_errors_propagate(self):
        with self.assertRaises(httpx.HTTPStatusError):
            await _plugin(503).search("whalefall")


if __name__ == "__main__":
    unittest.main()
