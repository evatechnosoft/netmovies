# Dizi kaydı sonradan "movie" yazan oynatmayla filme düşmez (Daha 17, 10 Eki).

import os
import sys
import tempfile
import unittest
from pathlib import Path

os.environ["WATCH_DB_PATH"] = str(Path(tempfile.mkdtemp()) / "w.db")
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from Public.Home.Libs import watch_store as ws


class DiziFilmeDusmezTest(unittest.TestCase):
    def test_serie_kalir(self) -> None:
        ws.upsert_progress("daha 17 tip", media_type="serie", episode="S1B18", position_seconds=60, duration_seconds=100)
        ws.upsert_progress("daha 17 tip", media_type="movie", episode="", position_seconds=10, duration_seconds=100)
        self.assertEqual("serie", ws.get_progress("daha 17 tip")["media_type"])

    def test_film_diziye_yukselir(self) -> None:
        ws.upsert_progress("x tip", media_type="movie", position_seconds=60, duration_seconds=100)
        ws.upsert_progress("x tip", media_type="serie", episode="S1B1", position_seconds=60, duration_seconds=100)
        self.assertEqual("serie", ws.get_progress("x tip")["media_type"])


if __name__ == "__main__":
    unittest.main()
