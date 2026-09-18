# Arama satırı: aynı içerik tek satır, kullanıcının listesi en üstte.

import sys
import unittest
from pathlib import Path

STREAM_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(STREAM_ROOT))

from Public.API.v1.Routers.search_all import _grupla


class GroupingTest(unittest.TestCase):
    def test_same_title_collapses_into_one_row(self) -> None:
        satirlar = _grupla([
            {"title": "Reacher", "plugin": "DiziPal", "url": "a", "poster": ""},
            {"title": "reacher", "plugin": "Dizilla", "url": "b", "poster": "p.jpg"},
            {"title": "Preacher", "plugin": "DiziMom", "url": "c"},
        ])

        self.assertEqual(["Reacher", "Preacher"], [s["title"] for s in satirlar])
        self.assertEqual(["DiziPal", "Dizilla"], [p["plugin"] for p in satirlar[0]["providers"]])
        # Temsilcinin posteri boştu; gruptaki dolu poster kullanılır.
        self.assertEqual("p.jpg", satirlar[0]["poster"])

    def test_same_provider_is_not_listed_twice(self) -> None:
        satirlar = _grupla([
            {"title": "Neagley", "plugin": "DiziPal", "url": "a"},
            {"title": "Neagley", "plugin": "DiziPal", "url": "b"},
        ])

        self.assertEqual(1, len(satirlar[0]["providers"]))

    def test_untitled_rows_do_not_merge(self) -> None:
        satirlar = _grupla([
            {"title": "", "plugin": "X", "url": "a"},
            {"title": "", "plugin": "Y", "url": "b"},
        ])

        self.assertEqual(2, len(satirlar))


if __name__ == "__main__":
    unittest.main()
