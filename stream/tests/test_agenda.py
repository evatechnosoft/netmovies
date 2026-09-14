# Ajanda gün gruplaması. Satırlar tarihe göre sıralı gelir; aynı günün kayıtları
# tek başlık altında toplanmalı, sıra bozulmamalı.

import datetime
import unittest

from Public.API.v1.Libs.ajanda_grup import aralikla, gunlere_bol


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


class AgendaAralikTest(unittest.TestCase):
    """Hafta, ayın alt kümesi olmalı: ayrı TMDB turu atıldığında `discover` iki
    aralık için farklı "ilk N popüler" listesi döndürüyor, aynı gün haftada 6
    ayda 5 satır görünüyordu."""

    def _liste(self):
        bugun = datetime.date.today()
        return bugun, [
            {"tarih": str(bugun), "baslik": "Bugün-A", "tur": "dizi"},
            {"tarih": str(bugun), "baslik": "Bugün-B", "tur": "dizi"},
            {"tarih": str(bugun + datetime.timedelta(days=3)), "baslik": "Hafta-İçi", "tur": "dizi"},
            {"tarih": str(bugun + datetime.timedelta(days=20)), "baslik": "Ay-Sonu", "tur": "dizi"},
        ]

    def test_hafta_ayin_alt_kumesi(self):
        bugun, ay = self._liste()
        hafta = aralikla(ay, str(bugun + datetime.timedelta(days=7)))
        self.assertEqual([s["baslik"] for s in hafta], ["Bugün-A", "Bugün-B", "Hafta-İçi"])
        self.assertTrue({s["baslik"] for s in hafta} <= {s["baslik"] for s in ay})

    def test_bugun_sayisi_iki_gorunumde_ayni(self):
        bugun, ay = self._liste()
        hafta = aralikla(ay, str(bugun + datetime.timedelta(days=7)))
        say = lambda satirlar: sum(1 for s in satirlar if s["tarih"] == str(bugun))
        self.assertEqual(say(hafta), say(ay))


if __name__ == "__main__":
    unittest.main()
