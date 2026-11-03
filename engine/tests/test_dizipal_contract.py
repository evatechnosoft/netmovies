"""DiziPal contract test (Next.js site, Nov 2026): a.tc / div.ec cards and flight-data player cipher."""

import base64
import hashlib
import json
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from Crypto.Cipher import AES
from Crypto.Util.Padding import pad
from KekikStream.Core import HTMLHelper
from Plugins import DiziPal as dp

_BASE = "https://dizipal1824.com"
# selector: a.tc[href] > span.tt (title) + img (poster; alt is empty)
_SERIES_CARD = '<a class="tc" aria-label="Below, 2026, Dizi" href="/dizi/below-dizi-izle"><img src="https://cdn/p.jpg" alt=""><span class="tt">Below</span></a>'
# selector: div.ec > a.ecl[href] + span.tn; season/episode come from the href
_EPISODE_CARD = '<div class="ec"><a class="ecl" aria-label="x" href="/dizi/below/1-sezon/2-bolum"></a><img src="https://cdn/e.jpg"><span class="tn">Below</span></div>'


def _flight(player_url: str, key: str) -> str:
    salt, iv = os.urandom(16), os.urandom(16)
    derived = hashlib.pbkdf2_hmac("sha512", key.encode(), salt, 999, 32)
    cipher = base64.b64encode(AES.new(derived, AES.MODE_CBC, iv).encrypt(pad(player_url.encode(), 16))).decode()
    enc = json.dumps({"ciphertext": cipher, "iv": iv.hex(), "salt": salt.hex()}, separators=(",", ":"))
    # Next.js flight data escapes quotes inside a JS string.
    return ('"player":{"kind":"tv","sources":[{"label":"PUB+ · Türkçe Altyazı","href":"/dizi/below/1-sezon/1-bolum","on":true}],'
            f'"enc":{enc},"k":"{key}"}}').replace('"', '\\"')


class DiziPalContract(unittest.TestCase):
    def test_cards(self):
        cards = dp.DiziPal._cards(HTMLHelper(_SERIES_CARD + _EPISODE_CARD), _BASE, "c")
        self.assertEqual([(c.title, c.url, c.poster) for c in cards], [
            ("Below", f"{_BASE}/dizi/below-dizi-izle", "https://cdn/p.jpg"),
            ("Below 1. Sezon 2. Bölüm", f"{_BASE}/dizi/below/1-sezon/2-bolum", "https://cdn/e.jpg"),
        ])

    def test_player_cipher_and_sources(self):
        page = dp._unescape_flight(_flight("//four.dplayer82.site/iframe.php?v=abc", "secret-k"))
        enc = dp._ENC_RE.search(page)
        self.assertIsNotNone(enc)
        self.assertEqual(dp._decrypt_player_url(enc.group(1), enc.group(2)), "//four.dplayer82.site/iframe.php?v=abc")
        self.assertEqual([s["label"] for s in dp._player_sources(page)], ["PUB+ · Türkçe Altyazı"])


if __name__ == "__main__":
    unittest.main()
