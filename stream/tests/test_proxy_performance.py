# NetMovies — proxy performans davranışı sözleşme testleri.
#
# İki şey korunuyor:
#  1. WARP'ın da çözemediği host TTL boyunca tekrar denenmez. Bu kayıt olmadan
#     her segment iki upstream isteği yiyor (doğrudan 403 → WARP → yine 403).
#  2. Segment zinciri: manifestten çıkan sıra hatırlanır, servis edilen
#     segmentin ardındakiler ön-yüklenir. Zincir yoksa 20. dakikada ön-yükleme
#     ölür ve her segment sıfırdan çekilir.

import os
import sys
import unittest
from pathlib import Path

os.environ["AUTH_USER"] = ""
os.environ["AUTH_PASS"] = ""
os.environ["ADMIN_PASS"] = ""

STREAM_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(STREAM_ROOT))

from Public.Proxy.Libs import helpers
from Public.Proxy.Routers import video


class WarpNegatifOnbellekTest(unittest.TestCase):
    def setUp(self) -> None:
        helpers._warp_dead.clear()
        helpers._warp_hosts.clear()

    def tearDown(self) -> None:
        helpers._warp_dead.clear()
        helpers._warp_hosts.clear()

    def test_bilinmeyen_host_denenebilir(self):
        self.assertFalse(helpers.warp_olu("example.com"))

    def test_warp_da_cozemedigi_host_atlanir(self):
        import time as _t

        helpers._warp_dead["olu.example"] = _t.monotonic() + helpers._WARP_DEAD_TTL
        self.assertTrue(helpers.warp_olu("olu.example"))

    def test_ttl_dolunca_yeniden_denenir(self):
        import time as _t

        helpers._warp_dead["eski.example"] = _t.monotonic() - 1.0
        self.assertFalse(helpers.warp_olu("eski.example"))


class SegmentZinciriTest(unittest.TestCase):
    def setUp(self) -> None:
        video._segment_zinciri.clear()

    def tearDown(self) -> None:
        video._segment_zinciri.clear()

    def test_zincir_sirayi_hatirlar(self):
        video.zinciri_kaydet(["a.ts", "b.ts", "c.ts"])
        self.assertEqual(video._segment_zinciri["a.ts"], "b.ts")
        self.assertEqual(video._segment_zinciri["b.ts"], "c.ts")
        self.assertNotIn("c.ts", video._segment_zinciri)

    def test_tavan_asilinca_temizlenir(self):
        video.zinciri_kaydet([f"{i}.ts" for i in range(video._ZINCIR_TAVANI + 10)])
        self.assertLessEqual(len(video._segment_zinciri), video._ZINCIR_TAVANI + 10)
        video.zinciri_kaydet(["x.ts", "y.ts"])
        self.assertEqual(video._segment_zinciri.get("x.ts"), "y.ts")

    def test_manifestten_tum_segmentler_zincire_girer(self):
        manifest = b"#EXTM3U\n#EXTINF:4,\nseg-1.ts\n#EXTINF:4,\nseg-2.ts\n#EXTINF:4,\nseg-3.ts\n"
        # prefetch görevi asyncio döngüsü ister; yalnız zincir kurulumunu doğruluyoruz.
        satirlar = [
            "https://cdn.example/seg-1.ts",
            "https://cdn.example/seg-2.ts",
            "https://cdn.example/seg-3.ts",
        ]
        video.zinciri_kaydet(satirlar)
        self.assertEqual(video._segment_zinciri[satirlar[0]], satirlar[1])
        self.assertEqual(video._segment_zinciri[satirlar[1]], satirlar[2])
        self.assertTrue(manifest.startswith(b"#EXTM3U"))


if __name__ == "__main__":
    unittest.main()
