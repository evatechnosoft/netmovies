"""PIN kapısı: çerez PIN'i taşımaz ve PIN değişince eski girişler düşer."""

from pathlib import Path
import sys
import unittest


STREAM_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(STREAM_ROOT))

import Core  # noqa: F401  — uygulamayı önce kurar
from Core.Modules import _pin


class SahteIstek:
    def __init__(self, cookies: dict) -> None:
        self.cookies = cookies


class SitePinTest(unittest.TestCase):
    def test_cookie_does_not_contain_the_pin(self) -> None:
        self.assertNotIn("1234", _pin.cerez_degeri("1234"))

    def test_changing_the_pin_invalidates_old_cookies(self) -> None:
        # PIN değiştirildiğinde daha önce girmiş cihazlar düşmeli.
        self.assertNotEqual(_pin.cerez_degeri("1234"), _pin.cerez_degeri("4321"))

    def test_open_when_no_pin_is_set(self) -> None:
        # Kapı yoksa mevcut davranış korunur (ev içi kullanım şifresiz).
        _pin.site_pin = lambda: ""
        self.assertTrue(_pin.girisli_mi(SahteIstek({})))

    def test_remote_endpoints_are_behind_the_gate(self) -> None:
        # Tünel açıkken yabancı biri televizyonu sürememeli.
        for yol in ("/api/v1/remote/command", "/api/v1/remote/play", "/api/v1/voice"):
            self.assertTrue(yol.startswith(_pin._KORUMALI_API), yol)

    def test_tv_client_endpoints_stay_open(self) -> None:
        # TV istemcisi çerez taşımıyor; katalog/yoklama uçları kapının dışında.
        for yol in ("/api/v1/aggregate_new", "/api/v1/remote/poll", "/api/v1/watch"):
            self.assertFalse(yol.startswith(_pin._KORUMALI_API), yol)


if __name__ == "__main__":
    unittest.main()


class SahteUrl:
    def __init__(self, hostname: str | None) -> None:
        self.hostname = hostname


class LanIstegiTest(unittest.TestCase):
    # Telefondaki uygulama çerez taşımaz; evdeyken remote/play 401 alıyordu.
    # Docker ardında istemci IP'si ayırt edilemez → Host'a bakılır.
    def _istek(self, host: str | None):
        istek = SahteIstek({})
        istek.url = SahteUrl(host)
        return istek

    def test_private_host_is_lan(self) -> None:
        for host in ("192.168.0.29", "192.168.1.185", "10.0.0.5", "localhost", "127.0.0.1"):
            self.assertTrue(_pin.lan_istegi(self._istek(host)), host)

    def test_tunnel_and_public_hosts_are_not_lan(self) -> None:
        for host in ("w.evaitec.com", "8.8.8.8", "", None):
            self.assertFalse(_pin.lan_istegi(self._istek(host)), host)
