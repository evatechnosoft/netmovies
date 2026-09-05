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
