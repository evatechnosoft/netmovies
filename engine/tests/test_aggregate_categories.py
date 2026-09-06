# Kategori seçimi saf fonksiyondur: eklenti/ağ gerekmez.
# Regresyon koruması — tek kategori seçildiğinde ana sayfa 38 içeriğe düşmüştü.

import sys, types, unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

# Router modülü FastAPI/eklenti zincirini çekiyor; test yalnız saf seçiciyi ölçer.
_SRC = (
    Path(__file__).resolve().parents[1]
    / "Public/API/v1/Routers/aggregate_new.py"
).read_text(encoding="utf-8")
_HEAD = _SRC.split("async def _fetch_category")[0].split("# type -> kategori")[1]
_mod = types.ModuleType("agg_pure")
exec("# type -> kategori" + _HEAD, _mod.__dict__)

HDFC = {
    "u1": "Yeni Eklenen Filmler", "u2": "Diziler", "u3": "Son Bölümler",
    "u4": "Aksiyon", "u5": "Komedi", "u6": "Korku", "u7": "Bilim Kurgu",
    "u8": "Animasyon", "u9": "Dram", "u10": "Gerilim",
}
DIZIYOU = {f"d{i}": c for i, c in enumerate(
    ["Aksiyon", "Dram", "Komedi", "Gerilim", "Bilim Kurgu", "Fantazi", "Macera"]
)}
DIZIMOM = {"m1": "Son Bölümler", "m2": "Yerli Diziler", "m3": "Yabancı Diziler", "m4": "TV Programları"}


class PickCategories(unittest.TestCase):
    def cats(self, name, page, tip):
        return [c for _, c in _mod._pick_categories(name, page, tip)]

    def test_film_kaynagi_tur_raflarini_da_verir(self):
        got = self.cats("HDFilmCehennemi", HDFC, "movie")
        self.assertIn("Yeni Eklenen Filmler", got)
        self.assertIn("Aksiyon", got)
        self.assertNotIn("Son Bölümler", got)   # dizi rafı film listesine sızmasın

    def test_dizi_kaynagi_adindan_anlasilir(self):
        # DiziYou'nun hiçbir kategorisi "dizi/film" içermez; eskiden tamamen kayıptı.
        self.assertIn("Aksiyon", self.cats("DiziYou", DIZIYOU, "serie"))
        self.assertEqual(self.cats("DiziYou", DIZIYOU, "movie"), [])

    def test_yerli_yabanci_turlerle_kirlenmez(self):
        self.assertEqual(self.cats("DiziMom", DIZIMOM, "serie_local"), ["Yerli Diziler"])
        self.assertEqual(self.cats("DiziMom", DIZIMOM, "serie_foreign"), ["Yabancı Diziler"])

    def test_kategori_sayisi_sinirli(self):
        self.assertLessEqual(len(self.cats("HDFilmCehennemi", HDFC, "movie")), 6)


if __name__ == "__main__":
    unittest.main()
