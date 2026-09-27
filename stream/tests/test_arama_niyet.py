# Arama niyeti: işaret sözcükleri aramadan ayıklanır, sonuç tekilleşir ve niyete
# göre sıralanır. Örnekler canlı sunucudaki "resident evil" yanıtından (2026-09-27).

import sys
import unittest
from pathlib import Path

STREAM_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(STREAM_ROOT))

from Public.API.v1.Libs.arama_niyet import (
    DIL_ALTYAZI, DIL_DUBLAJ, SIRA_SERI, SIRA_SON, Niyet, ayristir, sirala, tekillestir, yapim_yili,
)


class AyristirTest(unittest.TestCase):
    def test_isaretsiz_sorgu_aynen_kalir(self) -> None:
        self.assertEqual(Niyet(baslik="Resident Evil"), ayristir("  Resident   Evil "))

    def test_dublaj_ve_turkce(self) -> None:
        self.assertEqual(Niyet(baslik="Resident Evil", dil=DIL_DUBLAJ), ayristir("Resident Evil dublaj"))
        self.assertEqual(Niyet(baslik="resident evil", dil=DIL_DUBLAJ), ayristir("resident evil dublaj türkçe"))
        self.assertEqual(DIL_DUBLAJ, ayristir("Türkçe Dublaj Reacher").dil)
        self.assertEqual("Reacher", ayristir("Türkçe Dublaj Reacher").baslik)
        self.assertEqual(DIL_DUBLAJ, ayristir("reacher tr").dil)

    def test_altyazi(self) -> None:
        niyet = ayristir("Reacher türkçe altyazılı")
        self.assertEqual(("Reacher", DIL_ALTYAZI), (niyet.baslik, niyet.dil))

    def test_yil_ve_surumu(self) -> None:
        self.assertEqual(Niyet(baslik="resident evil", yil=2026), ayristir("resident evil 2026"))
        self.assertEqual(Niyet(baslik="Resident Evil", yil=2026), ayristir("Resident Evil 2026 sürümü"))
        self.assertEqual(Niyet(baslik="Dune", yil=2021), ayristir("Dune (2021)"))

    def test_basliktaki_sayi_yil_degil(self) -> None:
        self.assertEqual(Niyet(baslik="Blade Runner 2049"), ayristir("Blade Runner 2049"))
        # Yalnız yıldan ibaret sorgu başlıktır ("1917" filmi).
        self.assertEqual(Niyet(baslik="1917"), ayristir("1917"))

    def test_seri(self) -> None:
        self.assertEqual(Niyet(baslik="resident evil", sira=SIRA_SERI), ayristir("resident evil tüm seri"))
        self.assertEqual(Niyet(baslik="resident evil", sira=SIRA_SERI), ayristir("resident evil hepsi"))
        self.assertEqual(Niyet(baslik="Resident Evil", sira=SIRA_SERI), ayristir("Resident Evil serisi"))

    def test_son_ve_en_yeni(self) -> None:
        self.assertEqual(Niyet(baslik="resident evil", sira=SIRA_SON), ayristir("resident evil en yeni"))
        self.assertEqual(Niyet(baslik="resident evil", sira=SIRA_SON), ayristir("resident evil yenisi"))
        self.assertEqual(Niyet(baslik="resident evil", sira=SIRA_SON), ayristir("en son resident evil"))

    def test_basliktaki_son_ve_yeni_korunur(self) -> None:
        # Tek sözcüklük sıra işareti yalnız sonda sayılır.
        self.assertEqual(Niyet(baslik="Yeni Gelin"), ayristir("Yeni Gelin"))
        self.assertEqual(Niyet(baslik="Son Yaz"), ayristir("Son Yaz"))
        self.assertEqual(Niyet(baslik="yeni"), ayristir("yeni"))

    def test_hepsi_birden(self) -> None:
        niyet = ayristir("resident evil türkçe dublaj 2026 en yeni")
        self.assertEqual(Niyet(baslik="resident evil", dil=DIL_DUBLAJ, yil=2026, sira=SIRA_SON), niyet)


class YilTest(unittest.TestCase):
    def test_adres_kisa_adindaki_yil(self) -> None:
        oge = {"title": "Ölümcül Deney 2: Kıyamet", "url": "https%3A%2F%2Fx.land%2Ffilm%2Folumcul-deney-2-kiyamet-2004-izle-8%2F"}
        self.assertEqual(2004, yapim_yili(oge, 2026))

    def test_basliktaki_sayi_adreste_yil_sayilmaz(self) -> None:
        oge = {"title": "Blade Runner 2049", "url": "https://x/film/blade-runner-2049-izle/"}
        self.assertEqual(2017, yapim_yili(oge, 2017))

    def test_yukleme_tarihi_tmdb_yilini_tavanlar(self) -> None:
        # DiziMom 2022 dizisi; TMDB aynı adlı 2026 filmini eşleştiriyor.
        oge = {"title": "Resident Evil", "url": "https://dizimom/diziler/resident-evil-izle/",
               "poster": "https://www.dizimom.cam/wp-content/uploads/2022/06/resident-evil.jpg"}
        self.assertEqual(2022, yapim_yili(oge, 2026))
        self.assertEqual(2021, yapim_yili(oge, 2021))

    def test_dizi_adresine_film_yili_yazilmaz(self) -> None:
        oge = {"title": "Resident Evil", "url": "https://www.dizibox.live/diziler/resident-evil-hd/"}
        self.assertIsNone(yapim_yili(oge, 2026, "movie"))
        self.assertEqual(2022, yapim_yili(oge, 2022, "tv"))

    def test_bilinmeyen(self) -> None:
        self.assertIsNone(yapim_yili({"title": "X", "url": "https://x/film/x/"}))


