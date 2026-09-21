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

    def test_sayi_ayirt_edici(self):
        # "17" iki harf sinirina takilip elenince geriye {daha} kaliyor ve
        # alakasiz film eslesme sayiliyordu.
        self.assertFalse(baslik_uyusuyor("Daha 17", "Hızlı ve Öfkeli 2 Daha Hızlı Daha Öfkeli"))
        self.assertTrue(baslik_uyusuyor("Daha 17", "Daha 17 son bölüm"))

    def test_noktalama_ve_site_eki_tolere_edilir(self):
        self.assertTrue(baslik_uyusuyor("Örümcek Adam: Yepyeni Bir Gün", "Örümcek Adam Yepyeni Bir Gün izle"))
        self.assertTrue(baslik_uyusuyor("Inception", "Başlangıç - Inception Türkçe Dublaj"))

    def test_dizi_ararken_ayni_adi_tasiyan_film_elenir(self):
        # Dean: "reacher 4x8 baska bir film aciyor". Hedef tek kelime olunca
        # kapsama sinavi yetmiyordu; adaydaki FAZLA kelimeler baska yapim demek.
        self.assertFalse(baslik_uyusuyor("Reacher", "Jack Reacher: Asla Geri Dönme"))
        self.assertFalse(baslik_uyusuyor("Reacher", "Jack Reacher: Asla Geri Dönme - Jack Reacher: Never Go Back"))
        self.assertFalse(baslik_uyusuyor("Şeytan Çocuk", "Şeytan Çocuk Karanlık Doğuş"))

    def test_sezon_bolum_eki_yapimi_degistirmez(self):
        self.assertTrue(baslik_uyusuyor("Reacher", "Reacher 4. Sezon"))
        self.assertTrue(baslik_uyusuyor("Reacher", "Reacher 4. Sezon 8. Bölüm izle"))
        self.assertTrue(baslik_uyusuyor("Reacher", "Reacher (2022)"))

    def test_iki_dilli_baslik_parcalanir(self):
        # Saglayici Turkce ve orijinal adi tek satirda veriyor.
        self.assertTrue(baslik_uyusuyor("Dark Matter", "Karanlık Madde - Dark Matter"))

    def test_bos_aday(self):
        self.assertFalse(baslik_uyusuyor("Inception", ""))
        self.assertFalse(baslik_uyusuyor("", "Inception"))
