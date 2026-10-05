# YouTube araması genel aramanın başında; aynı liste iki kez çıkmaz.
import unittest

from Public.API.v1.Routers.search_all import youtube_kartlari, youtube_one


class YouTubeAramaTest(unittest.TestCase):
    def test_kart_adresi_kodlu_ve_kanal_kategoride(self):
        kart = youtube_kartlari([{"title": "Kaos Show 17", "url": "https://www.youtube.com/watch?v=x1", "channel": "Hayrettin"}])[0]
        self.assertEqual(kart["plugin"], "YouTube")
        self.assertEqual(kart["url"], "https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3Dx1")
        self.assertEqual(kart["category"], "YouTube · Hayrettin")

    def test_bozuk_satir_atlanir(self):
        self.assertEqual(youtube_kartlari([{"title": "adres yok"}, "metin", None]), [])

    def test_youtube_basta_tekrar_yok(self):
        yt = youtube_kartlari([{"title": "A.B.İ.", "url": "https://www.youtube.com/playlist?list=PL1"}])
        ogeler = [{"plugin": "DDizi", "url": "d1"}, {"plugin": "YouTube", "url": yt[0]["url"]}]
        sonuc = youtube_one(yt, ogeler)
        self.assertEqual([o["plugin"] for o in sonuc], ["YouTube", "DDizi"])


if __name__ == "__main__":
    unittest.main()
