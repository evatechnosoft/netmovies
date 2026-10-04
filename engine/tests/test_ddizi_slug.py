# DDizi bölüm listesi filtresi. Dizi sayfasının kenar çubuğunda BAŞKA dizilerin
# bölümleri de aynı `/izle/<id>/...-<n>-bolum-...htm` kalıbında duruyor: Mercan
# Köşk (1 bölüm yayınlanmış) listesine "daha-17-16-bolum" ve
# "masterchef-2026-88-bolum" giriyor, kullanıcı 16 ve 88 numaralı bölümler
# görüyordu. Ayrım slug'dan yapılır.

import unittest

from Plugins.DDizi import _HLS, _YOUTUBE, _slug


class DDiziSlugTest(unittest.TestCase):
    def test_dizi_ve_bolum_ayni_sluga_iner(self):
        self.assertEqual(_slug("https://www.ddizi.im/diziler/2178/mercan-kosk-son-bolum-izle"), "mercan-kosk")
        self.assertEqual(_slug("https://www.ddizi.im/izle/91664/mercan-kosk-1-bolum-izle-hd1.htm"), "mercan-kosk")

    def test_dizi_adresindeki_bolum_numarasi_atilir(self):
        # Dizi adresinde numara slug'ın İÇİNDE kalıyor, bölüm adresinde kalmıyor.
        # Atılmazsa hiçbir bölüm eşleşmiyor ve filtre sessizce devre dışı kalıyordu.
        self.assertEqual(_slug("https://www.ddizi.im/diziler/1838/gonul-dagi-171-son-bolum-izle"), "gonul-dagi")
        self.assertEqual(_slug("https://www.ddizi.im/izle/91700/gonul-dagi-213-bolum-izle-hd6.htm"), "gonul-dagi")
        self.assertEqual(_slug("https://www.ddizi.im/izle/91701/gonul-dagi-220-bolum-izle-sezon-finali-hd4.htm"), "gonul-dagi")

    def test_yabanci_bolumler_ayri_sluga_duser(self):
        yabanci = {
            _slug("https://www.ddizi.im/izle/91706/daha-17-16-bolum-izle-hd2.htm"),
            _slug("https://www.ddizi.im/izle/91705/masterchef-2026-88-bolum-izle-13-eylul.htm"),
        }
        self.assertNotIn("mercan-kosk", yabanci)
        self.assertNotIn("gonul-dagi", yabanci)
        self.assertEqual(len(yabanci), 2)


class DDiziOynaticiTest(unittest.TestCase):
    def test_telif_sayfasi_youtube_kimligi(self):
        # A.B.İ. 16-18: telif sayfası resmi atv YouTube videosuna bağlı; eski kalıp
        # yalnız `youtube.php?id=` arıyordu, bölüm "kaynak yok" düşüp 360p'ye iniyordu.
        html = '<iframe src="/player/telif/index.php?id=https://www.youtube.com/watch?v=GkGzfQwJsBc">'
        self.assertEqual(_YOUTUBE.search(html).group(1), "GkGzfQwJsBc")
        self.assertEqual(_YOUTUBE.search("ddizi.re/player/youtube.php?id=abc123XYZ").group(1), "abc123XYZ")

    def test_uzantisiz_oynatici_dosyasi(self):
        # A.B.İ. 21: streambox `master.txt` veriyor; `.m3u8` şartı kaynağı düşürüyordu.
        js = 'sources: [{file:"https://streambox.xyz/hls/f49c/master.txt?s=1&d="}]'
        self.assertTrue(_HLS.search(js).group(1).startswith("https://streambox.xyz/"))


if __name__ == "__main__":
    unittest.main()
