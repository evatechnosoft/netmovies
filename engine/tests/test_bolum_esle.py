# Bölüm seçimi numarayla: sağlayıcı listeleri farklı bölümden başlıyor, sıra
# numarası başka sağlayıcıda BAŞKA bölümü açıyordu ("3. bölüm dedik, 1 geldi").

import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from Public.API.v1.Libs.bolum_esle import basliktan_bolum, bolum_sirasi, int_or_none


def ep(sezon, bolum):
    return {"season": sezon, "episode": bolum}


class BolumEsleTest(unittest.TestCase):
    def test_liste_farkli_bolumden_basliyorsa_numara_kazanir(self):
        # DDizi sayfalı: liste 3. bölümden başlıyor. İstemci sırası 2 = 3. bölüm.
        liste = [ep(1, 3), ep(1, 4), ep(1, 5)]
        self.assertEqual(bolum_sirasi(liste, 2, bolum_no=3), 0)

    def test_cok_sezonda_sezon_da_eslesir(self):
        liste = [ep(1, 1), ep(1, 2), ep(1, 3), ep(2, 1), ep(2, 2), ep(2, 3)]
        self.assertEqual(bolum_sirasi(liste, 0, bolum_no=3, sezon_no=2), 5)

    def test_numarali_listede_olmayan_bolum_bulunmaz(self):
        # Son bölümden sonra yayınlanmamış sezon: sıraya düşüp 1. bölümü açıyordu.
        liste = [ep(1, 1), ep(1, 2)]
        self.assertIsNone(bolum_sirasi(liste, 1, bolum_no=9))
        self.assertIsNone(bolum_sirasi(liste, 0, bolum_no=1, sezon_no=2))

    def test_numarasiz_listede_siraya_duser(self):
        liste = [ep(1, None), ep(1, None)]
        self.assertEqual(bolum_sirasi(liste, 1, bolum_no=9), 1)
        self.assertIsNone(bolum_sirasi(liste, 7, bolum_no=9))

    def test_nesne_alanlari_da_okunur(self):
        class Ep:
            def __init__(self, s, e): self.season, self.episode = s, e
        self.assertEqual(bolum_sirasi([Ep(1, 1), Ep(1, 2)], 0, bolum_no=2), 1)

    def test_sorgu_parametresi(self):
        self.assertEqual(int_or_none("0"), 0)
        self.assertEqual(int_or_none(" 3 "), 3)
        self.assertIsNone(int_or_none(""))
        self.assertIsNone(int_or_none(None))
        self.assertIsNone(int_or_none("-1"))


class BasliktanBolumTest(unittest.TestCase):
    def test_bolum_sayfasi_karti(self):
        self.assertEqual(basliktan_bolum("Haysiyet 3.Bölüm"), ("Haysiyet", None, 3))
        self.assertEqual(basliktan_bolum("Kızılcık Şerbeti 2. Sezon 15. Bölüm"), ("Kızılcık Şerbeti", 2, 15))
        self.assertEqual(basliktan_bolum("Gonul Dagi 213 Bolum"), ("Gonul Dagi", None, 213))

    def test_eksiz_baslik_degismez(self):
        self.assertEqual(basliktan_bolum("Haysiyet"), ("Haysiyet", None, None))
        self.assertEqual(basliktan_bolum("Reacher"), ("Reacher", None, None))
        # Başlığın tamamı ek ise ad kalmaz: dokunulmaz.
        self.assertEqual(basliktan_bolum("3. Bölüm"), ("3. Bölüm", None, None))


class DDiziOynatTest(unittest.TestCase):
    def test_youtube_disi_oynatici_hls_verir(self):
        from Plugins.DDizi import _HLS, _OYNAT
        sayfa = '<iframe title="video" src="/player/oynat/75c0e73fe027a027fcf170fadeea223a" width="100%">'
        self.assertEqual(_OYNAT.search(sayfa).group(1), "/player/oynat/75c0e73fe027a027fcf170fadeea223a")
        oynat = 'sources: [{file:"https://video.twimg.com/a/pl/x.m3u8?tag=29", label: "auto"'
        self.assertEqual(_HLS.search(oynat).group(1), "https://video.twimg.com/a/pl/x.m3u8?tag=29")


if __name__ == "__main__":
    unittest.main()
