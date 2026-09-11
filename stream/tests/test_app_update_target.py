# OTA hedef ayrımı: saat APK'sı televizyona, TV APK'sı saate inmemeli.
# Ayrım dosya adı önekinden geliyor (`NetMovies-TV-` / `NetMovies-Wear-`).

import unittest
from pathlib import Path
from unittest import mock

from Public.API.v1.Routers import app_update


class AppUpdateTargetTest(unittest.TestCase):
    DOSYALAR = [
        Path("/data/apk/NetMovies-TV-v0.1.77.apk"),
        Path("/data/apk/NetMovies-TV-v0.1.76.apk"),
        Path("/data/apk/NetMovies-Wear-v0.1.0.apk"),
        Path("/data/apk/app-debug.apk"),           # sürümsüz: hiçbir hedefe girmez
    ]

    def _latest(self, hedef):
        # Path örneğinin metotları salt-okunur; yerine sahte bir dizin nesnesi konur.
        sahte = mock.Mock()
        sahte.is_dir.return_value = True
        sahte.glob.return_value = self.DOSYALAR
        with mock.patch.object(app_update, "APK_DIR", sahte):
            return app_update._latest(hedef)

    def test_tv_en_yuksek_surumu_alir(self):
        yol, surum = self._latest("tv")
        self.assertEqual(yol.name, "NetMovies-TV-v0.1.77.apk")
        self.assertEqual(surum, (0, 1, 77))

    def test_wear_kendi_apkisini_alir(self):
        yol, _ = self._latest("wear")
        self.assertEqual(yol.name, "NetMovies-Wear-v0.1.0.apk")

    def test_bilinmeyen_hedef_tv_sayilir(self):
        self.assertEqual(app_update._hedef({"target": "buzdolabi"}), "tv")
        self.assertEqual(app_update._hedef(None), "tv")
        self.assertEqual(app_update._hedef({"target": "WEAR"}), "wear")


if __name__ == "__main__":
    unittest.main()
