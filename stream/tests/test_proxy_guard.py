# NetMovies — proxy SSRF kapısı sözleşme testleri.
#
# /proxy uçları auth'tan muaf (harici oynatıcı auth header taşımaz) ve tünelden
# dışa açık. Hedef URL üçüncü taraf kaynaktan (manifest/extractor) geliyor;
# iç ağ adresine yönlendirilmemeli. Kapı tek yerde: helpers.url_is_public.

import asyncio
import os
import sys
import unittest
from pathlib import Path

os.environ["AUTH_USER"] = ""
os.environ["AUTH_PASS"] = ""
os.environ["ADMIN_PASS"] = ""

STREAM_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(STREAM_ROOT))

from fastapi.testclient import TestClient

from Core import kekik_FastAPI
from Public.Proxy.Libs import helpers
from Public.Proxy.Libs.proxy_token import issue_proxy_token


class HostGuardTest(unittest.TestCase):
    def setUp(self) -> None:
        helpers._host_cache.clear()

    def _is_public(self, url: str) -> bool:
        return asyncio.run(helpers.url_is_public(url))

    def test_loopback_blocked(self) -> None:
        self.assertFalse(self._is_public("http://127.0.0.1:8000/x.m3u8"))

    def test_private_range_blocked(self) -> None:
        self.assertFalse(self._is_public("http://192.168.1.1/admin"))

    def test_non_http_scheme_blocked(self) -> None:
        self.assertFalse(self._is_public("file:///etc/passwd"))

    def test_public_ip_allowed(self) -> None:
        self.assertTrue(self._is_public("http://1.1.1.1/x.m3u8"))


class ProxyEndpointGuardTest(unittest.TestCase):
    """Geçerli token bile iç ağ adresini açmamalı — token host'a bağlı, ağa değil."""

    def setUp(self) -> None:
        helpers._host_cache.clear()
        self.client = TestClient(kekik_FastAPI)

    def test_video_rejects_internal_host_with_valid_token(self) -> None:
        url      = "http://192.168.1.1/x.m3u8"
        response = self.client.get(
            "/proxy/video", params={"url": url, "proxy_token": issue_proxy_token([url])}
        )
        self.assertEqual(403, response.status_code)

    def test_subtitle_rejects_internal_host_with_valid_token(self) -> None:
        url      = "http://127.0.0.1:9999/a.srt"
        response = self.client.get(
            "/proxy/subtitle", params={"url": url, "proxy_token": issue_proxy_token([url])}
        )
        self.assertEqual(403, response.status_code)

    def test_video_without_token_still_403(self) -> None:
        response = self.client.get("/proxy/video", params={"url": "https://cdn.example/x.m3u8"})
        self.assertEqual(403, response.status_code)


if __name__ == "__main__":
    unittest.main()
