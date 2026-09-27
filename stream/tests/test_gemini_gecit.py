# NetMovies — Gemini kapısı: YZ geçidi yolu ve doğrudan yol.
#
# YZ_GECIT_URL doluysa istek OpenAI biçiminde geçide gider; boşsa Google'a.
# Ağ sahte (httpx.MockTransport) — gerçek çağrı yok.

import asyncio
import json
import os
import sys
import unittest
from pathlib import Path
from unittest import mock

import httpx

os.environ["AUTH_USER"] = ""
os.environ["AUTH_PASS"] = ""
os.environ["ADMIN_PASS"] = ""

STREAM_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(STREAM_ROOT))

from Public.API.v1.Libs import gemini

_GERCEK_ISTEMCI = httpx.AsyncClient


def _sahte_istemci(isleyici, istekler: list):
    def yakala(istek: httpx.Request) -> httpx.Response:
        istekler.append(istek)
        return isleyici(istek)

    def uret(*_, **kw):
        return _GERCEK_ISTEMCI(transport=httpx.MockTransport(yakala), timeout=kw.get("timeout"))
    return uret


def _ayar(ad, env_ad, varsayilan=""):
    return {"gemini_api_key": "anahtar"}.get(ad, varsayilan)


class GecitTest(unittest.TestCase):
    def _sor(self, isleyici, env: dict, parcalar=None):
        istekler: list = []
        with mock.patch.dict(os.environ, env), \
             mock.patch.object(gemini, "ayar", side_effect=_ayar), \
             mock.patch.object(gemini.httpx, "AsyncClient", side_effect=_sahte_istemci(isleyici, istekler)):
            sonuc = asyncio.run(gemini.sor(
                sistem="sistem", parcalar=parcalar or [{"text": "The Odyssey"}],
                sema={"type": "OBJECT"},
            ))
        return sonuc, istekler

    def test_gecit_openai_bicimi(self):
        def isleyici(_):
            return httpx.Response(200, json={"choices": [{"message": {
                "content": '```json\n{"basliklar": ["Odyssey"]}\n```'}}]})

        (cikti, hata), istekler = self._sor(isleyici, {"YZ_GECIT_URL": "http://gecit:4000/v1/"})
        self.assertEqual((cikti, hata), ({"basliklar": ["Odyssey"]}, ""))
        self.assertEqual(str(istekler[0].url), "http://gecit:4000/v1/chat/completions")
        govde = json.loads(istekler[0].content)
        self.assertEqual(govde["model"], "gemini")
        self.assertEqual(govde["messages"][1], {"role": "user", "content": "The Odyssey"})
        self.assertIn("OBJECT", govde["messages"][0]["content"])  # şema sistem metninde
        self.assertNotIn("x-goog-api-key", istekler[0].headers)  # anahtar geçitte

    def test_gecit_hatasi_none(self):
        (cikti, hata), _ = self._sor(lambda _: httpx.Response(429, text="kota"),
                                     {"YZ_GECIT_URL": "http://gecit:4000/v1"})
        self.assertIsNone(cikti)
        self.assertIn("429", hata)

    def test_gecit_bozuk_yanit_none(self):
        (cikti, _), _ = self._sor(lambda _: httpx.Response(200, json={"choices": []}),
                                  {"YZ_GECIT_URL": "http://gecit:4000/v1"})
        self.assertIsNone(cikti)

    def test_bos_gecit_dogrudan_google(self):
        def isleyici(_):
            return httpx.Response(200, json={"candidates": [{"content": {"parts": [
                {"text": '{"basliklar": []}'}]}}]})

        (cikti, _), istekler = self._sor(isleyici, {"YZ_GECIT_URL": ""})
        self.assertEqual(cikti, {"basliklar": []})
        self.assertIn("generativelanguage.googleapis.com", str(istekler[0].url))
        self.assertEqual(istekler[0].headers["x-goog-api-key"], "anahtar")

    def test_ses_parcasi_gecide_gitmez(self):
        def isleyici(_):
            return httpx.Response(200, json={"candidates": [{"content": {"parts": [{"text": "{}"}]}}]})

        _, istekler = self._sor(isleyici, {"YZ_GECIT_URL": "http://gecit:4000/v1"},
                                parcalar=[{"inline_data": {"mime_type": "audio/webm", "data": "AA=="}}])
        self.assertIn("generativelanguage.googleapis.com", str(istekler[0].url))

    def test_gecit_varsa_anahtar_gerekmez(self):
        with mock.patch.dict(os.environ, {"YZ_GECIT_URL": "http://gecit:4000/v1"}), \
             mock.patch.object(gemini, "ayar", return_value=""):
            self.assertTrue(gemini.anahtar_var())
        with mock.patch.dict(os.environ, {"YZ_GECIT_URL": ""}), \
             mock.patch.object(gemini, "ayar", return_value=""):
            self.assertFalse(gemini.anahtar_var())


if __name__ == "__main__":
    unittest.main()
