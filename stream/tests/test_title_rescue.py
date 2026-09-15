# NetMovies — başlık kurtarma sözleşme testleri.
#
# Kurtarma YALNIZ zincir boş döndüğünde çalışmalı: normal akışta Gemini'ye
# uğramak her oynatmaya gecikme ve maliyet ekler. Anahtar yoksa sessizce
# devre dışı kalmalı — özellik yokluğu hata değildir.

import asyncio
import os
import sys
import unittest
from pathlib import Path
from unittest import mock

os.environ["AUTH_USER"] = ""
os.environ["AUTH_PASS"] = ""
os.environ["ADMIN_PASS"] = ""

STREAM_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(STREAM_ROOT))

from Public.API.v1.Libs import title_rescue


class TitleRescueTest(unittest.TestCase):
    def test_anahtar_yoksa_sessizce_bos(self):
        with mock.patch.object(title_rescue.gemini, "anahtar_var", return_value=False):
            sonuc = asyncio.run(title_rescue.alternatif_basliklar("The Odyssey"))
        self.assertEqual(sonuc, [])

    def test_bos_baslik_gemini_cagirmaz(self):
        with mock.patch.object(title_rescue.gemini, "sor") as sor:
            sonuc = asyncio.run(title_rescue.alternatif_basliklar("   "))
        self.assertEqual(sonuc, [])
        sor.assert_not_called()

    def test_alternatifler_temizlenir(self):
        async def sahte(**_):
            return {"basliklar": ["Odyssey", "the odyssey", "Odesa Destanı", "Odyssey", ""]}, ""

        with mock.patch.object(title_rescue.gemini, "anahtar_var", return_value=True), \
             mock.patch.object(title_rescue.gemini, "sor", side_effect=sahte):
            sonuc = asyncio.run(title_rescue.alternatif_basliklar("The Odyssey"))

        # Aynı başlık (büyük/küçük harf farkıyla bile) ve tekrarlar elenir,
        # deneme sayısı sınırlıdır.
        self.assertEqual(sonuc, ["Odyssey", "Odesa Destanı"])
        self.assertLessEqual(len(sonuc), title_rescue.MAX_DENEME)

    def test_gemini_hatasi_oynatmayi_bozmaz(self):
        async def sahte(**_):
            return None, "gemini 429"

        with mock.patch.object(title_rescue.gemini, "anahtar_var", return_value=True), \
             mock.patch.object(title_rescue.gemini, "sor", side_effect=sahte):
            sonuc = asyncio.run(title_rescue.alternatif_basliklar("The Odyssey"))
        self.assertEqual(sonuc, [])

    def test_bozuk_yanit_bos_doner(self):
        async def sahte(**_):
            return {"basliklar": "metin degil liste olmali"}, ""

        with mock.patch.object(title_rescue.gemini, "anahtar_var", return_value=True), \
             mock.patch.object(title_rescue.gemini, "sor", side_effect=sahte):
            sonuc = asyncio.run(title_rescue.alternatif_basliklar("The Odyssey"))
        self.assertEqual(sonuc, [])


if __name__ == "__main__":
    unittest.main()


class KurtarmaTetiklemeTest(unittest.TestCase):
    """Kurtarma normal akışa dokunmamalı: sonuç varken Gemini'ye uğranmaz."""

    def setUp(self) -> None:
        from fastapi.testclient import TestClient
        from Core import kekik_FastAPI

        self.client = TestClient(kekik_FastAPI)

    def test_kaynak_bulunduysa_kurtarma_calismaz(self):
        async def sahte_engine(path, params=None, **_):
            return {"sources": [{"url": "https://x/a.m3u8", "plugin": "DiziPal"}], "episodes": [], "diagnostics": []}

        with mock.patch("Public.API.v1.Routers.resolve_sources.fuck_dmca", side_effect=sahte_engine), \
             mock.patch("Public.API.v1.Routers.resolve_sources.alternatif_basliklar") as kurtar:
            self.client.get("/api/v1/resolve_sources", params={"plugin": "DiziPal", "encoded_url": "x", "title": "Dark"})
        kurtar.assert_not_called()

    def test_fast_modda_kurtarma_calismaz(self):
        """Hızlı yolda kullanıcı ilk kaynağı bekliyor; tam zincir arkadan geliyor."""
        async def bos_engine(path, params=None, **_):
            return {"sources": [], "episodes": [], "diagnostics": []}

        with mock.patch("Public.API.v1.Routers.resolve_sources.fuck_dmca", side_effect=bos_engine), \
             mock.patch("Public.API.v1.Routers.resolve_sources.alternatif_basliklar") as kurtar:
            self.client.get(
                "/api/v1/resolve_sources",
                params={"plugin": "DiziPal", "encoded_url": "x", "title": "Dark", "mode": "fast"},
            )
        kurtar.assert_not_called()

    def test_bos_zincirde_alternatif_baslikla_yeniden_denenir(self):
        cagrilan_basliklar: list[str] = []

        async def engine(path, params=None, **_):
            baslik = (params or {}).get("title", "")
            cagrilan_basliklar.append(baslik)
            if baslik == "Odyssey":
                return {"sources": [{"url": "https://x/a.m3u8", "plugin": "DiziPal"}], "episodes": [], "diagnostics": []}
            return {"sources": [], "episodes": [], "diagnostics": []}

        async def alternatifler(_baslik):
            return ["Odyssey"]

        with mock.patch("Public.API.v1.Routers.resolve_sources.fuck_dmca", side_effect=engine), \
             mock.patch("Public.API.v1.Routers.resolve_sources.alternatif_basliklar", side_effect=alternatifler):
            res = self.client.get(
                "/api/v1/resolve_sources",
                params={"plugin": "DiziPal", "encoded_url": "x", "title": "The Odyssey"},
            )

        self.assertEqual(cagrilan_basliklar, ["The Odyssey", "Odyssey"])
        sonuc = res.json()["result"]
        self.assertEqual(len(sonuc["sources"]), 1)
        # Kullanıcı neden başka başlıkla bulunduğunu raporda görebilmeli.
        self.assertTrue(any(d["stage"] == "kurtarma" for d in sonuc["diagnostics"]))
