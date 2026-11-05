# Bölüm isteğinde alternatif sağlayıcının film sayfası (bölüm listesi yok) elenir:
# KultFilmler'in "Yeraltı" dublaj filmi dizinin S1E21'i yerine oynuyordu (5 Kas).

import asyncio
import os
import sys
import unittest
from types import SimpleNamespace
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import Core  # noqa: F401 — breaks Routers<->Core import cycle
from Public.API.v1.Routers import resolve_sources as rs


class FilmPlugin:
    async def load_item(self, url):
        return SimpleNamespace(title="Yeraltı", episodes=[])

    async def load_links(self, url):
        return [SimpleNamespace(url="https://x/film.m3u8", name="Dublaj", referer="", user_agent="", subtitles=[])]


class Diag:
    def __init__(self):
        self.entries = []

    def add(self, *a):
        self.entries.append(a)


class BolumdeFilmElemeTest(unittest.TestCase):
    def _run(self, **kw):
        with mock.patch.object(rs.plugin_manager, "select_plugin", return_value=FilmPlugin()), \
             mock.patch.object(rs, "hls_kisa_klip_mi", mock.AsyncMock(return_value=False)), \
             mock.patch.object(rs, "hls_av_orani", mock.AsyncMock(return_value=None)):
            return asyncio.run(rs._links_for("KultFilmler", "https://x/yeralti", 0, Diag(), **kw))

    def test_bolum_isteginde_film_sayfasi_elenir(self):
        sources, _ = self._run(episode_no=21, season_no=1, baslik="Yeraltı")
        self.assertEqual(sources, [])

    def test_film_isteginde_film_sayfasi_kalir(self):
        sources, _ = self._run(baslik="Yeraltı")
        self.assertEqual(len(sources), 1)


if __name__ == "__main__":
    unittest.main()
