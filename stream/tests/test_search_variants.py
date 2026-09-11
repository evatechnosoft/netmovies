# Arama varyantları — kaynak siteler tam ifade eşleştirdiği için tek sorgu yetmiyor.
# "the odyssey" hiçbir kaynakta sonuç vermezken "odyssey" iki kaynakta buluyor.

import unittest

from Public.API.v1.Routers.search_all import _varyantlar


class SearchVariantsTest(unittest.TestCase):
    def test_artikel_dusurulur(self):
        self.assertEqual(_varyantlar("The Odyssey"), ["The Odyssey", "Odyssey"])

    def test_alt_baslik_kirpilir(self):
        varyantlar = _varyantlar("Örümcek Adam: Yepyeni Bir Gün")
        self.assertEqual(varyantlar[0], "Örümcek Adam: Yepyeni Bir Gün")
        self.assertIn("Örümcek Adam", varyantlar)

    def test_tek_kelime_tek_varyant(self):
        self.assertEqual(_varyantlar("Inception"), ["Inception"])

    def test_varyantlar_tekil(self):
        varyantlar = _varyantlar("The Lord of the Rings")
        self.assertEqual(len(varyantlar), len(set(varyantlar)))
        # Ortadaki "the" korunur, yalnız baştaki artikel düşer.
        self.assertIn("Lord of the Rings", varyantlar)


if __name__ == "__main__":
    unittest.main()
