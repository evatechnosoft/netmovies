# Kaynağı olmayan yeni film "Sinemada" rozeti alır; eski film, dizi ve yanıtsız kart almaz.

import unittest

from Public.API.v1.Routers.search_all import SINEMADA, _sinemada_isaretle, _yeni_film


def _sayac(tablo: dict[str, int | None]):
    async def kaynak_sayisi(plugin: str, url: str) -> int | None:
        return tablo[plugin]
    return kaynak_sayisi


class SinemadaRozetiTest(unittest.IsolatedAsyncioTestCase):
    def test_yalniz_bu_yilin_filmi_yoklanir(self):
        self.assertTrue(_yeni_film({"year": 2026}, 2026))
        self.assertTrue(_yeni_film({"year": 2027}, 2026))
        self.assertFalse(_yeni_film({"year": 2024}, 2026))
        self.assertFalse(_yeni_film({}, 2026))
        self.assertFalse(_yeni_film({"year": 2026, "episode_count": 8}, 2026))

    async def test_tum_saglayicilar_bossa_rozet(self):
        kart = {"year": 2026, "lang": ["ALT"], "providers": [{"plugin": "A", "url": "u"}, {"plugin": "B", "url": "u"}]}
        await _sinemada_isaretle([kart], _sayac({"A": 0, "B": 0}), 2026)
        self.assertEqual(kart["lang"], ["ALT", SINEMADA])

    async def test_bir_saglayici_doluysa_rozet_yok(self):
        kart = {"year": 2026, "providers": [{"plugin": "A", "url": "u"}, {"plugin": "B", "url": "u"}]}
        await _sinemada_isaretle([kart], _sayac({"A": 0, "B": 2}), 2026)
        self.assertNotIn("lang", kart)

    async def test_yanitsiz_saglayici_rozet_vermez(self):
        kart = {"year": 2026, "plugin": "A", "url": "u"}
        await _sinemada_isaretle([kart], _sayac({"A": None}), 2026)
        self.assertNotIn("lang", kart)
