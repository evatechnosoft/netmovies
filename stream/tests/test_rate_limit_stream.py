# NetMovies — hız sınırı akış uçlarını saymaz.
#
# webOS'un yerel HLS motoru ~1500 sn ileriyi tamponluyor; ev ağı Docker NAT'ı
# arkasında tek IP. /proxy/video sayılınca 180/dk dolup segmentler 429 aldı,
# film ~40. saniyede kapandı (25 Eylül, LG).

import asyncio
import os
import sys
import unittest
from pathlib import Path
from types import SimpleNamespace

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

os.environ["AUTH_USER"] = ""
os.environ["AUTH_PASS"] = ""
os.environ["ADMIN_PASS"] = ""

from Core.Modules import _guard  # noqa: E402


def _istek(yol: str) -> SimpleNamespace:
    return SimpleNamespace(url=SimpleNamespace(path=yol))


def _say(yol: str, n: int) -> int:
    async def calis() -> int:
        izin = 0
        for _ in range(n):
            ok, _bekle = await _guard._check_ip_rate_limit("10.0.0.9", _istek(yol))
            izin += ok
        return izin
    return asyncio.run(calis())


class HizSiniriAkisTest(unittest.TestCase):
    def setUp(self):
        _guard._rate_limit_hits.clear()

    def test_video_proxy_sayilmaz(self):
        n = _guard._RATE_LIMIT_MAX_REQUESTS + 50
        self.assertEqual(_say("/proxy/video", n), n)

    def test_normal_uc_sinirlanir(self):
        n = _guard._RATE_LIMIT_MAX_REQUESTS + 50
        self.assertEqual(_say("/api/v1/aggregate_new", n), _guard._RATE_LIMIT_MAX_REQUESTS)


if __name__ == "__main__":
    unittest.main()
