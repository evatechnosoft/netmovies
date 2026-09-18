# Poster rozeti: çözümlemenin yan ürünü hatırlanmalı, hatırlanan da bozulmamalı.

import json
import sys
import tempfile
import unittest
from pathlib import Path

STREAM_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(STREAM_ROOT))

import os

from Public.API.v1.Libs import lang_memo
from Public.API.v1.Libs.language import (
    RANK_DUBBED,
    RANK_ORIGINAL,
    RANK_TR_SUB,
    RANK_UNKNOWN,
    language_rank,
)


class LanguageRankTest(unittest.TestCase):
    def test_provider_original_is_not_unknown(self) -> None:
        # DiziPal/DiziYou'nun gerçek kaynak adları (chain_scan, 18 Eylül).
        self.assertEqual(RANK_ORIGINAL, language_rank({"name": "DiziPal | Orijinal"}))
        self.assertEqual(RANK_ORIGINAL, language_rank({"name": "DiziYou · Orijinal Dil"}))

    def test_subtitle_wins_over_original(self) -> None:
        self.assertEqual(RANK_TR_SUB, language_rank({"name": "Orijinal · Türkçe altyazı"}))

    def test_dub_and_unknown_unchanged(self) -> None:
        self.assertEqual(RANK_DUBBED, language_rank({"name": "Dublaj · VidMoly"}))
        self.assertEqual(RANK_UNKNOWN, language_rank({"name": "DiziPal | Kaynak"}))


class LangMemoTest(unittest.TestCase):
    def setUp(self) -> None:
        self.dizin = tempfile.TemporaryDirectory()
        os.environ["LANG_MEMO_PATH"] = str(Path(self.dizin.name) / "lang_memo.json")

    def tearDown(self) -> None:
        os.environ.pop("LANG_MEMO_PATH", None)
        self.dizin.cleanup()

    def test_badges_are_remembered_by_title(self) -> None:
        lang_memo.kaydet("Neagley", [RANK_DUBBED, RANK_TR_SUB, RANK_UNKNOWN])

        # Anahtar büyük/küçük harf ve boşluktan bağımsız.
        self.assertEqual(["ALT", "DUB"], lang_memo.rozetler()[lang_memo.anahtar("  neagley ")])

    def test_unknown_only_leaves_no_badge(self) -> None:
        self.assertEqual([], lang_memo.kaydet("Sultana", [RANK_UNKNOWN]))
        self.assertEqual({}, lang_memo.rozetler())

    def test_expired_record_is_hidden(self) -> None:
        lang_memo.kaydet("Lanterns", [RANK_ORIGINAL], simdi=0.0)

        self.assertEqual({}, lang_memo.rozetler(simdi=lang_memo.OMUR_SN + 1))

    def test_corrupt_store_is_survivable(self) -> None:
        Path(os.environ["LANG_MEMO_PATH"]).write_text("{bozuk", encoding="utf-8")

        self.assertEqual({}, lang_memo.rozetler())
        self.assertEqual(["DUB"], lang_memo.kaydet("Reacher", [RANK_DUBBED]))
        self.assertIn("reacher", json.loads(Path(os.environ["LANG_MEMO_PATH"]).read_text(encoding="utf-8")))


if __name__ == "__main__":
    unittest.main()
