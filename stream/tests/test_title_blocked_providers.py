# A provider banned for one title (admin title_blocked_providers) must drop out of
# that title's sources only. Real case 6 Nov: DiziAbi Suskunlar "23. bölüm" is an old
# 49-min cut, official 23 is 103 min — the TV fell back to it and replayed old scenes.

import unittest

from Public.API.v1.Routers.resolve_sources import engelli_cikar
from Public.Home.Libs import admin_config


class TitleBlockedProvidersTest(unittest.TestCase):
    def setUp(self) -> None:
        self.kaynaklar = [{"plugin": "DiziAbi", "url": "a"}, {"plugin": "YouTube", "url": "b"}]
        self.cfg = {"title_blocked_providers": {"suskunlar": ["DiziAbi"]}}

    def test_engelli_saglayici_cikar(self) -> None:
        kalan = engelli_cikar(self.kaynaklar, "Suskunlar", self.cfg)
        self.assertEqual(["YouTube"], [k["plugin"] for k in kalan])

    def test_baska_baslik_etkilenmez(self) -> None:
        self.assertEqual(self.kaynaklar, engelli_cikar(self.kaynaklar, "Abi", self.cfg))

    def test_ayar_kayitta_korunur(self) -> None:
        norm = admin_config._normalize(self.cfg)
        self.assertEqual({"suskunlar": ["DiziAbi"]}, norm["title_blocked_providers"])
        self.assertEqual({}, admin_config._normalize({})["title_blocked_providers"])


if __name__ == "__main__":
    unittest.main()
