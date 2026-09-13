# DDizi bölüm listesi filtresi. Dizi sayfasının kenar çubuğunda BAŞKA dizilerin
# bölümleri de aynı `/izle/<id>/...-<n>-bolum-...htm` kalıbında duruyor: Mercan
# Köşk (1 bölüm yayınlanmış) listesine "daha-17-16-bolum" ve
# "masterchef-2026-88-bolum" giriyor, kullanıcı 16 ve 88 numaralı bölümler
# görüyordu. Ayrım slug'dan yapılır.

import unittest

from Plugins.DDizi import _slug


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


if __name__ == "__main__":
    unittest.main()
