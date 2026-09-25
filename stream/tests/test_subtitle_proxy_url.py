# NetMovies — kaynak altyazılarına jetonlu proxy adresi eklenir, ham adres korunur.
#
# /tv altyazıyı <track> ile yükler (aynı köken şart, TV DNS'i kaynağı engelli
# IP'ye çözebiliyor); Android TV ve /markers ham `url`'i kullanmaya devam eder.

import os
import sys
import unittest
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

os.environ["AUTH_USER"] = ""
os.environ["AUTH_PASS"] = ""
os.environ["ADMIN_PASS"] = ""

from Public.API.v1.Libs.source_proxy import route_through_proxy  # noqa: E402
from Public.Proxy.Libs.proxy_token import validate_proxy_token  # noqa: E402

ALT = "https://alt.example.com/tr.vtt"


class AltyaziProxyTest(unittest.TestCase):
    def test_proxy_url_eklenir_ham_korunur(self):
        for plugin in ("FullHDFilmizlesene", "BilinmeyenEklenti"):   # proxy'li ve proxysiz kaynak
            kaynak = {"plugin": plugin, "url": "https://cdn.example.com/m.m3u8", "referer": "https://r.example.com/",
                      "subtitles": [{"name": "Türkçe", "url": ALT}]}
            alt = route_through_proxy([kaynak], "http://ev:3310")[0]["subtitles"][0]
            self.assertEqual(alt["url"], ALT)
            parca = urlsplit(alt["proxy_url"])
            self.assertEqual(parca.path, "/proxy/subtitle")
            q = parse_qs(parca.query)
            self.assertEqual(q["url"][0], ALT)
            self.assertTrue(validate_proxy_token(q["proxy_token"][0], ALT))

    def test_altyazisiz_kaynak_degismez(self):
        kaynak = {"plugin": "BilinmeyenEklenti", "url": "https://cdn.example.com/m.m3u8"}
        self.assertEqual(route_through_proxy([kaynak], "http://ev:3310")[0], kaynak)


if __name__ == "__main__":
    unittest.main()
