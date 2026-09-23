# Kayıtlı adres çürüdüğünde detay ekranı "bulunamadı" demeden kurtarılmalı.
#
# İzlenecek/takip listeleri sağlayıcı + adres ANLIK GÖRÜNTÜSÜ tutuyor. Sağlayıcı
# domain değiştirince (SezonlukDizi öldü, HDFilmCehennemi .now -> .land) ya da
# kayıt dizi sayfası yerine bölüm sayfasını tutuyorsa (DiziPal `/2-sezon/1-bolum`)
# kart kalıcı olarak açılmaz hale geliyordu. Gerçek olay: 23 Eylül, Dean'in
# izlenecekler listesindeki beş dizinin beşi de açılmıyordu.

import asyncio
import sys
import unittest
from pathlib import Path
from urllib.parse import quote_plus

STREAM_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(STREAM_ROOT))

from Public.API.v1.Routers import load_item as router


class LoadItemRescueTest(unittest.TestCase):
    def setUp(self) -> None:
        self._gercek = router.fuck_dmca

    def tearDown(self) -> None:
        router.fuck_dmca = self._gercek

    def _sahte(self, load_item_sonuc):
        """Ölü sağlayıcı + başlıkta içeriği olan canlı sağlayıcı."""
        cagrilar: list[tuple[str, dict]] = []

        async def sahte(endpoint, params=None, timeout=None, client_headers=None):
            cagrilar.append((endpoint, params or {}))
            if endpoint == "/get_plugin_names":
                return ["OluKaynak", "CanliKaynak"]
            if endpoint == "/search":
                if (params or {}).get("plugin") != "CanliKaynak":
                    return []
                return [{"title": "Pluribus", "url": quote_plus("https://canli/dizi/pluribus")}]
            if endpoint == "/load_item":
                if (params or {}).get("plugin") == "CanliKaynak":
                    return {"title": "Pluribus", "episodes": [{"season": 1, "episode": 1}]}
                if isinstance(load_item_sonuc, Exception):
                    raise load_item_sonuc
                return load_item_sonuc
            return None

        router.fuck_dmca = sahte
        return cagrilar

    def _kurtar(self, load_item_sonuc):
        cagrilar = self._sahte(load_item_sonuc)
        sonuc = asyncio.run(router._kurtar("Pluribus", "OluKaynak", "serie", {}))
        return sonuc, cagrilar

    def test_olu_saglayici_baslikla_kurtarilir(self) -> None:
        sonuc, cagrilar = self._kurtar(ValueError("provider 500"))
        self.assertEqual("Pluribus", (sonuc or {}).get("title"))
        # Adres HAM gitmeli: arama sonucu quote_plus kodlu gelir, httpx bir kez daha
        # kodlarsa motor %253A görüp 500 döner.
        kurtarma = [p for e, p in cagrilar if e == "/load_item" and p.get("plugin") == "CanliKaynak"]
        self.assertEqual("https://canli/dizi/pluribus", kurtarma[0]["encoded_url"])

    def test_olu_saglayici_tekrar_sorgulanmaz(self) -> None:
        _, cagrilar = self._kurtar(ValueError("provider 500"))
        self.assertNotIn("OluKaynak", [p.get("plugin") for e, p in cagrilar if e == "/search"])

    def test_bolumsuz_dizi_detayi_curuk_sayilir(self) -> None:
        # Bölüm sayfası kaydı: istek BAŞARILI ama bölüm listesi yok.
        self.assertFalse(router._kullanilabilir({"title": "MobLand", "url": "x"}, "serie"))
        # Film sayfasında bölüm listesi zaten olmaz — o kullanılabilir.
        self.assertTrue(router._kullanilabilir({"title": "Dune", "url": "x"}, "movie"))

    def test_alakasiz_eslesme_kurtarma_sayilmaz(self) -> None:
        async def sahte(endpoint, params=None, timeout=None, client_headers=None):
            if endpoint == "/get_plugin_names":
                return ["CanliKaynak"]
            if endpoint == "/search":
                return [{"title": "Abi", "url": quote_plus("https://canli/dizi/abi")}]
            if endpoint == "/load_item":
                return {"title": "Abi", "episodes": [{"season": 1, "episode": 1}]}
            return None

        router.fuck_dmca = sahte
        self.assertIsNone(asyncio.run(router._kurtar("R.J. Decker", "OluKaynak", "serie", {})))


if __name__ == "__main__":
    unittest.main()
