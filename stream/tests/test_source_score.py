# NetMovies — kaynak puanlaması sözleşme testleri.
#
# Kritik davranış: veri YOKKEN sıralama engine'in elle yazılmış listesini
# aynen korur. Puanlama açmak ilk gün hiçbir şeyi değiştirmemeli — kanıt
# biriktikçe sıra değişir.

import os
import sys
import tempfile
import unittest
from pathlib import Path

os.environ["AUTH_USER"] = ""
os.environ["AUTH_PASS"] = ""
os.environ["ADMIN_PASS"] = ""

STREAM_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(STREAM_ROOT))


class SourceScoreTest(unittest.TestCase):
    def setUp(self) -> None:
        self._dir = tempfile.TemporaryDirectory()
        os.environ["SOURCE_SCORE_PATH"] = str(Path(self._dir.name) / "score.json")
        # Modül YENİDEN YÜKLENMEZ: depo yolu her çağrıda okunuyor. sys.modules'dan
        # silmek FastAPI'nin tuttuğu fonksiyon referanslarını bayatlatır ve BAŞKA
        # testlerin mock'ları hiç devreye girmez.
        from Public.API.v1.Libs import source_score

        self.ss = source_score

    def tearDown(self) -> None:
        self._dir.cleanup()
        os.environ.pop("SOURCE_SCORE_PATH", None)

    def test_veri_yokken_sira_korunur(self):
        liste = ["DiziPal", "DiziMom", "HDFilmCehennemi", "DiziBox"]
        self.assertEqual(self.ss.sirala(liste), liste)

    def test_basari_one_alir(self):
        self.ss.kaydet("DiziBox", True)
        sira = self.ss.sirala(["DiziPal", "DiziMom", "DiziBox"])
        self.assertEqual(sira[0], "DiziBox")

    def test_hata_geriye_atar(self):
        self.ss.kaydet("DiziPal", False)
        sira = self.ss.sirala(["DiziPal", "DiziMom", "DiziBox"])
        self.assertEqual(sira[-1], "DiziPal")

    def test_favori_istatistigi_gecer(self):
        # Bir sağlayıcı üst üste başarılı olsa bile yıldızlı olan öne geçer.
        for _ in range(2):
            self.ss.kaydet("DiziBox", True)
        sira = self.ss.sirala(["DiziPal", "DiziBox"], favoriler=["DiziPal"])
        self.assertEqual(sira[0], "DiziPal")

    def test_tavan_asilmaz(self):
        for _ in range(40):
            self.ss.kaydet("DiziPal", True)
        self.assertLessEqual(self.ss.puanlar()["DiziPal"], self.ss.TAVAN)

    def test_yari_omur_soldurur(self):
        import time as _t

        simdi = _t.time()
        self.ss.kaydet("DiziPal", True, simdi=simdi)
        taze = self.ss.puanlar(simdi=simdi)["DiziPal"]
        eski = self.ss.puanlar(simdi=simdi + self.ss.YARI_OMUR_SN)["DiziPal"]
        self.assertAlmostEqual(eski, taze / 2, places=1)

    def test_hicbir_saglayici_yasaklanmaz(self):
        for _ in range(40):
            self.ss.kaydet("DiziPal", False)
        # Puanı dipte ama listede duruyor: kaynak kaybetmek yok.
        self.assertIn("DiziPal", self.ss.sirala(["DiziPal", "DiziMom"]))

    def test_ozet_okunur_dokum_verir(self):
        self.ss.kaydet("DiziPal", True)
        self.ss.kaydet("DiziPal", False)
        satir = next(s for s in self.ss.ozet() if s["plugin"] == "DiziPal")
        self.assertEqual(satir["basari"], 1)
        self.assertEqual(satir["hata"], 1)


if __name__ == "__main__":
    unittest.main()


class PuanliSiraEngineTest(unittest.TestCase):
    """Puanlı sıra gerçekten engine'e gidiyor mu — sözleşmenin sunucu ucu."""

    def setUp(self) -> None:
        self._dir = tempfile.TemporaryDirectory()
        os.environ["SOURCE_SCORE_PATH"] = str(Path(self._dir.name) / "score.json")

    def tearDown(self) -> None:
        self._dir.cleanup()
        os.environ.pop("SOURCE_SCORE_PATH", None)

    def test_veri_yokken_order_gonderilmez(self):
        """Puan da yıldız da yoksa engine kendi listesine düşer."""
        from unittest import mock

        import Public.API.v1.Routers.prefs as prefs_mod
        from Public.API.v1.Routers.resolve_sources import _puanli_sira

        with mock.patch.object(prefs_mod, "_oku", return_value={}):
            self.assertEqual(_puanli_sira(), "")

    def test_puan_varsa_order_dolu_gelir(self):
        from unittest import mock

        import Public.API.v1.Routers.prefs as prefs_mod
        from Public.API.v1.Libs import source_score
        from Public.API.v1.Routers.resolve_sources import _puanli_sira

        source_score.kaydet("DiziBox", True)
        source_score.kaydet("DiziPal", False)
        with mock.patch.object(prefs_mod, "_oku", return_value={}):
            sira = _puanli_sira().split(",")
        self.assertEqual(sira[0], "DiziBox")
        self.assertEqual(sira[-1], "DiziPal")
