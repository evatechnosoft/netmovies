# NetMovies — /api/v1 gateway sözleşme testleri.
#
# Amaç: web ve TV istemcisinin paylaştığı gateway sözleşmesini (parametre adları,
# upstream yolu, cache davranışı, hata zarfı) regresyona karşı sabitlemek.
# Gerçek kaynak sitelere çıkılmaz; upstream provider MockTransport ile taklit edilir.
#
# Çalıştırma (container içinde, cwd=/usr/src/Stream):
#   docker compose exec -T stream python -m unittest discover -s tests

import os
import sys
import unittest
from pathlib import Path
from typing import Any

# Core import edilmeden ÖNCE auth kapatılmalı: middleware AUTH_USER'ı modül
# yüklenirken okuyor, sonradan değiştirmek etkisiz kalıyor.
os.environ["AUTH_USER"] = ""
os.environ["AUTH_PASS"] = ""
os.environ["ADMIN_PASS"] = ""

STREAM_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(STREAM_ROOT))

import httpx
from fastapi.testclient import TestClient

from Public.API.v1 import Libs
from Core import kekik_FastAPI


class FakeProvider:
    """Upstream KekikStream provider'ının yerine geçen kaydedici taklit."""

    def __init__(self) -> None:
        self.requests: list[httpx.Request] = []
        self.result: Any = {"ok": True}
        self.status_code: int = 200

    def handler(self, request: httpx.Request) -> httpx.Response:
        self.requests.append(request)
        if self.status_code != 200:
            return httpx.Response(self.status_code, json={"detail": "upstream"})

        return httpx.Response(200, json={"result": self.result})

    @property
    def paths(self) -> list[str]:
        return [req.url.path for req in self.requests]

    def params_of(self, index: int = 0) -> dict[str, str]:
        return dict(self.requests[index].url.params)


