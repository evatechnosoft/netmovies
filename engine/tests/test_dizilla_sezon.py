"""Dizilla sezon bağlantıları: bölüm adresleri sezon sanılmamalı."""

import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from KekikStream.Core import HTMLHelper
from Plugins.Dizilla import Dizilla

# Sayfada sezon bağlantılarının yanında bölüm bağlantıları da `-sezon` taşıyor.
_HTML = """
<div class="gap-2">
  <a href="/reacher-1-sezon">1</a>
  <a href="/reacher-2-sezon">2</a>
  <a href="/reacher-3-sezon">3</a>
  <a href="/reacher-4-sezon">4</a>
  <a href="/reacher-1-sezon-1-bolum-c06">1</a>
  <a href="/reacher-1-sezon-2-bolum">2</a>
  <a href="/reacher-4-sezon-7-bolum">7</a>
</div>
"""


class SezonAdresleri(unittest.TestCase):
    def test_yalniz_sezon_sayfalari_alinir(self):
        eklenti = Dizilla.__new__(Dizilla)
        eklenti.main_url = "https://dizilla.now"
        sonuc = eklenti._sezon_adresleri(HTMLHelper(_HTML))
        self.assertEqual([no for no, _ in sonuc], [1, 2, 3, 4])
        self.assertTrue(all(adres.endswith("-sezon") for _, adres in sonuc))


if __name__ == "__main__":
    unittest.main()
