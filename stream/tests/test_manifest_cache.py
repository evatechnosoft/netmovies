"""Son iyi manifest önbelleği.

Tek kullanımlık oynatma adresleri (`.../l.php?v=<jeton>`) ilk istekte 200,
ikincide 403 dönüyor ve film ortasında "kaynak bulunamadı"ya düşüyordu.
Önbellek yalnız hata yolunda devreye girer; bu testler sözleşmeyi tutar.
"""

import time
import unittest

from Public.Proxy.Libs import manifest_cache


class ManifestCacheTest(unittest.TestCase):
    def setUp(self):
        manifest_cache.temizle()

    def test_yazilan_okunur(self):
        manifest_cache.yaz("https://a/x.m3u8", b"#EXTM3U\n", "application/vnd.apple.mpegurl")
        sonuc = manifest_cache.oku("https://a/x.m3u8")
        self.assertIsNotNone(sonuc)
        self.assertEqual(sonuc[0], b"#EXTM3U\n")
        self.assertEqual(sonuc[1], "application/vnd.apple.mpegurl")

    def test_bos_govde_yazilmaz(self):
        # Boş yanıtı saklamak, 403 yolunda boş manifest dönmek demek olurdu.
        manifest_cache.yaz("https://a/bos.m3u8", b"", "application/vnd.apple.mpegurl")
        self.assertIsNone(manifest_cache.oku("https://a/bos.m3u8"))

    def test_bilinmeyen_adres_none(self):
        self.assertIsNone(manifest_cache.oku("https://a/yok.m3u8"))

    def test_omru_dolan_dusur(self):
        manifest_cache.yaz("https://a/eski.m3u8", b"#EXTM3U\n", "x")
        eski = manifest_cache._kayitlar["https://a/eski.m3u8"]
        manifest_cache._kayitlar["https://a/eski.m3u8"] = (
            eski[0], eski[1], time.time() - manifest_cache._OMUR_SANIYE - 1,
        )
        self.assertIsNone(manifest_cache.oku("https://a/eski.m3u8"))
        self.assertEqual(manifest_cache.sayi(), 0)

    def test_tavan_asilinca_en_eski_duser(self):
        for i in range(manifest_cache._TAVAN + 5):
            manifest_cache.yaz(f"https://a/{i}.m3u8", b"#EXTM3U\n", "x")
        self.assertEqual(manifest_cache.sayi(), manifest_cache._TAVAN)
        self.assertIsNone(manifest_cache.oku("https://a/0.m3u8"))
        self.assertIsNotNone(manifest_cache.oku(f"https://a/{manifest_cache._TAVAN + 4}.m3u8"))


if __name__ == "__main__":
    unittest.main()
