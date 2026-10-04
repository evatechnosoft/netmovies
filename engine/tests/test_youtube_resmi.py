# YouTube eklentisi: yalnız resmi liste, bölüm numarası başlıktan.
import unittest

from Plugins.YouTube import bolum_numarasi, resmi_mi


class YouTubeResmiTest(unittest.TestCase):
    def test_resmi_kanal(self):
        self.assertTrue(resmi_mi("A.B.İ.", "A.B.İ.", "Bölümler"))
        self.assertTrue(resmi_mi("Gönül Dağı", "TRT 1", "Gönül Dağı Tüm Bölümler"))
        # Hayran yüklemesi elenir.
        self.assertFalse(resmi_mi("A.B.İ.", "Farid Ragimhov", "A.B.İ Tüm Bölümler"))
        self.assertFalse(resmi_mi("A.B.İ.", "Serial kino Videoları", "A.B.İ Bölümler"))

    def test_bolum_numarasi(self):
        self.assertEqual(bolum_numarasi("A.B.İ. 16. Bölüm @atvturkiye"), (1, 16))
        self.assertEqual(bolum_numarasi("A.B.İ. Episode 20"), (1, 20))
        self.assertEqual(bolum_numarasi("Kızılcık Şerbeti 3. Sezon 12. Bölüm"), (3, 12))
        self.assertIsNone(bolum_numarasi("A.B.İ. 2. Fragman"))


if __name__ == "__main__":
    unittest.main()
