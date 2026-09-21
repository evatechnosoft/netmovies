"""Poster adresi çıkarımı — tembel yükleme yer tutucusu poster sanılmasın.

DiziMom son bölümler rafında 45 öğenin hepsi `data:image/svg+xml;base64,…`
posteriyle geliyordu: site asıl adresi `data-src`te tutuyor, `src`te gömülü bir
yer tutucu duruyor ve onu JavaScript değiştiriyor. Sunucu tarafında JavaScript
yok, bu yüzden kural eklentide olmalı.
"""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from KekikStream.Core import HTMLHelper
from Plugins.__dizi_common import poster_attr


class SahteNode:
    """`select_attr` sözleşmesini taşıyan en küçük düğüm."""

    def __init__(self, oznitelikler: dict[str, dict[str, str]]):
        # {secici: {oznitelik: deger}}
        self._veri = oznitelikler

    def select_attr(self, selector: str, attr: str):
        return self._veri.get(selector, {}).get(attr)


class PosterAttrTest(unittest.TestCase):
    def test_data_src_src_yerine_gecer(self):
        node = SahteNode({"img": {
            "src": "data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=",
            "data-src": "https://cdn/poster.jpg",
        }})
        self.assertEqual(poster_attr(node, ("img",)), "https://cdn/poster.jpg")

    def test_tembel_oznitelik_yoksa_src_kullanilir(self):
        node = SahteNode({"img": {"src": "https://cdn/duz.jpg"}})
        self.assertEqual(poster_attr(node, ("img",)), "https://cdn/duz.jpg")

    def test_yalniz_data_uri_varsa_none(self):
        # Yer tutucuyu poster diye döndürmek, istemcide boş kart demek.
        node = SahteNode({"img": {"src": "data:image/gif;base64,R0lGOD"}})
        self.assertIsNone(poster_attr(node, ("img",)))

    def test_diger_tembel_oznitelikler(self):
        for ad in ("data-original", "data-lazy-src", "data-echo"):
            node = SahteNode({"img": {"src": "data:image/png;base64,AAA", ad: "https://cdn/x.jpg"}})
            self.assertEqual(poster_attr(node, ("img",)), "https://cdn/x.jpg", ad)

    def test_secici_sirasi_korunur(self):
        node = SahteNode({
            "div.cat-img img": {"data-src": "https://cdn/ilk.jpg"},
            "img": {"data-src": "https://cdn/genel.jpg"},
        })
        self.assertEqual(poster_attr(node, ("div.cat-img img", "img")), "https://cdn/ilk.jpg")

    def test_bulunamazsa_none(self):
        self.assertIsNone(poster_attr(SahteNode({}), ("img",)))


if __name__ == "__main__":
    unittest.main()


class IframeSrcTest(unittest.TestCase):
    def test_tembel_iframe_data_src(self):
        from Plugins.__dizi_common import iframe_src
        html = '<div class="video"><p><iframe data-lazyloaded="1" src="about:blank" data-src="https://p.example/tv/video/abc"></iframe></p></div>'
        self.assertEqual(iframe_src(HTMLHelper(html), ("div.video p iframe", "iframe")), "https://p.example/tv/video/abc")

    def test_duz_iframe(self):
        from Plugins.__dizi_common import iframe_src
        self.assertEqual(iframe_src(HTMLHelper('<iframe src="https://x/y"></iframe>'), ("iframe",)), "https://x/y")
        self.assertIsNone(iframe_src(HTMLHelper('<iframe src="about:blank"></iframe>'), ("iframe",)))
