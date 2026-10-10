import unittest

from Public.Proxy.Libs.av_esitle import _pts_oku, _pts_yaz, extinf_olcekle, oran_oku, ts_olcekle
from Public.Proxy.Libs.helpers import rewrite_hls_manifest


def _ts_paket(pts: int, dts: int, stream_id: int = 0xE0) -> bytes:
    """Tek PES başlangıçlı TS paketi (PTS+DTS)."""
    pes = bytearray(b"\x00\x00\x01" + bytes([stream_id]) + b"\x00\x00\x80\xC0\x0A" + bytes(10))
    _pts_yaz(pes, 9, pts); pes[9] = (pes[9] & 0x0F) | 0x30
    _pts_yaz(pes, 14, dts); pes[14] = (pes[14] & 0x0F) | 0x10
    paket = bytes([0x47, 0x41, 0x00, 0x10]) + pes
    return paket + b"\xFF" * (188 - len(paket))


class AvEsitleTest(unittest.TestCase):
    def test_oran_aralik(self):
        self.assertEqual(oran_oku("1.001379"), 1.001379)
        self.assertIsNone(oran_oku("1"))
        self.assertIsNone(oran_oku("1.5"))
        self.assertIsNone(oran_oku("abc"))
        self.assertIsNone(oran_oku(None))

    def test_video_pts_dts_esnetilir(self):
        oran = 1.001379
        pts, dts = 6500 * 90000, 6499 * 90000
        cikti = bytearray(ts_olcekle(_ts_paket(pts, dts), oran))
        self.assertEqual(_pts_oku(cikti, 4 + 9), round(pts * oran))
        self.assertEqual(_pts_oku(cikti, 4 + 14), round(dts * oran))
        self.assertEqual(cikti[4 + 9] >> 4, 0x3)   # PTS+DTS öneki korunur
        self.assertEqual(len(cikti), 188)

    def test_ses_pesi_ve_ts_olmayan_dokunulmaz(self):
        ses = _ts_paket(90000, 90000, stream_id=0xC0)
        self.assertEqual(ts_olcekle(ses, 1.01), ses)
        self.assertEqual(ts_olcekle(b"\x00\x00\x00\x18ftyp", 1.01), b"\x00\x00\x00\x18ftyp")

    def test_extinf(self):
        self.assertEqual(extinf_olcekle("#EXTINF:10.0,", 1.001), "#EXTINF:10.010000,")

    def test_manifest_oran_yalniz_goruntuye(self):
        master = (b'#EXTM3U\n#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="a",URI="ses.m3u8"\n'
                  b'#EXT-X-STREAM-INF:BANDWIDTH=1,AUDIO="a"\nv.m3u8\n')
        cikti = rewrite_hls_manifest(master, "https://x.com/master.m3u8", force_proxy=True, av_oran=1.001).decode()
        ses_satiri, goruntu_satiri = cikti.splitlines()[1], cikti.splitlines()[3]
        self.assertNotIn("av_oran", ses_satiri)
        self.assertIn("av_oran=1.001", goruntu_satiri)
        medya = b"#EXTM3U\n#EXTINF:10.0,\nhttps://cdn.x.com/s1.ts\n"
        cikti = rewrite_hls_manifest(medya, "https://x.com/v.m3u8", force_proxy=True, av_oran=1.001).decode()
        self.assertIn("#EXTINF:10.010000,", cikti)
        self.assertIn("av_oran=1.001", cikti.splitlines()[2])


if __name__ == "__main__":
    unittest.main()