class ApiV1ContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.provider = FakeProvider()
        self._real_client = Libs._client
        Libs._client = httpx.AsyncClient(transport=httpx.MockTransport(self.provider.handler))

        # Cache ve inflight tablosu süreç-içi global: testler birbirini kirletmesin.
        Libs._cache.clear()
        Libs._inflight.clear()

        self.client = TestClient(kekik_FastAPI)

    def tearDown(self) -> None:
        Libs._client = self._real_client
        Libs._cache.clear()
        Libs._inflight.clear()

    # ! ------------------------------------» Parametre sözleşmesi

    def test_load_item_forwards_encoded_url_parameter(self) -> None:
        """Regresyon: istemciler `url` gönderip engine `encoded_url` beklediğinde
        dizi/bölüm çözme akışı kırılıyordu."""
        res = self.client.get("/api/v1/load_item", params={"plugin": "DiziBox", "encoded_url": "https%3A%2F%2Fx%2Fy"})

        self.assertEqual(200, res.status_code)
        self.assertEqual(["/api/v1/load_item"], self.provider.paths)
        self.assertEqual(
            {"plugin": "DiziBox", "encoded_url": "https%3A%2F%2Fx%2Fy"},
            self.provider.params_of(),
        )

    def test_get_main_page_forwards_catalog_parameters(self) -> None:
        params = {
            "plugin": "DiziYou",
            "page": "2",
            "encoded_url": "https%3A%2F%2Fx%2Fkategori",
            "encoded_category": "Aksiyon",
        }
        self.client.get("/api/v1/get_main_page", params=params)

        self.assertEqual(["/api/v1/get_main_page"], self.provider.paths)
        self.assertEqual(params, self.provider.params_of())

    def test_load_links_forwards_episode_url(self) -> None:
        self.client.get("/api/v1/load_links", params={"plugin": "Dizilla", "encoded_url": "bolum-1"})

        self.assertEqual(["/api/v1/load_links"], self.provider.paths)
        self.assertEqual({"plugin": "Dizilla", "encoded_url": "bolum-1"}, self.provider.params_of())

    def test_quick_channels_is_exposed_to_clients(self) -> None:
        """Regresyon: uç yalnız web'in server-side çağrısında vardı; TV istemcisi
        canlı kanal listesine hiç erişemiyordu."""
        self.provider.result = [{"title": "TRT 1", "url": "https://x/y.m3u8"}]
        res = self.client.get("/api/v1/quick_channels")

        self.assertEqual(200, res.status_code)
        self.assertEqual(["/api/v1/quick_channels"], self.provider.paths)
        self.assertEqual([{"title": "TRT 1", "url": "https://x/y.m3u8"}], res.json()["result"])

    def test_response_envelope_keeps_schema_pointer(self) -> None:
        self.provider.result = [{"title": "Dark"}]
        res = self.client.get("/api/v1/search", params={"plugin": "DiziYou", "query": "dark"})

        body = res.json()
        self.assertEqual([{"title": "Dark"}], body["result"])
        self.assertEqual("/api/v1/schema", body["schema"])

    def test_client_identity_headers_are_forwarded(self) -> None:
        """Kaynak siteler hotlink/UA kontrolü yapıyor; istemci kimliği upstream'e taşınmalı."""
        self.client.get(
            "/api/v1/get_plugin_names",
            headers={"X-Original-User-Agent": "NetMoviesTV/1.0", "X-Original-Referer": "https://ornek/"},
        )

        sent = self.provider.requests[0].headers
        self.assertEqual("NetMoviesTV/1.0", sent["User-Agent"])
        self.assertEqual("https://ornek/", sent["Referer"])

    # ! ------------------------------------» Cache sözleşmesi

    def test_search_result_is_cached(self) -> None:
        for _ in range(2):
            self.client.get("/api/v1/search", params={"plugin": "DiziYou", "query": "dark"})

        self.assertEqual(1, len(self.provider.requests))

    def test_link_resolution_is_never_cached(self) -> None:
        """load_links kısa ömürlü token üretir; cache'lenirse ölü link servis edilir."""
        for _ in range(2):
            self.client.get("/api/v1/load_links", params={"plugin": "Dizilla", "encoded_url": "bolum-1"})

        self.assertEqual(2, len(self.provider.requests))

    def test_empty_aggregate_is_not_cached(self) -> None:
        """Geçici kaynak hatasında boş agregasyon cache'lenirse ana sayfa 10 dk boş kalıyordu."""
        self.provider.result = {"items": []}
        self.client.get("/api/v1/aggregate_new", params={"type": "movie"})

        self.provider.result = {"items": [{"title": "Dark"}]}
        res = self.client.get("/api/v1/aggregate_new", params={"type": "movie"})

        self.assertEqual(2, len(self.provider.requests))
        self.assertEqual([{"title": "Dark"}], res.json()["result"]["items"])

    def test_filled_aggregate_is_cached(self) -> None:
        self.provider.result = {"items": [{"title": "Dark"}]}
        for _ in range(2):
            self.client.get("/api/v1/aggregate_new", params={"type": "movie"})

        self.assertEqual(1, len(self.provider.requests))

    def test_cache_is_isolated_per_parameter_set(self) -> None:
        self.client.get("/api/v1/search", params={"plugin": "DiziYou", "query": "dark"})
        self.client.get("/api/v1/search", params={"plugin": "DiziYou", "query": "loki"})

        self.assertEqual(2, len(self.provider.requests))

    # ! ------------------------------------» Hata sözleşmesi

    def test_provider_http_error_becomes_structured_envelope(self) -> None:
        """İstemci modali `provider_error` bekliyor; 410 gövdesiz düşerse ekran sessiz kalıyor."""
        self.provider.status_code = 410
        res = self.client.get("/api/v1/load_item", params={"plugin": "DiziBox", "encoded_url": "x"})

        body = res.json()
        self.assertEqual(200, res.status_code)
        self.assertFalse(body["success"])
        self.assertEqual("PROVIDER_HTTP_ERROR", body["provider_error"]["code"])
        self.assertEqual(410, body["provider_error"]["status_code"])
        self.assertFalse(body["provider_error"]["retryable"])
        self.assertIsNone(body["result"])

    def test_provider_server_error_is_marked_retryable(self) -> None:
        self.provider.status_code = 502
        res = self.client.get("/api/v1/search", params={"plugin": "DiziYou", "query": "dark"})

        self.assertTrue(res.json()["provider_error"]["retryable"])

    def test_failed_request_is_not_cached(self) -> None:
        self.provider.status_code = 502
        self.client.get("/api/v1/search", params={"plugin": "DiziYou", "query": "dark"})

        self.provider.status_code = 200
        self.provider.result = [{"title": "Dark"}]
        res = self.client.get("/api/v1/search", params={"plugin": "DiziYou", "query": "dark"})

        self.assertEqual(2, len(self.provider.requests))
        self.assertEqual([{"title": "Dark"}], res.json()["result"])

    def test_health_endpoint_stays_reachable_without_provider(self) -> None:
        """Docker healthcheck bu ucu kullanıyor; upstream ölüyken de 200 dönmeli."""
        res = self.client.get("/api/v1/health")

        self.assertEqual(200, res.status_code)
        self.assertEqual([], self.provider.paths)


if __name__ == "__main__":
    unittest.main()


