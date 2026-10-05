# YouTube eklentisi: yalnız resmi liste, bölüm numarası başlıktan.
import unittest

from Plugins.YouTube import bolum_numarasi, kanal_dizinin_mi, resmi_mi, video_mu


class YouTubeResmiTest(unittest.TestCase):
    def test_resmi_kanal(self):
        self.assertTrue(resmi_mi("A.B.İ.", "A.B.İ.", "Bölümler"))
        self.assertTrue(resmi_mi("Gönül Dağı", "TRT 1", "Gönül Dağı Tüm Bölümler"))
        # Hayran yüklemesi elenir.
        self.assertFalse(resmi_mi("A.B.İ.", "Farid Ragimhov", "A.B.İ Tüm Bölümler"))
        self.assertFalse(resmi_mi("A.B.İ.", "Serial kino Videoları", "A.B.İ Bölümler"))

    def test_kanal_dizinin_mi(self):
        # Sitenin "Abi" yazdığı dizi YouTube'da "A.B.İ." kanalı: kart adı kanaldan gelir.
        self.assertTrue(kanal_dizinin_mi("Abi", "A.B.İ."))
        self.assertTrue(kanal_dizinin_mi("A.B.İ.", "A.B.İ."))
        self.assertFalse(kanal_dizinin_mi("Gönül Dağı", "TRT 1"))
        self.assertFalse(kanal_dizinin_mi("", "A.B.İ."))

    def test_bolum_numarasi(self):
        self.assertEqual(bolum_numarasi("A.B.İ. 16. Bölüm @atvturkiye"), (1, 16))
        self.assertEqual(bolum_numarasi("A.B.İ. Episode 20"), (1, 20))
        self.assertEqual(bolum_numarasi("Kızılcık Şerbeti 3. Sezon 12. Bölüm"), (3, 12))
        self.assertIsNone(bolum_numarasi("A.B.İ. 2. Fragman"))
        # Kaos Show: Türkçe ve İngilizce başlıklar karışık.
        self.assertEqual(bolum_numarasi("Hayrettin ile Kaos Show - 2.Sezon 17.Bölüm | Kaos Allstar"), (2, 17))
        self.assertEqual(bolum_numarasi("Chaos Show with Hayrettin - Season 2 Episode 15 | X"), (2, 15))
        self.assertEqual(bolum_numarasi("Hayrettin and Chaos Show - Episode 1 | Music Festival"), (1, 1))

    def test_video_mu(self):
        # Hızlı yolun arama adresi ve liste adresi load_links'e yt-dlp koşturmaz.
        self.assertTrue(video_mu("https://www.youtube.com/watch?v=OMHZ22RfJ3o"))
        self.assertTrue(video_mu("https://youtu.be/OMHZ22RfJ3o"))
        self.assertFalse(video_mu("https://www.youtube.com/results?search_query=A.B.%C4%B0."))
        self.assertFalse(video_mu("https://www.youtube.com/playlist?list=PL8pI2u3SrDIbaS04otJLPEnHduFSA0mPs"))
        self.assertFalse(video_mu(""))


if __name__ == "__main__":
    unittest.main()
