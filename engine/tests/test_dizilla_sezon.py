"""Dizilla bölüm listesi: sezon sayfası adresi bölüm sanılmamalı."""

import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from Plugins.Dizilla import _bolumler

# Next.js flight metni: JSON-LD bölüm adresleri kaçışlı, sezon sayfası adresleri de geçiyor.
_FLIGHT = r"""
{\"@type\":\"TVSeason\",\"url\":\"https://dizilla.now/reacher-1-sezon\"}
{\"@type\":\"TVSeason\",\"url\":\"https://dizilla.now/reacher-4-sezon\"}
{\"@type\":\"TVEpisode\",\"url\":\"https://dizilla.now/reacher-4-sezon-7-bolum\"}
{\"@type\":\"TVEpisode\",\"url\":\"https://dizilla.now/reacher-1-sezon-2-bolum\"}
{\"@type\":\"TVEpisode\",\"url\":\"https://dizilla.now/reacher-1-sezon-1-bolum-c06\"}
{\"@type\":\"TVEpisode\",\"url\":\"https://dizilla.now/reacher-1-sezon-1-bolum-c06\"}
"""


class Bolumler(unittest.TestCase):
    def test_yalniz_bolum_adresleri_sirali_ve_tekil(self):
        sonuc = _bolumler(_FLIGHT, "https://dizilla.now")
        self.assertEqual([(b.season, b.episode) for b in sonuc], [(1, 1), (1, 2), (4, 7)])
        self.assertTrue(all("-bolum" in b.url for b in sonuc))


if __name__ == "__main__":
    unittest.main()
