# NetMovies — poster hattı sözleşme testleri.
#
# Zincir sunucuda tek yerde çalışır: kaynak → proxy cache → TMDB (başlıkla) →
# placeholder. Web şablonları, tarayıcı JS'i ve TV istemcisi aynı URL'i üretir;
# bu testler o sözleşmeyi ve kırık poster davranışını sabitler.

import os
import sys
import unittest
from pathlib import Path

os.environ["AUTH_USER"] = ""
os.environ["AUTH_PASS"] = ""
os.environ["ADMIN_PASS"] = ""

STREAM_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(STREAM_ROOT))

import httpx
from fastapi.testclient import TestClient

from Public.Home.Routers import _poster_proxy
from Public.Proxy.Routers import image as image_proxy
from Core import kekik_FastAPI


PNG_1PX = bytes.fromhex(
    "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c4"
    "890000000a49444154789c6360000002000100ffff03000006000557bfabd400"
    "00000049454e44ae426082"
)


class PosterHelperTest(unittest.TestCase):
    """Sunucu helper'ı — JS `posterUrl()` ve Kotlin `proxiedPoster()` ile aynı sözleşme."""

    def test_remote_poster_goes_through_proxy(self) -> None:
        self.assertEqual(
            "/proxy/image?url=https%3A%2F%2Fcdn%2Fa.jpg",
            _poster_proxy("https://cdn/a.jpg"),
        )

    def test_title_is_attached_for_server_side_fallback(self) -> None:
        self.assertEqual(
            "/proxy/image?url=https%3A%2F%2Fcdn%2Fa.jpg&title=Dark",
            _poster_proxy("https://cdn/a.jpg", "Dark"),
        )

    def test_missing_poster_still_resolves_via_title(self) -> None:
        self.assertEqual("/proxy/image?url=&title=Dark", _poster_proxy("", "Dark"))

    def test_local_url_is_left_untouched(self) -> None:
        self.assertEqual("/static/x.png", _poster_proxy("/static/x.png", "Dark"))

    def test_nothing_to_show_returns_empty(self) -> None:
        self.assertEqual("", _poster_proxy(None, None))


class PosterProxyTest(unittest.TestCase):
    def setUp(self) -> None:
        self.requests: list[httpx.Request] = []
        self.response_factory = lambda request: httpx.Response(
            200, content=PNG_1PX, headers={"content-type": "image/png"}
        )

        def handler(request: httpx.Request) -> httpx.Response:
            self.requests.append(request)
            return self.response_factory(request)

        self._real_client = image_proxy.shared_client
        image_proxy.shared_client = httpx.AsyncClient(transport=httpx.MockTransport(handler))

        # Testler DNS'e bağlı olmasın: sahte CDN host'u "public" sayılır.
        # SSRF davranışı gerçek fonksiyonla ayrıca test edilir (aşağıda).
        self._real_host_check = image_proxy._host_is_public

        async def _always_public(host: str) -> bool:
            return bool(host)

        image_proxy._host_is_public = _always_public

        # Süreç-içi cache'ler global: testler birbirini kirletmesin.
        image_proxy._img_cache.clear()
        image_proxy._img_cache_bytes = 0
        image_proxy._neg_cache.clear()

        self.client = TestClient(kekik_FastAPI)

    def tearDown(self) -> None:
        image_proxy.shared_client = self._real_client
        image_proxy._host_is_public = self._real_host_check
        image_proxy._img_cache.clear()
        image_proxy._neg_cache.clear()

    def _get(self, **params) -> httpx.Response:
        return self.client.get("/proxy/image", params=params, follow_redirects=False)

    def test_successful_poster_is_served_and_cached(self) -> None:
        first = self._get(url="https://cdn/a.jpg")
        second = self._get(url="https://cdn/a.jpg")

        self.assertEqual(200, first.status_code)
        self.assertEqual("MISS", first.headers["X-Cache"])
        self.assertEqual("HIT", second.headers["X-Cache"])
        self.assertEqual(1, len(self.requests), "cache HIT upstream'e çıkmamalı")

    def test_broken_poster_falls_back_to_tmdb_when_title_known(self) -> None:
        self.response_factory = lambda request: httpx.Response(404)
        res = self._get(url="https://cdn/dead.jpg", title="Dark")

        self.assertEqual(302, res.status_code)
        self.assertEqual("/tmdb-poster?title=Dark", res.headers["location"])

    def test_broken_poster_without_title_reports_error(self) -> None:
        self.response_factory = lambda request: httpx.Response(404)
        res = self._get(url="https://cdn/dead.jpg")

        self.assertEqual(502, res.status_code)

    def test_broken_poster_is_negatively_cached(self) -> None:
        """Ölü poster her rafta tekrar ediyor; her denemede CDN'e çıkmak rafı geciktiriyordu."""
        self.response_factory = lambda request: httpx.Response(404)
        self._get(url="https://cdn/dead.jpg", title="Dark")
        res = self._get(url="https://cdn/dead.jpg", title="Dark")

        self.assertEqual(1, len(self.requests), "negatif cache ikinci turu engellemeli")
        self.assertEqual("NEG", res.headers["X-Cache"])
        self.assertEqual(302, res.status_code)

    def test_non_image_response_is_rejected(self) -> None:
        self.response_factory = lambda request: httpx.Response(
            200, content=b"<html>bot kontrol</html>", headers={"content-type": "text/html"}
        )
        res = self._get(url="https://cdn/a.jpg", title="Dark")

        self.assertEqual(302, res.status_code)
        self.assertEqual("NOT_IMAGE", res.headers["X-Cache"])

    def test_empty_url_uses_title_directly(self) -> None:
        res = self._get(url="", title="Dark")

        self.assertEqual(302, res.status_code)
        self.assertEqual([], self.requests, "kaynak yoksa CDN'e çıkılmamalı")

    def test_private_host_is_blocked(self) -> None:
        """SSRF: proxy iç ağa istek atmamalı (gerçek host kontrolüyle)."""
        image_proxy._host_is_public = self._real_host_check
        res = self._get(url="http://127.0.0.1:3310/admin", title="Dark")

        self.assertEqual([], self.requests)
        self.assertEqual("BLOCKED_HOST", res.headers["X-Cache"])

    def test_non_http_scheme_is_blocked(self) -> None:
        res = self._get(url="file:///etc/passwd")

        self.assertEqual([], self.requests)
        self.assertEqual(502, res.status_code)


if __name__ == "__main__":
    unittest.main()
