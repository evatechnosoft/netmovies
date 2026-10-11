# Çocuk modu: yasaklı kelime süzgeci, yalnız izinli raflar, kanal girdisi.
import asyncio
import sys
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from Public.Home.Libs import cocuk

KANAL = "UCrFf5dtMe6M1XJHqbKJ4X6Q"
RSS = """<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns="http://www.w3.org/2005/Atom">
 <entry><yt:videoId>kisa1</yt:videoId><title>Sessiz sinema #shorts</title>
  <link rel="alternate" href="https://www.youtube.com/shorts/kisa1"/></entry>
 <entry><yt:videoId>uzun1</yt:videoId><title>Rafadan Tayfa 12. Bölüm</title>
  <link rel="alternate" href="https://www.youtube.com/watch?v=uzun1"/></entry>
</feed>"""


class SuzgecTest(unittest.TestCase):
    def test_yasakli_kelime_gizlenir_turkce_buyuk_harf(self):
        videolar = [{"title": "KORKU EVİ"}, {"title": "Kırmızı Balık"}, {"title": "Pepee Şarkı"}]
        self.assertEqual(["Pepee Şarkı"], [v["title"] for v in cocuk.suz(videolar, ["korku", " KIRMIZI "])])

    def test_bos_kelime_hicbir_seyi_gizlemez(self):
        self.assertFalse(cocuk.yasakli_mi("Pepee", ["", "  "]))
        self.assertFalse(cocuk.yasakli_mi("Pepee", []))

    def test_rss_shorts_atlanir(self):
        self.assertEqual(["uzun1"], [v["id"] for v in cocuk.rss_videolari(RSS)])


class RaflarTest(unittest.TestCase):
    def test_yalniz_izinli_kanal_ve_onayli_icerik(self):
        cfg = {
            "cocuk_kanallar": [
                {"id": KANAL, "ad": "TRT Çocuk", "yasakli": ["kavga"]},
                {"id": "kotu-kimlik", "ad": "Sahte"},   # geçersiz kimlik: raf olmaz
            ],
            "cocuk_icerik": [{"plugin": "DiziPal", "title": "Rafadan Tayfa", "url": "https%3A%2F%2Fx", "tur": "serie"}],
            "featured": [{"title": "Yetişkin filmi"}],   # çocuk ekranına sızmamalı
        }
        videolar = [{"id": "a", "title": "Kavga Bölümü", "url": "https://www.youtube.com/watch?v=a", "poster": ""},
                    {"id": "b", "title": "Oyun Zamanı", "url": "https://www.youtube.com/watch?v=b", "poster": ""}]
        sorulan = []

        async def sahte(kimlik):
            sorulan.append(kimlik)
            return videolar

        with mock.patch.object(cocuk, "kanal_videolari", sahte):
            raflar = asyncio.run(cocuk.raflar(cfg))
        self.assertEqual([KANAL], sorulan)
        self.assertEqual(["TRT Çocuk", "Çizgi filmler"], [r["ad"] for r in raflar])
        self.assertEqual(["Oyun Zamanı"], [k["title"] for k in raflar[0]["kartlar"]])
        self.assertEqual("youtube", raflar[0]["kartlar"][0]["plugin"])
        self.assertEqual("serie", raflar[1]["kartlar"][0]["tur_sat"])

    def test_bos_ayar_bos_ekran(self):
        self.assertEqual([], asyncio.run(cocuk.raflar({})))


class KanalGirdisiTest(unittest.TestCase):
    def test_bicimler(self):
        self.assertEqual(f"https://www.youtube.com/channel/{KANAL}", cocuk.kanal_adresi(KANAL))
        self.assertEqual("https://www.youtube.com/@TRTCocuk", cocuk.kanal_adresi("@TRTCocuk"))
        self.assertEqual("https://www.youtube.com/@TRTCocuk", cocuk.kanal_adresi("https://m.youtube.com/@TRTCocuk/videos"))
        self.assertEqual("https://www.youtube.com/c/trtcocuk", cocuk.kanal_adresi("youtube.com/c/trtcocuk"))

    def test_yabanci_konak_ve_video_reddedilir(self):
        self.assertIsNone(cocuk.kanal_adresi("https://evil.com/@x"))
        self.assertIsNone(cocuk.kanal_adresi("https://youtube.com.evil.com/@x"))
        self.assertIsNone(cocuk.kanal_adresi("https://www.youtube.com/watch?v=abc"))

    def test_sayfadan_ad_avatar(self):
        sayfa = (f'<link rel="canonical" href="https://www.youtube.com/channel/{KANAL}">'
                 '<meta property="og:title" content="TRT &amp; Çocuk"><meta property="og:image" content="https://yt3/a.jpg">')
        self.assertEqual({"id": KANAL, "ad": "TRT & Çocuk", "avatar": "https://yt3/a.jpg"}, cocuk.kanal_sayfasi_coz(sayfa))


if __name__ == "__main__":
    unittest.main()
