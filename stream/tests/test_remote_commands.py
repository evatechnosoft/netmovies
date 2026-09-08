"""Kumanda komut şeması: telefon TV'ye yalnız bilinen bir eylem geçirebilmeli."""

from pathlib import Path
import sys
import time
import unittest


STREAM_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(STREAM_ROOT))

import Core  # noqa: F401  — uygulamayı önce kurar, dairesel import'u açar
from Public.API.v1.Routers import remote


class RemoteCommandTest(unittest.TestCase):
    def setUp(self) -> None:
        while not remote._queue.empty():
            remote._queue.get_nowait()

    def test_play_needs_plugin_and_url(self) -> None:
        self.assertIsInstance(remote.build_command({"type": "play", "url": "x"}), str)
        self.assertIsInstance(remote.build_command({"type": "play", "plugin": "DiziPal"}), str)

    def test_play_is_the_default_type_for_old_clients(self) -> None:
        cmd = remote.build_command({"plugin": "DiziPal", "url": "https://x/y"})
        self.assertEqual("play", cmd["type"])

    def test_unknown_key_is_rejected(self) -> None:
        self.assertIsInstance(remote.build_command({"type": "key", "key": "POWER_OFF"}), str)
        self.assertEqual("UP", remote.build_command({"type": "key", "key": "up"})["key"])

    def test_unknown_type_is_rejected(self) -> None:
        self.assertIsInstance(remote.build_command({"type": "shell"}), str)

    def test_transport_value_must_be_numeric(self) -> None:
        self.assertIsInstance(remote.build_command({"type": "transport", "action": "seek", "value": "abc"}), str)
        self.assertEqual(-10.0, remote.build_command({"type": "transport", "action": "seek", "value": -10})["value"])

    def test_text_uses_its_own_field_not_value(self) -> None:
        # `value` transport'ta sayısal; aynı ada iki tip sığmadığı için metin
        # ayrı alanda taşınır (istemci modeli tek şema kullanıyor).
        cmd = remote.build_command({"type": "text", "text": "sıcak kafa", "submit": "true"})
        self.assertEqual("sıcak kafa", cmd["text"])
        self.assertTrue(cmd["submit"])
        self.assertNotIn("value", cmd)

    def test_full_queue_drops_oldest_not_newest(self) -> None:
        # Kumandada son basılan tuş, birikmiş eski tuşlardan değerlidir.
        for i in range(remote._QUEUE_MAX + 5):
            remote.enqueue({"type": "key", "key": "UP", "n": i})

        self.assertEqual(remote._QUEUE_MAX, remote._queue.qsize())
        self.assertEqual(remote._QUEUE_MAX + 4, remote._queue._queue[-1]["n"])

    def test_stale_commands_are_skipped(self) -> None:
        remote.enqueue({"type": "key", "key": "UP", "n": "bayat"})
        remote._queue._queue[0]["sent_at"] = int(time.time()) - remote._TTL_SECONDS - 1
        remote.enqueue({"type": "key", "key": "DOWN", "n": "taze"})

        self.assertEqual("taze", remote._next_fresh()["n"])


if __name__ == "__main__":
    unittest.main()
