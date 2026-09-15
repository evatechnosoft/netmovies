# Alternatif tarama sırası. Zincir ilk çalışan kaynakta durduğu için sıra
# doğrudan bekleme süresidir: o hafta bozulan sağlayıcı listenin başındaysa
# her çözümleme onun 25 sn'lik bütçesini harcayıp geçiyordu.

import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from Public.API.v1.Libs.tarama_sirasi import ALTERNATIVE_ORDER, tarama_sirasi


class TaramaSirasiTest(unittest.TestCase):
    def test_sira_verilmezse_elle_yazilan_liste(self):
        self.assertIs(tarama_sirasi(None), ALTERNATIVE_ORDER)
        self.assertIs(tarama_sirasi(""), ALTERNATIVE_ORDER)
        self.assertIs(tarama_sirasi("   "), ALTERNATIVE_ORDER)

    def test_puanli_sira_one_gecer(self):
        sira = tarama_sirasi("DiziBox,DiziYou")
        self.assertEqual(sira[:2], ["DiziBox", "DiziYou"])

    def test_sirada_olmayan_saglayici_kaybolmaz(self):
        """Puanı henüz olmayan yeni eklenti listenin sonuna eklenir."""
        sira = tarama_sirasi("DiziBox")
        for ad in ALTERNATIVE_ORDER:
            self.assertIn(ad, sira)

    def test_tekrar_olusmaz(self):
        sira = tarama_sirasi(",".join(ALTERNATIVE_ORDER))
        self.assertEqual(len(sira), len(set(sira)))

    def test_bosluklar_temizlenir(self):
        self.assertEqual(tarama_sirasi(" DiziBox , DiziYou ")[:2], ["DiziBox", "DiziYou"])
