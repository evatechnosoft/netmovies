from pathlib import Path
import sys
import unittest


STREAM_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(STREAM_ROOT))

from Public.API.v1.Libs import _cache_key, _normalize_provider_url


class ProviderRoutingTest(unittest.TestCase):
    def test_empty_provider_uses_normalized_fallback(self) -> None:
        self.assertEqual(
            "http://engine:3310",
            _normalize_provider_url("", "http://engine:3310/"),
        )

    def test_remote_provider_is_normalized(self) -> None:
        self.assertEqual(
            "https://stream.watchbuddy.tv",
            _normalize_provider_url(" https://stream.watchbuddy.tv/ "),
        )

    def test_cache_key_is_isolated_by_provider(self) -> None:
        params = {"type": "movie"}
        local = _cache_key("http://engine:3310", "/aggregate_new", params)
        remote = _cache_key("https://stream.watchbuddy.tv", "/aggregate_new", params)

        self.assertNotEqual(local, remote)


if __name__ == "__main__":
    unittest.main()
