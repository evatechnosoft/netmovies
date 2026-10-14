import unittest

from Public.Proxy.Libs.helpers import detect_hls_from_url, is_hls_segment

YT_SEG = "https://rr5---sn-x.googlevideo.com/videoplayback/id/abc/itag/616/playlist/index.m3u8/govp/slices%3D0-8/gosq/0/file/seg.ts"
YT_MAP = "https://rr5---sn-x.googlevideo.com/videoplayback/id/abc/itag/616/playlist/index.m3u8/govp/slices%3D0-8/gosq/0?is=1&file=seg.ts"
YT_PL  = "https://manifest.googlevideo.com/api/manifest/hls_playlist/id/abc/itag/616/playlist/index.m3u8"


class HlsTespit(unittest.TestCase):
    def test_youtube_segment_manifest_sayilmaz(self):
        for url in (YT_SEG, YT_MAP):
            self.assertTrue(is_hls_segment(url), url)
            self.assertFalse(detect_hls_from_url(url), url)

    def test_m3u8_ile_biten_manifest(self):
        self.assertFalse(is_hls_segment(YT_PL))
        self.assertTrue(detect_hls_from_url(YT_PL))
        self.assertTrue(detect_hls_from_url("https://x/y/playlist.m3u8?token=1.ts"))

    def test_klasik_segment(self):
        self.assertTrue(is_hls_segment("https://cdn/x/seg-00001.ts"))
        self.assertFalse(detect_hls_from_url("https://cdn/x/seg-00001.ts"))


if __name__ == "__main__":
    unittest.main()
