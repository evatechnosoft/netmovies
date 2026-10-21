import os
import sqlite3
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from Public.Home.Libs import watch_store as ws


class _TempDb(unittest.TestCase):
    """Her test kendi DB'sini kullanır; watch_store tekil bağlantısını yeniden kurar."""

    def setUp(self) -> None:
        self._old = (ws._DB_PATH, ws._CONN)
        ws._DB_PATH = Path(tempfile.mkdtemp()) / "p.db"
        ws._CONN = None
        ws._PIN_FAILS.clear()

    def tearDown(self) -> None:
        if ws._CONN is not None:
            ws._CONN.close()
        ws._DB_PATH, ws._CONN = self._old

    def as_profile(self, profile: str):
        token = ws.CURRENT_PROFILE.set(profile)
        self.addCleanup(ws.CURRENT_PROFILE.reset, token)


class ProfilAyrimiTest(_TempDb):
    def test_gecmis_profile_ozel(self) -> None:
        self.as_profile("dean")
        ws.upsert_progress("film a", title="A", position_seconds=10, duration_seconds=100)
        ws.add_favorite("film a", title="A")
        ws.toggle_user_list("film a", "izlenecek", title="A")

        self.as_profile("eva")
        self.assertEqual([], ws.list_continue_watching())
        self.assertEqual([], ws.list_favorites())
        self.assertEqual([], ws.list_user_list("izlenecek"))
        self.assertIsNone(ws.get_progress("film a"))
        # Aynı içerik iki profilde ayrı satır: Eva'nın kaydı Dean'inkini ezmez.
        ws.upsert_progress("film a", title="A", position_seconds=50, duration_seconds=100)

        self.as_profile("dean")
        self.assertEqual(10, ws.get_progress("film a")["position_seconds"])
        self.assertEqual(1, len(ws.list_favorites()))

    def test_varsayilan_profil_dean(self) -> None:
        self.assertEqual("dean", ws.CURRENT_PROFILE.get())


class EskiVeriDeaneTasinirTest(_TempDb):
    def test_eski_sema_dean_profiline_gecer(self) -> None:
        ws._DB_PATH.parent.mkdir(parents=True, exist_ok=True)
        old = sqlite3.connect(str(ws._DB_PATH))
        old.executescript("""
            CREATE TABLE watch_history (content_key TEXT PRIMARY KEY, plugin TEXT, title TEXT,
              poster TEXT, media_type TEXT, episode TEXT, position_seconds REAL,
              duration_seconds REAL, updated_at INTEGER);
            CREATE TABLE favorites (content_key TEXT PRIMARY KEY, plugin TEXT, title TEXT,
              poster TEXT, media_type TEXT, added_at INTEGER);
            CREATE TABLE user_lists (content_key TEXT NOT NULL, list_name TEXT NOT NULL,
              plugin TEXT, title TEXT, poster TEXT, media_type TEXT, added_at INTEGER,
              PRIMARY KEY (content_key, list_name));
            INSERT INTO watch_history VALUES ('eski','p','Eski','', 'movie','',5,100,1);
            INSERT INTO favorites VALUES ('fav','p','Fav','', 'movie',1);
            INSERT INTO user_lists VALUES ('lst','izlenecek','p','Lst','', 'movie',1);
        """)
        old.commit()
        old.close()

        self.as_profile("dean")
        self.assertEqual(["eski"], [r["content_key"] for r in ws.list_continue_watching()])
        self.assertEqual(["fav"], [r["content_key"] for r in ws.list_favorites()])
        self.assertEqual(["lst"], [r["content_key"] for r in ws.list_user_list("izlenecek")])
        self.as_profile("eva")
        self.assertEqual([], ws.list_continue_watching())


class CihazVePinTest(_TempDb):
    def test_dort_profil_eva_cocuk(self) -> None:
        profiles = {p["id"]: p for p in ws.list_profiles()}
        self.assertEqual(["dean", "eva", "profil3", "profil4"], list(profiles))
        self.assertTrue(profiles["eva"]["kid"])
        self.assertNotIn("pin_hash", profiles["dean"])

    def test_yeni_cihaz_atanmamis_kaydolur(self) -> None:
        self.assertIsNone(ws.device_profile("tv-1", label="Projektor"))
        self.assertEqual(["tv-1"], [d["device_id"] for d in ws.list_devices()])
        ws.assign_device("tv-1", "eva")
        self.assertEqual("eva", ws.device_profile("tv-1"))

    def test_atanmamis_cihaz_kendi_gecmisi(self) -> None:
        self.assertEqual("cihaz:tv-9", ws.profile_for_device("tv-9"))
        ws.assign_device("tv-9", "eva")
        self.assertEqual("eva", ws.profile_for_device("tv-9"))

    def test_model_sablonu_yeni_cihazi_atar(self) -> None:
        ws.assign_device("model:MIBOX4", "dean")
        self.assertEqual("dean", ws.profile_for_device("mibox-yeni", "MIBOX4"))
        self.assertEqual("cihaz:proj-1", ws.profile_for_device("proj-1", "Projektor"))

    def test_pin_ile_profil_bulunur(self) -> None:
        ws.set_profile("dean", pin="112233")
        ws.set_profile("eva", pin="5555")
        self.assertEqual(("bad_pin", None), ws.claim_by_pin("tel-1", "000000"))
        self.assertEqual("cihaz:tel-1", ws.profile_for_device("tel-1"))
        self.assertEqual(("ok", "dean"), ws.claim_by_pin("tel-1", "112233"))
        self.assertEqual("dean", ws.profile_for_device("tel-1"))
        self.assertEqual(("ok", "eva"), ws.claim_by_pin("tab-1", "5555"))

    def test_pin_ile_bulma_kilitlenir(self) -> None:
        ws.set_profile("dean", pin="112233")
        for _ in range(ws.MAX_PIN_FAILS):
            ws.claim_by_pin("x", "999999")
        self.assertEqual(("locked", None), ws.claim_by_pin("x", "112233"))

    def test_pin_dogru_yanlis_kilit(self) -> None:
        ws.set_profile("dean", pin="4321")
        self.assertEqual("bad_pin", ws.select_profile("tv-2", "dean", "0000"))
        self.assertIsNone(ws.device_profile("tv-2"))
        self.assertEqual("ok", ws.select_profile("tv-2", "dean", "4321"))
        self.assertEqual("dean", ws.device_profile("tv-2"))

        for _ in range(ws.MAX_PIN_FAILS):
            ws.select_profile("tv-3", "dean", "1111")
        # Kilitliyken doğru PIN de kabul edilmez (kaba kuvvet freni).
        self.assertEqual("locked", ws.select_profile("tv-3", "dean", "4321"))

    def test_pinsiz_cocuk_profili(self) -> None:
        self.assertEqual("ok", ws.select_profile("tv-4", "eva", ""))
        self.assertEqual("eva", ws.device_profile("tv-4"))

    def test_pinsiz_yetiskin_profili_secilemez(self) -> None:
        # PIN ayarlanmamış yetişkin profili açık kapı olmasın.
        self.assertEqual("bad_pin", ws.select_profile("tv-5", "profil3", ""))

    def test_bilinmeyen_profil(self) -> None:
        self.assertEqual("unknown", ws.select_profile("tv-6", "yok", "1234"))


if __name__ == "__main__":
    unittest.main()
