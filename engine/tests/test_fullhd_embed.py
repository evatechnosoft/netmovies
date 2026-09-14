"""rapidvid embed yükü: eski `av()` ve yeni `window._p8` biçimleri."""

import base64
import json
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from Plugins.FullHDFilmizlesene import FullHDFilmizlesene, _KAYDIRMA


def _sifrele(duz: str) -> str:
    """Eklentinin çözdüğü zincirin tersi — testin kendi kaynağını üretir."""
    b64 = base64.b64encode(duz.encode()).decode()
    kaydirilmis = "".join(chr((ord(ch) + _KAYDIRMA[i % 3]) % 256) for i, ch in enumerate(b64))
    return base64.b64encode(kaydirilmis.encode("latin-1")).decode()[::-1]


class EmbedCozumu(unittest.TestCase):
    def test_eski_av_bicimi_duz_adres_verir(self):
        sifre = _sifrele("https://cdn.ornek/mt/abc.m3u8")
        akis, altyazi = FullHDFilmizlesene._akis_ve_altyazi(f"<script>av('{sifre}')</script>")
        self.assertEqual(akis, "https://cdn.ornek/mt/abc.m3u8")
        self.assertEqual(altyazi, [])

    def test_yeni_p8_bicimi_json_coz(self):
        yuk = {
            "cm": "https://cdn.ornek/mt/abc",
            "tm": "https://cdn.ornek/mp/abc",
            "ct": [{"kind": "captions", "file": "https://cdn.ornek/tur.vtt", "label": "Türkçe Altyazı "}],
        }
        sifre = _sifrele(json.dumps(yuk))
        akis, altyazi = FullHDFilmizlesene._akis_ve_altyazi(f"<script>window._p8='{sifre}';</script>")
        self.assertEqual(akis, "https://cdn.ornek/mt/abc")
        self.assertEqual([(a.name, a.url) for a in altyazi], [("Türkçe Altyazı", "https://cdn.ornek/tur.vtt")])

    def test_tanimsiz_sayfa_sessizce_bos_doner(self):
        self.assertEqual(FullHDFilmizlesene._akis_ve_altyazi("<html>hiçbir şey</html>"), (None, []))


if __name__ == "__main__":
    unittest.main()
