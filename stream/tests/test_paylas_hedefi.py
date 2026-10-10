import unittest

from Public.API.v1.Libs.paylas_hedefi import eklenti_bul, oynatma_komutu, youtube_izleme_adresi

EKLENTILER = [
    {"name": "DiziPal", "main_url": "https://dizipal2225.com"},
    {"name": "DDizi", "main_url": "https://www.ddizi.tel"},
    {"name": "YouTube", "main_url": "https://www.youtube.com"},
    {"name": "M3U Listelerim", "main_url": "m3u://local"},
]


class PaylasHedefiTest(unittest.TestCase):
    def test_tasinmis_domain_markadan_eslesir(self):
        self.assertEqual("DiziPal", eklenti_bul("https://dizipal2230.com/dizi/daha-17", EKLENTILER))
        self.assertEqual("DDizi", eklenti_bul("https://www.ddizi.im/diziler/2167/x", EKLENTILER))

    def test_youtube_video_oynatici_kanal_sayfa(self):
        self.assertEqual("YouTube", eklenti_bul("https://youtu.be/xSPgFH5yBRE?si=abc", EKLENTILER))
        self.assertEqual("YouTube", eklenti_bul("https://m.youtube.com/watch?v=xSPgFH5yBRE", EKLENTILER))
        self.assertIsNone(eklenti_bul("https://www.youtube.com/@atvturkiye", EKLENTILER))

    def test_youtube_adres_tek_bicim(self):
        self.assertEqual("https://www.youtube.com/watch?v=abc", youtube_izleme_adresi("https://youtu.be/abc?si=1"))
        self.assertEqual("https://www.youtube.com/watch?v=abc", youtube_izleme_adresi("https://youtube.com/shorts/abc"))

    def test_yabanci_site_sayfa_olarak_kalir(self):
        self.assertIsNone(eklenti_bul("https://www.hurriyet.com.tr/gundem/x", EKLENTILER))
        self.assertIsNone(oynatma_komutu("https://example.com/a", EKLENTILER))

    def test_komut_bicimi(self):
        cmd = oynatma_komutu("https://youtu.be/abc", EKLENTILER)
        self.assertEqual({"type": "play", "plugin": "YouTube", "url": "https://www.youtube.com/watch?v=abc",
                          "title": "", "poster": "", "episode": -1}, cmd)


if __name__ == "__main__":
    unittest.main()
