"""Dizilla ana sayfa ızgarası: altbilgi bağlantıları katalogda dizi olmamalı."""

import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from KekikStream.Core import HTMLHelper
from Plugins.Dizilla import Dizilla

_HTML = """
<div class="grid">
  <a href="/dizi/breaking-bad-2"><h2>Breaking Bad</h2><img src="/p.jpg"></a>
  <a href="/forum"><h2>Forum</h2></a>
  <a href="/iletisim"><h2>İletişim</h2></a>
  <a href="/"><h2>yabancı dizi</h2></a>
</div>
"""


class GridSuzgeci(unittest.TestCase):
    def test_yalniz_dizi_adresleri_kalir(self):
        secilen = HTMLHelper(_HTML).select("div.grid a")
        sonuc   = [Dizilla._result(n, "https://dizilla.now", "Yeni") for n in secilen]
        self.assertEqual([r.title for r in sonuc if r], ["Breaking Bad"])


if __name__ == "__main__":
    unittest.main()
