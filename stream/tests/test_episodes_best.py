# En zengin bölüm listesi: hangi sağlayıcının listesi kazanır.

import sys
import unittest
from pathlib import Path

STREAM_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(STREAM_ROOT))

from Public.API.v1.Routers.episodes_best import _cift_sayisi, ayni_yapim, daha_zengin


class CiftSayisiTest(unittest.TestCase):
    def test_benzersiz_bolum_sayilir(self) -> None:
        # Aynı bölüm iki kez gelebiliyor (kenar çubuğu sızıntısı): ham uzunluk yanıltır.
        liste = [
            {"season": 1, "episode": 1},
            {"season": 1, "episode": 1},
            {"season": 1, "episode": 2},
        ]
        self.assertEqual(2, _cift_sayisi(liste))

    def test_numarasiz_bolum_sayilmaz(self) -> None:
        self.assertEqual(1, _cift_sayisi([{"season": 1, "episode": 3}, {"season": 1}]))
        self.assertEqual(0, _cift_sayisi([]))
        self.assertEqual(0, _cift_sayisi(None))

    def test_gercek_olcum(self) -> None:
        # 18 Eylül ölçümü: HDFilmCehennemi 7, DiziMom 20 — zengin olan kazanmalı.
        hdfc = [{"season": 1, "episode": i} for i in range(1, 7)] + [{"season": 2, "episode": 1}]
        mom  = [{"season": s, "episode": e} for s in (1, 2) for e in range(1, 11)]
        self.assertEqual(7, _cift_sayisi(hdfc))
        self.assertEqual(20, _cift_sayisi(mom))
        self.assertTrue(daha_zengin(_cift_sayisi(mom), _cift_sayisi(hdfc), 0.0, 0.0))


class DahaZenginTest(unittest.TestCase):
    def test_cok_bolum_kazanir(self) -> None:
        self.assertTrue(daha_zengin(32, 28, 0.0, 500.0))   # puan düşük olsa da
        self.assertFalse(daha_zengin(28, 32, 500.0, 0.0))

    def test_esitlikte_puan_belirler(self) -> None:
        self.assertTrue(daha_zengin(20, 20, 100.0, 50.0))
        self.assertFalse(daha_zengin(20, 20, 50.0, 100.0))

    def test_bos_liste_kazanmaz(self) -> None:
        self.assertFalse(daha_zengin(0, 0, 500.0, 0.0))


def _liste(*boylar: int) -> list:
    return [{"season": s, "episode": b} for s, n in enumerate(boylar, 1) for b in range(1, n + 1)]


class AyniYapimTest(unittest.TestCase):
    def test_ayni_adli_eski_yapim_elenir(self) -> None:
        # Dark Matter 2024 (9+5) karşısında 2015 yapımı (13+13+13)
        self.assertFalse(ayni_yapim(_liste(13, 13, 13), _liste(9, 5)))

    def test_son_sezonu_ilerde_olan_kabul(self) -> None:
        # Reacher: taban S4B4, aday S4B8
        self.assertTrue(ayni_yapim(_liste(8, 8, 8, 8), _liste(8, 8, 8, 4)))

    def test_tek_sezonlu_tabanda_kisit_yok(self) -> None:
        self.assertTrue(ayni_yapim(_liste(10, 6), _liste(1)))
        self.assertTrue(ayni_yapim(_liste(10), []))


if __name__ == "__main__":
    unittest.main()
