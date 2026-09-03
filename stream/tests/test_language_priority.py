# NetMovies — kaynak dili önceliği sözleşmesi.
#
# Kural (Dean'in isteği): "önce dublaj, o yoksa Türkçe altyazı".
# Bu kural web (izle.py), API (/api/v1/load_links) ve TV istemcisinde AYNI
# olmalı; burada sunucu tarafı sabitlenir, TV tarafı SourceResolverTest'te.

import sys
import unittest
from pathlib import Path

STREAM_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(STREAM_ROOT))

from Public.API.v1.Libs.language import (
    RANK_DUBBED,
    RANK_TR_SUB,
    RANK_UNKNOWN,
    language_name,
    language_rank,
    order_by_language,
)


class LanguagePriorityTest(unittest.TestCase):
    def test_dubbed_source_ranks_first(self) -> None:
        self.assertEqual(RANK_DUBBED, language_rank({"name": "DiziBox · Türkçe Dublaj"}))

    def test_subtitled_source_ranks_second(self) -> None:
        self.assertEqual(RANK_TR_SUB, language_rank({"name": "DiziBox · Türkçe Altyazılı"}))

    def test_turkish_subtitle_track_counts(self) -> None:
        link = {"name": "Dizilla · Oynatıcı", "subtitles": [{"name": "Türkçe", "url": "x.vtt"}]}

        self.assertEqual(RANK_TR_SUB, language_rank(link))

    def test_unlabelled_source_ranks_last(self) -> None:
        self.assertEqual(RANK_UNKNOWN, language_rank({"name": "RecTV · Oynatıcı"}))

    def test_queue_order_is_dubbed_then_subtitled_then_rest(self) -> None:
        queue = [
            {"name": "A · Oynatıcı"},
            {"name": "B · Türkçe Altyazılı"},
            {"name": "C · Türkçe Dublaj"},
        ]

        self.assertEqual(
            ["C · Türkçe Dublaj", "B · Türkçe Altyazılı", "A · Oynatıcı"],
            [link["name"] for link in order_by_language(queue)],
        )

    def test_same_rank_keeps_provider_order(self) -> None:
        queue = [{"name": "İlk · Dublaj"}, {"name": "İkinci · Dublaj"}]

        self.assertEqual(
            ["İlk · Dublaj", "İkinci · Dublaj"],
            [link["name"] for link in order_by_language(queue)],
        )

    def test_rank_names_are_user_facing(self) -> None:
        self.assertEqual("Türkçe dublaj", language_name(RANK_DUBBED))
        self.assertEqual("Türkçe altyazı", language_name(RANK_TR_SUB))
        self.assertEqual("dil bilinmiyor", language_name(RANK_UNKNOWN))

    def test_missing_fields_do_not_crash(self) -> None:
        self.assertEqual(RANK_UNKNOWN, language_rank({}))
        self.assertEqual(RANK_UNKNOWN, language_rank({"name": None, "subtitles": None}))


if __name__ == "__main__":
    unittest.main()
