# Kaynakların çoğu aynı anda erişilemez görünüyorsa ölçüm 6 saat saklanmamalı (WARP kesintisi, 28.11).
import unittest

from Public.API.v1.Routers import plugin_health


class SaglikOmruTest(unittest.TestCase):
    def test_cogu_saglikli_uzun_omur(self):
        self.assertEqual(plugin_health._omur({"healthy": 15, "total": 16}), plugin_health._TTL_SECONDS)
        self.assertEqual(plugin_health._omur({"healthy": 8, "total": 16}), plugin_health._TTL_SECONDS)

    def test_cogu_erisilemez_kisa_omur(self):
        self.assertEqual(plugin_health._omur({"healthy": 1, "total": 16}), plugin_health._TTL_SUPHELI)
        self.assertEqual(plugin_health._omur({"healthy": 0, "total": 16}), plugin_health._TTL_SUPHELI)


if __name__ == "__main__":
    unittest.main()
