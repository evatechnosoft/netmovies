"""M3U kaynak birleştirme: ülke süzgeci ve akış tekilleştirme."""

import os
import sys
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from Plugins.M3UPlaylist import M3UPlaylist, _tvg_ulke

_TR = """#EXTM3U
#EXTINF:-1 tvg-id="ATV.tr" group-title="General",ATV
https://ornek.tr/atv.m3u8
"""

_MOVIES = """#EXTM3U
#EXTINF:-1 tvg-id="beINMoviesTurk.tr@SD" group-title="Movies",beIN Movies Turk
https://ornek.tr/bein.m3u8
#EXTINF:-1 tvg-id="AXNIndia.in" group-title="Movies",AXN India
https://ornek.in/axn.m3u8
#EXTINF:-1 tvg-id="ATV.tr" group-title="General",ATV
https://ornek.tr/atv.m3u8
"""


class TvgUlke(unittest.TestCase):
    def test_kod_cikarilir(self):
        self.assertEqual(_tvg_ulke("beINMoviesTurk.tr@SD"), "tr")
        self.assertEqual(_tvg_ulke("ATV.tr"), "tr")

    def test_kodsuz_bos_doner(self):
        self.assertEqual(_tvg_ulke("SomeChannel"), "")
        self.assertEqual(_tvg_ulke(""), "")


class KaynakBirlestirme(unittest.TestCase):
    def _yukle(self, kaynaklar: str):
        icerik = {"https://a/tr.m3u": _TR, "https://a/movies.m3u": _MOVIES}

        def sahte_ac(req, timeout=0):
            govde = icerik[req.full_url].encode()

            class _Yanit:
                def read(self_inner):
                    return govde

                def __enter__(self_inner):
                    return self_inner

                def __exit__(self_inner, *a):
                    return False

            return _Yanit()

        with mock.patch.dict(os.environ, {"M3U_SOURCES": kaynaklar}), \
             mock.patch("urllib.request.urlopen", sahte_ac):
            return M3UPlaylist()

    def test_ulke_suzgeci_yabanciyi_eler(self):
        eklenti = self._yukle("https://a/movies.m3u#tr")
        self.assertEqual(
            sorted(it["title"] for it in eklenti._items),
            ["ATV", "beIN Movies Turk"],
        )

    def test_ayni_akis_iki_listede_tek_kez_girer(self):
        eklenti = self._yukle("https://a/tr.m3u,https://a/movies.m3u#tr")
        adresler = [it["stream_url"] for it in eklenti._items]
        self.assertEqual(len(adresler), len(set(adresler)))
        self.assertEqual(adresler.count("https://ornek.tr/atv.m3u8"), 1)


if __name__ == "__main__":
    unittest.main()