class TekillestirTest(unittest.TestCase):
    def test_ayni_baslik_ayni_yil_tek_kart(self) -> None:
        kartlar = tekillestir([
            {"title": "Resident Evil", "plugin": "DiziMom", "url": "a", "year": 2022},
            {"title": "Resident Evil", "plugin": "DiziMom", "url": "b", "year": 2022},
            {"title": "Resident Evil", "plugin": "DiziBox", "url": "c", "year": 2022},
        ])
        self.assertEqual(1, len(kartlar))
        self.assertEqual(["DiziMom", "DiziBox"], [p["plugin"] for p in kartlar[0]["providers"]])
        # Temsilci ilk gelen (puanı en yüksek) sağlayıcı.
        self.assertEqual("a", kartlar[0]["url"])

    def test_farkli_yil_birlesmez(self) -> None:
        kartlar = tekillestir([
            {"title": "Ölümcül Deney - Resident Evil", "plugin": "HDFilmCehennemi", "url": "a", "year": 2002},
            {"title": "Resident Evil", "plugin": "DiziMom", "url": "b", "year": 2022},
        ])
        self.assertEqual(2, len(kartlar))

    def test_tr_en_parcasi_ve_eksik_yil(self) -> None:
        kartlar = tekillestir([
            {"title": "Ölümcül Deney 6: Son Bölüm - Resident Evil: The Final Chapter",
             "plugin": "HDFilmCehennemi", "url": "a", "year": 2016},
            {"title": "Ölümcül Deney 6 - Resident Evil The Final Chapter", "plugin": "FullHDFilmizlesene", "url": "b"},
        ])
        self.assertEqual(1, len(kartlar))
        self.assertEqual(2, len(kartlar[0]["providers"]))

    def test_kisa_ortak_parca_yilsiz_birlesmez(self) -> None:
        # "resident evil" hem film hem dizi olabilir: yılı bilinmeyen tarafla birleşmez.
        kartlar = tekillestir([
            {"title": "Ölümcül Deney - Resident Evil", "plugin": "HDFilmCehennemi", "url": "a", "year": 2002},
            {"title": "Resident Evil", "plugin": "DiziYou", "url": "b"},
        ])
        self.assertEqual(2, len(kartlar))

    def test_eksik_yil_gruptan_dolar(self) -> None:
        kartlar = tekillestir([
            {"title": "Reacher", "plugin": "A", "url": "a"},
            {"title": "Reacher", "plugin": "B", "url": "b", "lang": ["DUB"]},
        ])
        self.assertEqual(["DUB"], kartlar[0]["lang"])


def _kart(baslik: str, yil: int | None = None, lang: list[str] | None = None) -> dict:
    kart: dict = {"title": baslik}
    if yil:
        kart["year"] = yil
    if lang:
        kart["lang"] = lang
    return kart


_RE = [
    _kart("Kiracı - The Resident", 2011),
    _kart("Ölümcül Deney - Resident Evil", 2002),
    _kart("Resident Evil: Death Island"),
    _kart("Ölümcül Deney 6: Son Bölüm - Resident Evil: The Final Chapter", 2016),
    _kart("Resident Evil", 2022, ["DUB"]),
    _kart("Ölümcül Deney 2: Kıyamet - Resident Evil: Apocalypse", 2004, ["ALT"]),
    _kart("Resident Evil: Raccoon Şehri", 2021),
]


def _basliklar(kartlar: list[dict]) -> list[str]:
    return [k["title"] for k in kartlar]


class SiralaTest(unittest.TestCase):
    def test_varsayilan_en_yeni_basta_aile_disi_sonda(self) -> None:
        sonuc = _basliklar(sirala(_RE, Niyet(baslik="resident evil")))
        self.assertEqual([
            "Resident Evil",
            "Resident Evil: Raccoon Şehri",
            "Ölümcül Deney 6: Son Bölüm - Resident Evil: The Final Chapter",
            "Ölümcül Deney 2: Kıyamet - Resident Evil: Apocalypse",
            "Ölümcül Deney - Resident Evil",
            "Resident Evil: Death Island",
            "Kiracı - The Resident",
        ], sonuc)

    def test_seri_yila_gore_artan(self) -> None:
        sonuc = [k.get("year") for k in sirala(_RE, Niyet(baslik="resident evil", sira=SIRA_SERI))]
        self.assertEqual([2002, 2004, 2016, 2021, 2022, None, 2011], sonuc)

    def test_yil_isabeti_basta(self) -> None:
        sonuc = sirala(_RE, Niyet(baslik="resident evil", yil=2004))
        self.assertEqual(2004, sonuc[0]["year"])

    def test_dublaj_once_sonra_altyazi(self) -> None:
        sonuc = sirala(_RE, Niyet(baslik="resident evil", dil=DIL_DUBLAJ))
        self.assertEqual([["DUB"], ["ALT"]], [k.get("lang") for k in sonuc[:2]])

    def test_basliktaki_dublaj_etiketi(self) -> None:
        sonuc = sirala([_kart("Reacher"), _kart("Reacher Türkçe Dublaj")], Niyet(baslik="reacher", dil=DIL_DUBLAJ))
        self.assertEqual("Reacher Türkçe Dublaj", sonuc[0]["title"])


if __name__ == "__main__":
    unittest.main()
