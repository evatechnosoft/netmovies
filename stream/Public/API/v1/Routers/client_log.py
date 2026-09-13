# NetMovies — televizyonun oynatma günlüğü, tarayıcıdan okunur.
#
# Günlük TV'de (PlaybackLog) zaten tutuluyordu ama YALNIZ televizyonda okunabiliyordu:
# kumandayla satır satır gezilemediği için D-pad listenin başına/sonuna atlıyor,
# aradaki satırlar hiç görünmüyordu. Ses kesintisi gibi şeyleri teşhis etmek için
# günlüğün telefondan/PC'den okunması gerekiyor.
#
# Bellekte, tek hane: son oturumun dökümü. Kalıcı değil — teşhis kaydı, arşiv değil.

from __future__ import annotations

import time

from fastapi.responses import PlainTextResponse

from Core import Request
from .    import api_v1_router, api_v1_global_message

# Tek televizyon, tek oturum. Kaçak bir istemci belleği doldurmasın.
_MAX_LINES = 400
_MAX_LEN   = 400

_lines: list[str] = []
_at: float        = 0.0
_device: str      = ""


@api_v1_router.post("/client_log")
async def client_log_post(request: Request):
    """Televizyon çağırır: oynatma günlüğünün son hâli (satır listesi)."""
    global _lines, _at, _device
    veri = request.state.veri or {}

    ham = veri.get("lines")
    if isinstance(ham, str):
        ham = ham.splitlines()
    if not isinstance(ham, list):
        return {**api_v1_global_message, "result": {"ok": False, "error": "lines listesi bekleniyor"}}

    _lines  = [str(satir)[:_MAX_LEN] for satir in ham[:_MAX_LINES]]
    _at     = time.time()
    _device = str(veri.get("device") or "TV")[:60]
    return {**api_v1_global_message, "result": {"ok": True, "count": len(_lines)}}


@api_v1_router.get("/client_log")
async def client_log_get(request: Request):
    """Tarayıcıdan okunur düz metin: en yeni kayıt en üstte (TV'deki sırayla)."""
    if not _lines:
        return PlainTextResponse("Kayıt yok — televizyonda bir şey oynat, günlük buraya düşer.\n")

    baslik = f"# {_device} · {time.strftime('%d.%m.%Y %H:%M:%S', time.localtime(_at))} · {len(_lines)} satır"
    return PlainTextResponse("\n".join([baslik, ""] + _lines) + "\n")