class ResolveSourcesContractTest(unittest.TestCase):
    """Oynatma zincirinin TEK ucu — TV, telefon ve web bunu tüketir."""

    def setUp(self) -> None:
        self.provider = FakeProvider()
        self._real_client = Libs._client
        Libs._client = httpx.AsyncClient(transport=httpx.MockTransport(self.provider.handler))
        Libs._cache.clear()
        Libs._inflight.clear()
        self.client = TestClient(kekik_FastAPI)

    def tearDown(self) -> None:
        Libs._client = self._real_client
        Libs._cache.clear()
        Libs._inflight.clear()

    def test_sources_are_ordered_and_labelled_by_language(self) -> None:
        self.provider.result = {
            "mode": "full",
            "sources": [
                {"name": "A · Oynatıcı", "url": "a"},
                {"name": "B · Türkçe Altyazılı", "url": "b"},
                {"name": "C · Türkçe Dublaj", "url": "c"},
            ],
            "episodes": [],
            "diagnostics": [],
        }
        res = self.client.get("/api/v1/resolve_sources", params={"plugin": "DiziBox", "encoded_url": "x", "title": "Dark"})

        sources = res.json()["result"]["sources"]
        self.assertEqual(["c", "b", "a"], [s["url"] for s in sources])
        self.assertEqual(
            ["Türkçe dublaj", "Türkçe altyazı", "dil bilinmiyor"],
            [s["language"]["label"] for s in sources],
        )

    def test_request_parameters_reach_the_engine(self) -> None:
        self.provider.result = {"sources": [], "episodes": [], "diagnostics": []}
        self.client.get(
            "/api/v1/resolve_sources",
            params={"plugin": "DiziBox", "encoded_url": "x", "title": "Dark", "mode": "fast", "episode": "3"},
        )

        self.assertEqual(["/api/v1/resolve_sources"], self.provider.paths)
        self.assertEqual(
            {"plugin": "DiziBox", "encoded_url": "x", "title": "Dark", "mode": "fast", "episode": "3"},
            self.provider.params_of(),
        )

    def test_diagnostics_are_passed_through_to_clients(self) -> None:
        """Kaynak raporu her istemcide aynı: teşhis kaydı sunucudan geliyor."""
        self.provider.result = {
            "sources": [],
            "episodes": [],
            "diagnostics": [{"level": "warn", "stage": "arama", "message": "DiziYou · sonuç yok"}],
        }
        res = self.client.get("/api/v1/resolve_sources", params={"plugin": "DiziBox", "encoded_url": "x"})

        self.assertEqual(
            [{"level": "warn", "stage": "arama", "message": "DiziYou · sonuç yok"}],
            res.json()["result"]["diagnostics"],
        )

    def test_empty_source_list_is_not_an_error(self) -> None:
        self.provider.result = {"sources": [], "episodes": [], "diagnostics": []}
        res = self.client.get("/api/v1/resolve_sources", params={"plugin": "DiziBox", "encoded_url": "x"})

        self.assertEqual(200, res.status_code)
        self.assertEqual([], res.json()["result"]["sources"])


class PlayerProofTest(unittest.TestCase):
    """Tek kullanımlık oynatıcı imzası (X-Sp) — her istekte yeniden üretilmeli."""

    def test_material_becomes_a_fresh_signature(self) -> None:
        from Public.Proxy.Libs.player_proof import apply_player_proof

        headers = apply_player_proof({"X-Sp-Secret": "abc", "X-Sp-Time": "1788430142", "Origin": "https://x"})

        self.assertIn("X-Sp", headers)
        self.assertTrue(headers["X-Sp"].startswith("1788430142."))
        self.assertEqual("https://x", headers["Origin"])

    def test_material_is_never_leaked_upstream(self) -> None:
        from Public.Proxy.Libs.player_proof import apply_player_proof

        headers = apply_player_proof({"X-Sp-Secret": "abc", "X-Sp-Time": "1788430142"})

        self.assertNotIn("X-Sp-Secret", headers)
        self.assertNotIn("X-Sp-Time", headers)

    def test_each_call_produces_a_different_signature(self) -> None:
        """Aynı imza ikinci kez gönderilirse kaynak 404 veriyor (ölçüldü)."""
        from Public.Proxy.Libs.player_proof import apply_player_proof

        first = apply_player_proof({"X-Sp-Secret": "abc", "X-Sp-Time": "100"})["X-Sp"]
        second = apply_player_proof({"X-Sp-Secret": "abc", "X-Sp-Time": "100"})["X-Sp"]

        self.assertNotEqual(first, second)

    def test_sources_without_material_are_untouched(self) -> None:
        from Public.Proxy.Libs.player_proof import apply_player_proof

        headers = apply_player_proof({"Referer": "https://x/"})

        self.assertEqual({"Referer": "https://x/"}, headers)

    def test_signed_sources_are_routed_through_the_proxy(self) -> None:
        from Public.API.v1.Libs.source_proxy import route_through_proxy

        sources = [
            {"name": "A", "url": "https://cdn/a.m3u8", "extra_headers": {"X-Sp-Secret": "s", "X-Sp-Time": "1"}},
            {"name": "B", "url": "https://cdn/b.m3u8"},
        ]
        routed = route_through_proxy(sources, "http://ev:3310")

        self.assertTrue(routed[0]["url"].startswith("http://ev:3310/proxy/video?url="))
        self.assertTrue(routed[0]["proxied"])
        self.assertNotIn("extra_headers", routed[0], "imza malzemesi istemciye sızmamalı")
        self.assertEqual("https://cdn/b.m3u8", routed[1]["url"], "başlık istemeyen kaynak proxy'ye alınmaz")
