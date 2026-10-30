import unittest
from datetime import datetime, timedelta, timezone

from source_canary import alerts, streaks

NOW = datetime(2026, 10, 30, 3, 0, tzinfo=timezone.utc)


def run(days_ago, **plugins):
    ts = (NOW - timedelta(days=days_ago)).isoformat()
    return {"ts": ts, "plugins": {name: {"items": 2, "ok": ok, "dead": 2 - ok} for name, ok in plugins.items()}}


class SourceCanaryTest(unittest.TestCase):
    def test_dropped_from_catalog_counts_as_dead(self):
        runs = [run(2, A=2, B=1), run(1, A=2), run(0, A=1)]
        self.assertEqual(streaks(runs), {"A": 0, "B": 2})

    def test_alert_fires_once_when_streak_hits_days(self):
        runs = [run(2, A=2, B=1), run(1, A=2, B=0), run(0, A=1, B=0)]
        self.assertEqual(alerts(runs, 2, NOW), ["2 taramadır kaynak vermiyor: B."])
        self.assertEqual(alerts(runs + [run(-1, A=1, B=0)], 2, NOW + timedelta(days=1)), [])

    def test_empty_run_blames_stack_not_sources(self):
        runs = [run(2, A=2, B=1), run(1, A=2, B=1), run(0, A=0, B=0)]
        messages = alerts(runs, 2, NOW)
        self.assertEqual(len(messages), 1)
        self.assertIn("yığın", messages[0])

    def test_stale_history_alerts(self):
        self.assertIn("bayat", alerts([run(2, A=1)], 2, NOW)[0])

    def test_no_history_alerts(self):
        self.assertTrue(alerts([], 2, NOW))


if __name__ == "__main__":
    unittest.main()
