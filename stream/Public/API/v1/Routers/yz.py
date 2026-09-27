# NetMovies — cihaz üstü YZ kaydı (Gemini Nano durumu).
#
# Telefon uygulaması açılışta ML Kit / AICore'a "Gemini Nano var mı" diye sorar ve
# sonucu buraya yazar. Amaç teşhis: Dean'in telefonu Nano'yu destekliyor mu,
# indirdi mi — sunucudan (tarayıcıdan) görülsün. Cihaz başına tek satır, son hâl.

from __future__ import annotations

import json
import os
import time
from pathlib import Path

from Core import Request
from .    import api_v1_router, api_v1_global_message

_DEFAULT_PATH = "/data/yz_cihazlar.json" if Path("/data").is_dir() else "yz_cihazlar.json"
_PATH         = Path(os.getenv("YZ_CIHAZLAR_PATH", _DEFAULT_PATH))

NANO_DURUMLARI = ("UNAVAILABLE", "DOWNLOADABLE", "DOWNLOADING", "AVAILABLE", "HATA")
# Evde birkaç cihaz var; kaçak bir istemci dosyayı şişirmesin.
_MAX_CIHAZ = 20
_MAX_LEN   = 80


def _oku() -> list[dict]:
    try:
        if _PATH.exists():
            veri = json.loads(_PATH.read_text(encoding="utf-8"))
            return veri if isinstance(veri, list) else []
    except (OSError, ValueError):
        pass
    return []


def _yaz(liste: list[dict]) -> None:
    _PATH.parent.mkdir(parents=True, exist_ok=True)
    gecici = _PATH.with_suffix(".tmp")
    gecici.write_text(json.dumps(liste, ensure_ascii=False, indent=2), encoding="utf-8")
    gecici.replace(_PATH)


def kayit_olustur(veri: dict, simdi: float) -> dict | str:
    """Gelen gövdeyi doğrular; hata metni ya da saklanacak kayıt döner."""
    cihaz = str(veri.get("cihaz") or "").strip()[:_MAX_LEN]
    durum = str(veri.get("nano_durum") or "").strip().upper()
    if not cihaz:
        return "cihaz gerekli"
    if durum not in NANO_DURUMLARI:
        return f"nano_durum su degerlerden biri olmali: {', '.join(NANO_DURUMLARI)}"
    return {
        "cihaz"     : cihaz,
        "android"   : str(veri.get("android") or "").strip()[:_MAX_LEN],
        "nano_durum": durum,
        "zaman"     : str(veri.get("zaman") or "").strip()[:_MAX_LEN],
        # İstemci saati yanlış olabilir; sunucunun gördüğü an ayrıca tutulur.
        "alindi"    : time.strftime("%Y-%m-%dT%H:%M:%S", time.localtime(simdi)),
    }


def listeye_ekle(liste: list[dict], kayit: dict) -> list[dict]:
    """Aynı cihazın eski satırı düşer, yenisi başa gelir."""
    kalan = [k for k in liste if isinstance(k, dict) and k.get("cihaz") != kayit["cihaz"]]
    return [kayit, *kalan][:_MAX_CIHAZ]


@api_v1_router.get("/yz/cihaz")
async def yz_cihaz_get(request: Request):
    return {**api_v1_global_message, "result": _oku()}


@api_v1_router.post("/yz/cihaz")
async def yz_cihaz_post(request: Request):
    veri = request.state.veri or {}
    if not isinstance(veri, dict):
        return {**api_v1_global_message, "result": {"ok": False, "error": "sozluk bekleniyor"}}

    kayit = kayit_olustur(veri, time.time())
    if isinstance(kayit, str):
        return {**api_v1_global_message, "result": {"ok": False, "error": kayit}}

    try:
        _yaz(listeye_ekle(_oku(), kayit))
    except OSError as hata:
        return {**api_v1_global_message, "result": {"ok": False, "error": type(hata).__name__}}
    return {**api_v1_global_message, "result": {"ok": True}}
