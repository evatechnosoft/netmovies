# NetMovies — izleme anahtarı (content_key) sözleşmesi.
#
# Kural: anahtar SİTE-AGNOSTİK **ve TÜR-AGNOSTİK**. Aynı film iki sağlayıcıdan
# ya da media_type gönderen/göndermeyen iki istemciden gelse tek kayıt olmalı;
# aksi hâlde "Devam Et" rafında aynı film iki poster olarak görünüyordu.

import os
import sys
import sqlite3
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

os.environ["WATCH_DB_PATH"] = str(Path(tempfile.mkdtemp()) / "test.db")

from Public.Home.Libs import watch_store  # noqa: E402


class ContentKeyTest(unittest.TestCase):
    def test_tur_anahtari_etkilemez(self):
        """media_type gönderilse de gönderilmese de aynı anahtar."""
        self.assertEqual(
            watch_store.normalize_key("The Gorge", "movie"),
            watch_store.normalize_key("The Gorge", ""),
        )

    def test_site_gurultusu_temizlenir(self):
        self.assertEqual(
            watch_store.normalize_key("İnception (2010) Türkçe Dublaj izle"),
            watch_store.normalize_key("inception 2010 HD"),
        )

    def test_hazir_anahtarin_tur_soneki_kirpilir(self):
        self.assertEqual(watch_store.canonical_key("gorge|movie"), "gorge")
        self.assertEqual(watch_store.canonical_key("dizi|serie"), "dizi")
        self.assertEqual(watch_store.canonical_key("gorge|2025"), "gorge|2025")


class MigrationTest(unittest.TestCase):
    def test_mukerrer_kayit_birlestirilir_en_yeni_kazanir(self):
        path = Path(tempfile.mkdtemp()) / "old.db"
        conn = sqlite3.connect(path)
        conn.row_factory = sqlite3.Row
        conn.executescript(watch_store._SCHEMA)
        conn.executemany(
            "INSERT INTO watch_history (content_key, title, position_seconds, updated_at)"
            " VALUES (?, ?, ?, ?)",
            [("gorge|movie", "The Gorge", 3763, 200), ("gorge", "The Gorge", 2884, 100)],
        )
        conn.commit()

        watch_store._migrate_type_suffix(conn)
        conn.commit()

        rows = conn.execute("SELECT content_key, position_seconds FROM watch_history").fetchall()
        self.assertEqual(len(rows), 1, "aynı film tek kayda inmeli")
        self.assertEqual(rows[0]["content_key"], "gorge")
        self.assertEqual(rows[0]["position_seconds"], 3763, "en son güncellenen konum kalmalı")


if __name__ == "__main__":
    unittest.main()


class RemoveProgressTest(unittest.TestCase):
    # Başka test modülü watch_store'u önce içe aktarırsa WATCH_DB_PATH geç kalır ve
    # yazmalar CANLI veritabanına gider ("bbb" Devam Et'e düştü). Bağlantı burada
    # geçici dosyaya zorlanır, sonra eski hâline döner.
    def setUp(self):
        self._eski = (watch_store._DB_PATH, watch_store._CONN)
        watch_store._DB_PATH = Path(tempfile.mkdtemp()) / "remove.db"
        watch_store._CONN = None

    def tearDown(self):
        if watch_store._CONN is not None:
            watch_store._CONN.close()
        watch_store._DB_PATH, watch_store._CONN = self._eski

    def test_toplu_silme_yalniz_secilenleri_siler(self):
        for ad in ("aaa", "bbb", "ccc"):
            watch_store.upsert_progress(ad, title=ad, position_seconds=60, duration_seconds=600)
        self.assertEqual(watch_store.remove_progress(["aaa", "ccc", "yok", ""]), 2)
        kalan = {r["content_key"] for r in watch_store.list_continue_watching(limit=50)}
        self.assertIn("bbb", kalan)
        self.assertFalse({"aaa", "ccc"} & kalan)

    def test_izlenenler_devam_etin_tersi(self):
        watch_store.upsert_progress("bitti", title="bitti", position_seconds=580, duration_seconds=600)
        watch_store.upsert_progress("yarim", title="yarim", position_seconds=60, duration_seconds=600)
        watch_store.upsert_progress("suresiz", title="suresiz", position_seconds=60, duration_seconds=0)
        izlenen = {r["content_key"] for r in watch_store.list_watched(limit=50)}
        devam = {r["content_key"] for r in watch_store.list_continue_watching(limit=50)}
        self.assertEqual(izlenen, {"bitti"})
        self.assertEqual(devam, {"yarim", "suresiz"})


class YoutubeSiraTest(unittest.TestCase):
    def test_tek_video_sona_dizi_yerinde(self):
        from Public.API.v1.Routers.watch import _youtube_tek_video
        satirlar = [
            {"plugin": "YouTube", "media_type": "movie", "title": "video"},
            {"plugin": "YouTube", "media_type": "serie", "title": "kanal dizisi"},
            {"plugin": "DDizi", "media_type": "serie", "title": "dizi"},
        ]
        satirlar.sort(key=_youtube_tek_video)
        self.assertEqual([s["title"] for s in satirlar], ["kanal dizisi", "dizi", "video"])
