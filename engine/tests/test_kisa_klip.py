import unittest

from Public.API.v1.Libs.kisa_klip import kisa_klip_mi


class KisaKlipTest(unittest.TestCase):
    def test_googlevideo_clip_is_short(self):
        self.assertTrue(kisa_klip_mi("https://r.googlevideo.com/videoplayback?dur=55.296&itag=18"))

    def test_full_episode_and_unknown_pass(self):
        self.assertFalse(kisa_klip_mi("https://r.googlevideo.com/videoplayback?dur=5400.1"))
        self.assertFalse(kisa_klip_mi("https://x.com/master.m3u8"))
        self.assertFalse(kisa_klip_mi("https://x.com/v?dur=abc"))
        self.assertFalse(kisa_klip_mi(None))


if __name__ == "__main__":
    unittest.main()
