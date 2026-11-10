# Upstream header names must be canonical case: dplayer82 rejects lowercase "user-agent"/"referer" with 403.
import os
import sys
import unittest
from pathlib import Path
from types import SimpleNamespace

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
os.environ.setdefault("AUTH_PASS", "test")

from Public.Proxy.Libs.helpers import prepare_request_headers


class BaslikHarfTest(unittest.TestCase):
    def test_kanonik_harf(self):
        h = prepare_request_headers(SimpleNamespace(headers={}), "https://sn.dplayer82.site/nm.php?v=1", "https://sn.dplayer82.site/", "UA/1", {"Referer": "eski"})
        self.assertEqual(h["User-Agent"], "UA/1")
        self.assertEqual(h["Referer"], "https://sn.dplayer82.site/")
        self.assertNotIn("user-agent", h)
        self.assertNotIn("referer", h)


if __name__ == "__main__":
    unittest.main()
