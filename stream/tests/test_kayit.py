"""Kayıtlar motoru: HLS kopyası yerel adlarla yazılır, dosya yolu klasörden çıkamaz.

Ağ sahte (httpx.MockTransport): master → ≤1080 varyant + ses grubu + AES anahtarı.
"""

import asyncio
import json
import tempfile
import unittest
from pathlib import Path

import httpx

from Public.API.v1.Libs import kayit

MASTER = """#EXTM3U
#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="tr",DEFAULT=YES,URI="ses.m3u8"
#EXT-X-STREAM-INF:BANDWIDTH=9000000,RESOLUTION=3840x2160,AUDIO="aud"
v2160.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080,AUDIO="aud"
v1080.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1280x720,AUDIO="aud"
v720.m3u8
"""
MEDYA = """#EXTM3U
#EXT-X-TARGETDURATION:6
#EXT-X-KEY:METHOD=AES-128,URI="https://cdn/key"
#EXTINF:6,
s1.js
#EXTINF:6,
s2.js
#EXT-X-ENDLIST
"""


def _sahte(istek: httpx.Request) -> httpx.Response:
    yol = istek.url.path
    if yol.endswith("master.m3u8"):
        return httpx.Response(200, text=MASTER)
    if yol.endswith(("v1080.m3u8", "ses.m3u8")):
        return httpx.Response(200, text=MEDYA)
    if yol.endswith(("s1.js", "s2.js", "key")):
        return httpx.Response(200, content=b"x" * 10)
    return httpx.Response(404)


class KayitTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        kayit.KAYIT_DIR = Path(self.tmp.name)
        kayit._client = httpx.AsyncClient(transport=httpx.MockTransport(_sahte))

    def tearDown(self):
        self.tmp.cleanup()

    def test_kimlik_saglayicidan_bagimsiz(self):
        a = kayit.kayit_id("Reacher (2022) Türkçe Dublaj izle", 2, 3, 7)
        b = kayit.kayit_id("reacher 2022", "2", "3", 0)
        self.assertEqual(a, b)
        self.assertNotEqual(a, kayit.kayit_id("Reacher 2022", 2, 4, 7))

    def test_hls_kopyasi(self):
        meta = {"id": "0123456789abcdef", "bayt": 0}
        asyncio.run(kayit._kaynagi_indir(meta, "http://yerel/master.m3u8"))
        klasor = kayit.KAYIT_DIR / meta["id"]
        master = (klasor / "index.m3u8").read_text()
        self.assertIn("RESOLUTION=1920x1080", master)          # 4K değil
        self.assertIn('URI="a/index.m3u8"', master)
        v = (klasor / "v" / "index.m3u8").read_text()
        self.assertIn('URI="ek0.bin"', v)
        self.assertIn("00001.ts", v)
        self.assertNotIn("http", v)                           # internetsiz oynar
        self.assertTrue((klasor / "a" / "00002.ts").is_file())
        self.assertEqual(meta["son_segment"], "v/00002.ts")
        self.assertEqual(meta["giris"], "index.m3u8")

    def test_dosya_yolu_klasorden_cikamaz(self):
        kid = "0123456789abcdef"
        (kayit.KAYIT_DIR / kid / "v").mkdir(parents=True)
        (kayit.KAYIT_DIR / kid / "v" / "00000.ts").write_bytes(b"x")
        (kayit.KAYIT_DIR / kid / "meta.json").write_text(json.dumps({"id": kid}))
        self.assertIsNotNone(kayit.dosya(kid, "v/00000.ts"))
        for kotu in ("../x", "v/../../etc/passwd", "meta.json", "/etc/passwd"):
            self.assertIsNone(kayit.dosya(kid, kotu), kotu)
        self.assertIsNone(kayit.dosya("../../etc", "v/00000.ts"))

    def test_canli_yayin_reddedilir(self):
        meta = {"id": "0123456789abcdef", "bayt": 0}
        canli = MEDYA.replace("#EXT-X-ENDLIST\n", "")
        with self.assertRaises(ValueError):
            asyncio.run(kayit._medya_indir(meta, canli, "http://yerel/v.m3u8", kayit.KAYIT_DIR / "x", (0, 1)))


if __name__ == "__main__":
    unittest.main()
