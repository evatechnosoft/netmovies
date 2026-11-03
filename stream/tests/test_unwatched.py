# İzlemediklerim: kayıttan SONRAKİ bölümler listelenir, yayınlanmamışlar girmez.

import datetime
import sys
import unittest
from pathlib import Path

STREAM_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(STREAM_ROOT))

from Public.API.v1.Routers import unwatched as uw


class RefCozTest(unittest.TestCase):
    def test_tv_ve_web_bicimi(self) -> None:
        self.assertEqual((4, 8), uw.ref_coz("S4B8"))   # TV
        self.assertEqual((4, 8), uw.ref_coz("S4 E8"))  # web (central-progress.js)

    def test_indeks_kaydi_bolum_sayilmaz(self) -> None:
        # Eski kayıtlar liste indeksi tutuyordu; "123" bölüm numarası değildir.
        self.assertIsNone(uw.ref_coz("123"))
        self.assertIsNone(uw.ref_coz(""))
        self.assertIsNone(uw.ref_coz(None))


class SiralamaTest(unittest.TestCase):
    def test_en_yeni_ustte(self) -> None:
        satirlar = [
            {"tarih": "2026-09-06", "baslik": "B"},
            {"tarih": "2026-09-15", "baslik": "A"},
        ]
        satirlar.sort(key=lambda s: (s["tarih"], s["baslik"]), reverse=True)
        self.assertEqual(["2026-09-15", "2026-09-06"], [s["tarih"] for s in satirlar])


class PencereTest(unittest.TestCase):
    def test_geri_pencere_bir_aydan_uzun(self) -> None:
        # Ayın 1'inde dünkü bölüm listeden düşmesin diye 30 değil 45 gün.
        self.assertGreaterEqual(uw._GERI_GUN, 32)
        bugun = datetime.date(2026, 9, 18)
        self.assertLessEqual(bugun - datetime.timedelta(days=uw._GERI_GUN), datetime.date(2026, 8, 17))


class MutlaktanSezonaTest(unittest.TestCase):
    def test_mutlak_numara_gercek_sezona_iner(self) -> None:
        self.assertEqual((7, 7), uw._mutlaktan_sezona((1, 191), [14, 34, 31, 32, 37, 36, 10]))
        self.assertEqual((2, 4), uw._mutlaktan_sezona((1, 20), [16, 5]))

    def test_ilk_sezon_icindeyse_dokunulmaz(self) -> None:
        self.assertEqual((1, 21), uw._mutlaktan_sezona((1, 21), [25]))
        self.assertEqual((3, 2), uw._mutlaktan_sezona((3, 2), [10, 10, 10]))


class TekSatirTest(unittest.TestCase):
    def test_dizi_basina_sirada_ki_bolum_ve_kalan(self) -> None:
        satirlar = [{"bolum": f"S7B{n}", "baslik": "Teşkilat"} for n in range(1, 8)]
        tek = uw._tek_satir(satirlar)
        self.assertEqual(1, len(tek))
        self.assertEqual("S7B1", tek[0]["bolum"])
        self.assertEqual(7, tek[0]["kalan"])
        self.assertEqual([], uw._tek_satir([]))


if __name__ == "__main__":
    unittest.main()
