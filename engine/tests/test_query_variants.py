# Yedek zincirin arama varyantları. Tek sorguyla arayan zincir "The Odyssey" ve
# "Örümcek Adam: Yepyeni Bir Gün" için dokuz sağlayıcıdan da "sonuç yok" alıyordu.

import unittest

from Public.API.v1.Libs.arama_varyant import query_variants


class QueryVariantsTest(unittest.TestCase):
    def test_artikel_dusurulur(self):
        self.assertEqual(query_variants("The Odyssey"), ["the odyssey", "odyssey"])

    def test_alt_baslik_kirpilir(self):
        varyantlar = query_variants("Örümcek Adam: Yepyeni Bir Gün")
        self.assertIn("örümcek adam", varyantlar)

    def test_site_gurultusu_temizlenir(self):
        # clean_title "izle"/"türkçe dublaj" gibi ekleri zaten atıyor.
        self.assertEqual(query_variants("Inception izle Türkçe Dublaj")[0], "inception")

    def test_bos_baslik(self):
        self.assertEqual(query_variants(""), [])
        self.assertEqual(query_variants(None), [])

    def test_varyantlar_tekil(self):
        varyantlar = query_variants("The Lord of the Rings")
        self.assertEqual(len(varyantlar), len(set(varyantlar)))


if __name__ == "__main__":
    unittest.main()
