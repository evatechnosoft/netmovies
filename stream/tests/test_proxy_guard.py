# NetMovies — proxy SSRF kapısı sözleşme testleri.
#
# /proxy uçları auth'tan muaf (harici oynatıcı auth header taşımaz) ve tünelden
# dışa açık. Hedef URL üçüncü taraf kaynaktan (manifest/extractor) geliyor.
# Kural: internet serbest · ev LAN'ı (PROXY_ALLOWED_NETS) serbest · geri kalan
# iç ağ (loopback, link-local, diğer özel bloklar) kapalı.
# Kapı tek yerde: helpers.url_is_public.

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

    def test_link_local_blocked(self) -> None:
        # Bulut metadata servisi klasik SSRF hedefi; izin listesinde değil.
        self.assertFalse(self._is_public("http://169.254.169.254/latest/meta-data/"))

    def test_other_private_range_blocked(self) -> None:
        # 10.x izin listesinde değil (varsayılan yalnız 192.168.0.0/16).
        self.assertFalse(self._is_public("http://10.0.0.5/x.m3u8"))

    def test_non_http_scheme_blocked(self) -> None:
        self.assertFalse(self._is_public("file:///etc/passwd"))

    def test_public_ip_allowed(self) -> None:
        self.assertTrue(self._is_public("http://1.1.1.1/x.m3u8"))

    def test_home_lan_allowed(self) -> None:
        # Ev ağındaki kaynaklar (NAS/ZimaOS M3U, LAN IPTV) proxy'den geçebilmeli.
        self.assertTrue(self._is_public("http://192.168.1.186:4602/liste.m3u"))
        self.assertTrue(self._is_public("http://192.168.0.10/kanal.ts"))

    def test_allowed_nets_is_configurable(self) -> None:
        """PROXY_ALLOWED_NETS boşsa iç ağın tamamı kapanır (en sıkı mod)."""
        original = helpers._ALLOWED_NETS
        helpers._ALLOWED_NETS = ()
        helpers._host_cache.clear()
        try:
            self.assertFalse(self._is_public("http://192.168.1.186/liste.m3u"))
        finally:
            helpers._ALLOWED_NETS = original
            helpers._host_cache.clear()


class ProxyEndpointGuardTest(unittest.TestCase):
    """Geçerli token bile izin listesi dışındaki adresi açmamalı."""

    def setUp(self) -> None:
        helpers._host_cache.clear()
        self.client = TestClient(kekik_FastAPI)

    def test_video_rejects_blocked_host_with_valid_token(self) -> None:
        url      = "http://169.254.169.254/latest/meta-data/"
        response = self.client.get(
            "/proxy/video", params={"url": url, "proxy_token": issue_proxy_token([url])}
        )
        self.assertEqual(403, response.status_code)

    def test_subtitle_rejects_blocked_host_with_valid_token(self) -> None:
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
