"""M3U kaynak birleştirme: süzgeçler, tekilleştirme ve grup eşlemesi."""

import os
import sys
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from Plugins.M3UPlaylist import M3UPlaylist, _normalize_group, _tvg_ulke

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

_SECME = """#EXTM3U
#EXTINF:-1 tvg-id="AMC.us" group-title="Movies",AMC (720p)
https://ornek.us/amc.m3u8
#EXTINF:-1 tvg-id="PlutoTVCineAccion.us" group-title="Movies",Pluto TV Cine Acción
https://ornek.us/accion.m3u8
#EXTINF:-1 tvg-id="AMC.us" group-title="Movies",AMC East (720p)
https://ornek.us/amc-east.m3u8
"""

_KAYNAKLAR = {
    "https://a/tr.m3u":     _TR,
    "https://a/movies.m3u": _MOVIES,
    "https://a/secme.m3u":  _SECME,
}


def _yukle(kaynaklar: str) -> M3UPlaylist:
    """Eklentiyi ağa çıkmadan, sahte listelerle kurar."""

    def sahte_ac(req, timeout=0):
        govde = _KAYNAKLAR[req.full_url].encode()

        class _Yanit:
            def read(self):
                return govde

            def __enter__(self):
                return self

            def __exit__(self, *a):
                return False

        return _Yanit()

    with mock.patch.dict(os.environ, {"M3U_SOURCES": kaynaklar}), \
         mock.patch("urllib.request.urlopen", sahte_ac):
        return M3UPlaylist()


class TvgUlke(unittest.TestCase):
    def test_kod_cikarilir(self):
        self.assertEqual(_tvg_ulke("beINMoviesTurk.tr@SD"), "tr")
        self.assertEqual(_tvg_ulke("ATV.tr"), "tr")

    def test_kodsuz_bos_doner(self):
        self.assertEqual(_tvg_ulke("SomeChannel"), "")
        self.assertEqual(_tvg_ulke(""), "")


class KaynakBirlestirme(unittest.TestCase):
    def test_ulke_suzgeci_yabanciyi_eler(self):
        eklenti = _yukle("https://a/movies.m3u#tr")
        self.assertEqual(
            sorted(it["title"] for it in eklenti._items),
            ["ATV", "beIN Movies Turk"],
        )

    def test_ayni_akis_iki_listede_tek_kez_girer(self):
        eklenti = _yukle("https://a/tr.m3u,https://a/movies.m3u#tr")
        adresler = [it["stream_url"] for it in eklenti._items]
        self.assertEqual(len(adresler), len(set(adresler)))
        self.assertEqual(adresler.count("https://ornek.tr/atv.m3u8"), 1)

    def test_secme_yalniz_beyaz_listeyi_alir(self):
        # 585 kanallık havuzdan yalnız tanınmış markalar, kendi grubuna yazılı.
        eklenti = _yukle("https://a/secme.m3u#secme")
        self.assertEqual(
            [(it["title"], it["group"]) for it in eklenti._items],
            [("AMC (720p)", "Yabancı Film")],
        )

    def test_secme_ayni_kanalin_kopyalarini_tekrarlamaz(self):
        # "AMC" ve "AMC East" aynı tvg-id: rafta tek satır.
        eklenti = _yukle("https://a/secme.m3u#secme")
        self.assertEqual(len(eklenti._items), 1)


class GrupEslemesi(unittest.TestCase):
    """Ulusal/Bölgesel ayrımı — iptv-org bu etiketleri vermiyor, tablo bizim."""

    def test_ana_yayin_kanallari_ulusala_gider(self):
        for ad in ("TRT 1", "ATV", "Kanal D", "Star TV", "Show TV", "TV 8", "NOW TV"):
            self.assertEqual(_normalize_group("General", ad), "Ulusal", ad)

    def test_favori_trtler_ulusala_girmez(self):
        # Dean bunları favorilerine alıyor; ana yayın rafında yer kaplamıyorlar.
        for ad in ("TRT 2", "TRT 3", "TRT Türk", "TRT Avaz", "TRT Kurdî"):
            self.assertEqual(_normalize_group("General", ad), "Genel", ad)

    def test_sehir_yayinlari_bolgesele_ayrilir(self):
        for ad in ("ETV Kayseri", "Kocaeli TV", "Erzurum Web TV", "ATV Alanya", "KANAL 58", "TV 52"):
            self.assertEqual(_normalize_group("General", ad), "Bölgesel", ad)

    def test_ulusal_adlar_plaka_kalibina_yenilmez(self):
        # "Kanal 7" 07 Antalya plakasıyla, "TV 8" 08 Artvin'le çakışıyor.
        self.assertEqual(_normalize_group("General", "Kanal 7"), "Ulusal")
        self.assertEqual(_normalize_group("General", "TV 8"), "Ulusal")

    def test_tematik_gruplara_dokunulmaz(self):
        # Haber/Spor kendi grubunda kalır; bölgesel ayrımı yalnız Genel'e uygulanır.
        self.assertEqual(_normalize_group("News", "Bursa AS TV"), "Haber")
        self.assertEqual(_normalize_group("Sports", "A Spor"), "Spor")

    def test_kalite_ve_yayin_notu_eslemeyi_bozmaz(self):
        # iptv-org adları "ATV (1080p)", "KANAL 58 (720p) [Not 24/7]" biçiminde.
        self.assertEqual(_normalize_group("General", "ATV (1080p)"), "Ulusal")
        self.assertEqual(_normalize_group("General", "TRT 1 (1440p)"), "Ulusal")
        self.assertEqual(_normalize_group("General", "KANAL 58 (720p) [Not 24/7]"), "Bölgesel")


if __name__ == "__main__":
    unittest.main()
