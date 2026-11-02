"""Dizilla kart ızgarası: altbilgi/menü bağlantıları katalogda dizi olmamalı."""

import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from Plugins.Dizilla import Dizilla

_HTML = """
<div class="grid">
  <a class="pcard" href="/dizi/breaking-bad-2"><span class="mvk-t">Breaking Bad</span><img src="/p.jpg"></a>
  <a class="pcard" href="/forum"><span class="mvk-t">Forum</span></a>
  <a class="pcard" href="/iletisim"><span class="mvk-t">İletişim</span></a>
  <a class="pcard" href="/"><span class="mvk-t">yabancı dizi</span></a>
</div>
"""


class KartSuzgeci(unittest.TestCase):
    def test_yalniz_dizi_adresleri_kalir(self):
        eklenti = Dizilla.__new__(Dizilla)
        eklenti.main_url = "https://dizilla.now"
        self.assertEqual([baslik for baslik, _, _ in eklenti._kartlar(_HTML)], ["Breaking Bad"])


if __name__ == "__main__":
    unittest.main()
