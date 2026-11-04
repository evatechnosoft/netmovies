# Proxy iç ağa kapalı; yalnız engine'in DDizi birleştirme ucu açık.

import unittest

from Public.Proxy.Libs.helpers import ic_engine_mi


class IcEngineTest(unittest.TestCase):
    def test_yalniz_birlestirme_ucu_acik(self):
        self.assertTrue(ic_engine_mi("http://engine:3310/api/v1/ddizi_birlesik?u=x&t=m"))
        self.assertFalse(ic_engine_mi("http://engine:3310/api/v1/health"))
        self.assertFalse(ic_engine_mi("http://engine:3310/api/v1/ddizi_birlesik/../x"))
        self.assertFalse(ic_engine_mi("http://baska:3310/api/v1/ddizi_birlesik"))
        self.assertFalse(ic_engine_mi("https://engine:3310/api/v1/ddizi_birlesik"))


if __name__ == "__main__":
    unittest.main()
