# Ajanda gün gruplaması. Satırlar tarihe göre sıralı gelir; aynı günün kayıtları
# tek başlık altında toplanmalı, sıra bozulmamalı.

import unittest

from Public.API.v1.Libs.ajanda_grup import gunlere_bol


class AgendaGrupTest(unittest.TestCase):
    def _satir(self, tarih, baslik):
        return {"tarih": tarih, "baslik": baslik, "tur": "dizi"}

    def test_ayni_gun_tek_baslik(self):
        gunler = gunlere_bol([
            self._satir("2026-09-11", "A"),
            self._satir("2026-09-11", "B"),
            self._satir("2026-09-12", "C"),
        ])
        self.assertEqual([g["tarih"] for g in gunler], ["2026-09-11", "2026-09-12"])
        self.assertEqual(len(gunler[0]["ogeler"]), 2)
        self.assertEqual(gunler[1]["ogeler"][0]["baslik"], "C")

    def test_bos_liste(self):
        self.assertEqual(gunlere_bol([]), [])

    def test_tek_kayit(self):
        gunler = gunlere_bol([self._satir("2026-09-11", "A")])
        self.assertEqual(len(gunler), 1)
        self.assertEqual(len(gunler[0]["ogeler"]), 1)


if __name__ == "__main__":
    unittest.main()
