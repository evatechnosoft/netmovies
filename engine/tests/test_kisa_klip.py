import unittest

from Public.API.v1.Libs.kisa_klip import AV_KAYMA_ESIK, av_kayma_orani, hls_suresi, kisa_klip_mi


class KisaKlipTest(unittest.TestCase):
    def test_googlevideo_clip_is_short(self):
        self.assertTrue(kisa_klip_mi("https://r.googlevideo.com/videoplayback?dur=55.296&itag=18"))

    def test_twimg_konagi_tek_basina_klip_degil(self):
        # Tuzlu Kahve 3-4 ve Haysiyet 3 tam bölümleri de twimg'de (8500 sn); karar süreyle.
        self.assertFalse(kisa_klip_mi("https://video.twimg.com/amplify_video/2092681508070866944/pl/k.m3u8?tag=29"))

    def test_hls_suresi_varyanttan(self):
        master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\n/a/v.m3u8\n"
        varyant = "#EXTM3U\n#EXTINF:10.0,\ns1.ts\n#EXTINF:21.3,\ns2.ts\n"
        self.assertAlmostEqual(hls_suresi(master, varyant), 31.3)
        self.assertAlmostEqual(hls_suresi(varyant, None), 31.3)

    def test_full_episode_and_unknown_pass(self):
        self.assertFalse(kisa_klip_mi("https://r.googlevideo.com/videoplayback?dur=5400.1"))
        self.assertFalse(kisa_klip_mi("https://x.com/master.m3u8"))
        self.assertFalse(kisa_klip_mi("https://x.com/v?dur=abc"))
        self.assertFalse(kisa_klip_mi(None))

    def test_av_kayma_daha17(self):
        # DiziPal Daha 17: ses 8640,07 sn, görüntü 8628,17 sn → ses giderek geride.
        ses = _pl(3.008, 2872) + _pl(1.0, 1)
        goruntu = _pl(10.416667, 828) + _pl(3.0, 1)
        self.assertGreater(av_kayma_orani(ses, goruntu), AV_KAYMA_ESIK)

    def test_av_senkron_ve_olculemeyen(self):
        goruntu = _pl(10.0, 299) + _pl(9.5, 1)
        self.assertLess(av_kayma_orani(_pl(3.0, 1000), goruntu), AV_KAYMA_ESIK)
        self.assertEqual(av_kayma_orani("", goruntu), 0.0)


def _pl(sure: float, adet: int) -> str:
    return f"#EXTINF:{sure},\ns.ts\n" * adet


if __name__ == "__main__":
    unittest.main()
