# NetMovies — istek süre sınırı sözleşmesi.
#
# Kural: video/altyazı proxy'si AKIŞ ucudur, sabit üst sınıra tabi değildir. Sınır
# konduğunda uzun izlemede istek 504'e düşüyor, oynatıcı segmenti alamayıp DONUYORDU
# (log: "Timeout: /proxy/video - 30sn aşıldı"). Kaynak tarafındaki koruma httpx'te.

import os
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

os.environ["AUTH_USER"] = ""
os.environ["AUTH_PASS"] = ""
os.environ["ADMIN_PASS"] = ""

from Core.Modules._istek import istek_timeout  # noqa: E402


class IstekTimeoutTest(unittest.TestCase):
    def test_akis_uclari_sinirsiz(self):
        self.assertIsNone(istek_timeout("/api/v1/proxy/video"))
        self.assertIsNone(istek_timeout("/api/v1/proxy/subtitle"))

    def test_dosya_uclari_uzun(self):
        self.assertEqual(istek_timeout("/admin/backup"), 120)

    def test_normal_uclar_30sn(self):
        self.assertEqual(istek_timeout("/api/v1/health"), 30)
        self.assertEqual(istek_timeout("/api/v1/proxy/image"), 30)


if __name__ == "__main__":
    unittest.main()
