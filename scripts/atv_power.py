"""Android TV kutusuna (Mi Box) uzaktan tuş gönderen küçük köprü.

Neden ayrı bir süreç: stream konteyneri ev ağındaki cihazlara TCP açamıyor
(`192.168.1.x` her portta timeout — Docker Desktop'ın ağı LAN'a köprülenmiyor).
Kutunun kumanda protokolü (Android TV Remote v2, 6466/6467 — Google TV / Xiaomi
Home uygulamasının kullandığı protokol) ancak aynı ağdaki bir süreçten
konuşulabilir, o yüzden köprü HOST'ta çalışır. Konteyner HOST'a ulaşabiliyor:
compose'daki `extra_hosts: host.docker.internal:host-gateway` ile stream buraya
uğrar (isim olmadan Docker'ın DNS'i o adı çözemiyor).

Kullanım:
    python scripts/atv_power.py --eslestir      # ilk kurulum: TV'de kod çıkar
    python scripts/atv_power.py                 # köprüyü dinlemeye al (3311)

Uçlar:  GET /saglik · GET /guc (kapatır) · GET /tus/<AD>  (ör. HOME, BACK, DPAD_UP)
"""

from __future__ import annotations

import argparse
import asyncio
import json
import os
import socket
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer

from androidtvremote2 import AndroidTVRemote

KUTU_IP  = os.getenv("ATV_HOST", "192.168.1.189")
PORT    = int(os.getenv("ATV_PORT", "3311"))
DIZIN   = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "data", "atv")


def ayakta() -> bool:
    """Kumanda portu dinliyor mu. Uykudaki kutu ping'e bile cevap vermez."""
    try:
        socket.create_connection((KUTU_IP, 6466), 2).close()
        return True
    except OSError:
        return False


def uzak() -> AndroidTVRemote:
    os.makedirs(DIZIN, exist_ok=True)
    return AndroidTVRemote(
        client_name = "NetMovies",
        certfile    = os.path.join(DIZIN, "cert.pem"),
        keyfile     = os.path.join(DIZIN, "key.pem"),
        host        = KUTU_IP,
    )


async def kodu_bekle() -> str:
    """Kodu terminalden al; terminal yoksa `data/atv/kod.txt`e yazılmasını bekle
    (eşleştirme oturumu açık kalmalı, bu yüzden süreç kodu burada bekler)."""
    dosya = os.path.join(DIZIN, "kod.txt")
    if os.isatty(0):
        return input("TV ekranındaki 6 haneli kodu yaz: ").strip()
    print(f"TV'de kod çıktı — kodu şuraya yaz: {dosya}", flush=True)
    for _ in range(300):
        if os.path.exists(dosya):
            kod = open(dosya, encoding="utf-8").read().strip()
            if kod:
                os.remove(dosya)
                return kod
        await asyncio.sleep(1)
    raise TimeoutError("kod gelmedi")


async def eslestir() -> None:
    tv = uzak()
    await tv.async_generate_cert_if_missing()
    await tv.async_start_pairing()
    await tv.async_finish_pairing(await kodu_bekle())
    await tv.async_connect()
    print(f"eşleşme tamam · cihaz: {tv.device_info} · açık: {tv.is_on}")
    tv.disconnect()


class Kopru:
    """Bağlantıyı açık tutar; kopunca kütüphane kendi yeniden bağlanır."""

    def __init__(self) -> None:
        self.dongu = asyncio.new_event_loop()
        threading.Thread(target=self.dongu.run_forever, daemon=True).start()
        self.tv: AndroidTVRemote | None = None

    def _tv(self) -> AndroidTVRemote:
        """Bağlantı açılışta değil İLK TUŞTA kurulur: uykudaki kutuda 6466 kapalı
        olduğu için açılışta bağlanmak köprünün kendisini başlatılamaz yapıyordu.
        AndroidTVRemote kurucusu ÇALIŞAN bir event loop istiyor: nesne de
        bağlantı da döngünün içinde doğmalı."""
        if self.tv is None:
            self.tv = self._calistir(self._bagla())
        return self.tv

    def _calistir(self, isk):
        return asyncio.run_coroutine_threadsafe(isk, self.dongu).result(timeout=20)

    async def _bagla(self) -> AndroidTVRemote:
        tv = uzak()
        await tv.async_connect()
        tv.keep_reconnecting()
        return tv

    def durum(self) -> dict:
        if self.tv is None and not ayakta():
            return {"host": KUTU_IP, "acik": False, "cihaz": None, "uyanik": False}
        tv = self._tv()
        return {"host": KUTU_IP, "acik": tv.is_on, "cihaz": tv.device_info, "uyanik": True}

    def tus(self, ad: str) -> None:
        self._tv().send_key_command(ad)

    def guc(self) -> str:
        """POWER yalnız KAPATIR. Uykudaki kutu ağda tamamen yok — 6466/8008/8009
        kapanıyor, ping bile geçmiyor, Wi-Fi'de WoWLAN olmadığı için magic packet
        de uyandırmıyor (denendi, tutmadı). Ağdan açmanın yolu yok; kutunun uyku
        ayarı "asla"ya alınmadıkça açma işi kumandada kalır."""
        if not ayakta():
            raise ConnectionError("kutu uykuda — agdan uyandirilamiyor")
        self.tus("POWER")
        return "POWER"


def sunucu(kopru: Kopru) -> HTTPServer:
    class Islem(BaseHTTPRequestHandler):
        def _yanit(self, kod: int, govde: dict) -> None:
            ham = json.dumps(govde, ensure_ascii=False).encode()
            self.send_response(kod)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(ham)))
            self.end_headers()
            self.wfile.write(ham)

        def do_GET(self) -> None:  # noqa: N802
            yol = self.path.rstrip("/")
            try:
                if yol in ("/saglik", ""):
                    self._yanit(200, {"ok": True, **kopru.durum()})
                elif yol == "/guc":
                    self._yanit(200, {"ok": True, "gonderildi": kopru.guc()})
                elif yol.startswith("/tus/"):
                    ad = yol.removeprefix("/tus/").upper()
                    kopru.tus(ad)
                    self._yanit(200, {"ok": True, "gonderildi": ad})
                else:
                    self._yanit(404, {"ok": False, "hata": "bilinmeyen uç"})
            except Exception as hata:            # kutu kapalı / ağ yok
                self._yanit(502, {"ok": False, "hata": f"{type(hata).__name__}: {hata}"})

        def log_message(self, *_):               # konsolu kirletme
            return

    return HTTPServer(("0.0.0.0", PORT), Islem)


def main() -> None:
    ayristirici = argparse.ArgumentParser()
    ayristirici.add_argument("--eslestir", action="store_true", help="ilk kurulum")
    if ayristirici.parse_args().eslestir:
        asyncio.run(eslestir())
        return
    kopru = Kopru()
    print(f"köprü hazır · kutu {KUTU_IP} · dinleniyor :{PORT} · {kopru.durum()}")
    sunucu(kopru).serve_forever()


if __name__ == "__main__":
    main()
