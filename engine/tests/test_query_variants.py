# Yedek zincirin arama varyantları. Tek sorguyla arayan zincir "The Odyssey" ve
# "Örümcek Adam: Yepyeni Bir Gün" için dokuz sağlayıcıdan da "sonuç yok" alıyordu.

import unittest

from Public.API.v1.Libs.arama_varyant import baslik_uyusuyor, query_variants


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


class BaslikUyusuyorTest(unittest.TestCase):
    def test_farkli_yapim_elenir(self):
        # "örümcek adam" varyantıyla bulunan çizgi film kabul edilmemeli.
        self.assertFalse(baslik_uyusuyor("Örümcek Adam: Yepyeni Bir Gün", "Örümcek Adam: Örümcek Evreninde"))
        self.assertFalse(baslik_uyusuyor("The Odyssey", "2001: A Space Odyssey"))
        self.assertFalse(baslik_uyusuyor("The Odyssey", "Doctor Odyssey"))

    def test_noktalama_ve_site_eki_tolere_edilir(self):
        self.assertTrue(baslik_uyusuyor("Örümcek Adam: Yepyeni Bir Gün", "Örümcek Adam Yepyeni Bir Gün izle"))
        self.assertTrue(baslik_uyusuyor("Inception", "Başlangıç - Inception Türkçe Dublaj"))

    def test_bos_aday(self):
        self.assertFalse(baslik_uyusuyor("Inception", ""))
        self.assertFalse(baslik_uyusuyor("", "Inception"))
