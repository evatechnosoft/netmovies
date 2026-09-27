# NetMovies — cihaz YZ kaydı (/api/v1/yz/cihaz): doğrulama ve cihaz başına tek satır.

import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

os.environ["AUTH_USER"] = ""
os.environ["AUTH_PASS"] = ""
os.environ["ADMIN_PASS"] = ""

STREAM_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(STREAM_ROOT))

import Core  # noqa: F401  — uygulamayı önce kurar, dairesel import'u açar
from Public.API.v1.Routers import yz


class YzCihazTest(unittest.TestCase):
    def test_bilinmeyen_durum_reddedilir(self):
        self.assertIsInstance(yz.kayit_olustur({"cihaz": "Pixel 9", "nano_durum": "belki"}, 0), str)
        self.assertIsInstance(yz.kayit_olustur({"nano_durum": "AVAILABLE"}, 0), str)

    def test_ayni_cihaz_tek_satir_yenisi_basta(self):
        eski = yz.kayit_olustur({"cihaz": "Pixel 9", "nano_durum": "downloadable"}, 0)
        diger = yz.kayit_olustur({"cihaz": "Galaxy S24", "nano_durum": "UNAVAILABLE"}, 0)
        yeni = yz.kayit_olustur({"cihaz": "Pixel 9", "nano_durum": "AVAILABLE"}, 0)
        liste = yz.listeye_ekle(yz.listeye_ekle(yz.listeye_ekle([], eski), diger), yeni)
        self.assertEqual([k["cihaz"] for k in liste], ["Pixel 9", "Galaxy S24"])
        self.assertEqual(liste[0]["nano_durum"], "AVAILABLE")

    def test_uc_yazar_ve_okur(self):
        from fastapi.testclient import TestClient
        from Core import kekik_FastAPI

        with tempfile.TemporaryDirectory() as dizin, \
             mock.patch.object(yz, "_PATH", Path(dizin) / "yz_cihazlar.json"):
            client = TestClient(kekik_FastAPI)
            yanit = client.post("/api/v1/yz/cihaz", json={
                "cihaz": "Google Pixel 9", "android": "16 (API 36)",
                "nano_durum": "AVAILABLE", "zaman": "2026-09-27T10:00:00",
            })
            self.assertTrue(yanit.json()["result"]["ok"])
            liste = client.get("/api/v1/yz/cihaz").json()["result"]
        self.assertEqual(liste[0]["cihaz"], "Google Pixel 9")
        self.assertEqual(liste[0]["android"], "16 (API 36)")


if __name__ == "__main__":
    unittest.main()
