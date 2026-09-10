# NetMovies — istemci tercihleri (kumanda düzeni, blok boyutları, görünüm).
#
# Bunlar tarayıcının localStorage'ındaydı ve her açılışta sıfırlanmış görünüyordu:
# localStorage KÖKEN BAŞINA ayrıdır — ev ağından `http://192.168.x.x:3310` ile
# açmak ile tünelden `https://w.evaitec.com` ile açmak iki ayrı depodur; PWA'yı
# yeniden kurmak da temizler. Düzen "Devam Et" kaydı gibi SUNUCUDA tutulur ki
# hangi adresten, hangi cihazdan girilirse girilsin aynı gelsin.
#
# Şema serbest (sözlük): kumanda kendi anahtarlarını yazar, sunucu yorumlamaz.
# Yalnız boyut sınırı vardır — bu uç kişisel ayar deposudur, veri kuyruğu değil.

from __future__ import annotations

import json
import os
from pathlib import Path

from Core import Request
from .    import api_v1_router, api_v1_global_message

_DEFAULT_PATH = "/data/prefs.json" if Path("/data").is_dir() else "prefs.json"
_PATH         = Path(os.getenv("PREFS_PATH", _DEFAULT_PATH))

# Tek kullanıcılık kişisel ayar; kaçak bir istemci diski doldurmasın.
_MAX_BYTES = 64 * 1024


def _oku() -> dict:
    try:
        if _PATH.exists():
            veri = json.loads(_PATH.read_text(encoding="utf-8"))
            return veri if isinstance(veri, dict) else {}
    except Exception:
        pass
    return {}


def _yaz(veri: dict) -> None:
    _PATH.parent.mkdir(parents=True, exist_ok=True)
    # Yarım yazılmış dosya bir daha okunamaz hale gelirdi: önce geçiciye, sonra taşı.
    gecici = _PATH.with_suffix(".tmp")
    gecici.write_text(json.dumps(veri, ensure_ascii=False, indent=2), encoding="utf-8")
    gecici.replace(_PATH)


@api_v1_router.get("/prefs")
async def prefs_get(request: Request):
    """Kayıtlı tercihler. Hiç yazılmadıysa boş sözlük — istemci varsayılanını kullanır."""
    return {**api_v1_global_message, "result": _oku()}


@api_v1_router.post("/prefs")
async def prefs_post(request: Request):
    """Gönderilen anahtarları MEVCUT kaydın üzerine yazar (silmez, birleştirir).

    Birleştirme bilinçli: kumanda yalnız değişen anahtarı yollar, başka bir
    istemcinin yazdığı ayar bu yüzden kaybolmaz.
    """
    veri = request.state.veri or {}
    if not isinstance(veri, dict):
        return {**api_v1_global_message, "result": {"ok": False, "error": "sozluk bekleniyor"}}

    gelen = {k: v for k, v in veri.items() if isinstance(k, str)}
    if len(json.dumps(gelen)) > _MAX_BYTES:
        return {**api_v1_global_message, "result": {"ok": False, "error": "cok buyuk"}}

    kayit = _oku()
    kayit.update(gelen)
    try:
        _yaz(kayit)
    except Exception as hata:
        return {**api_v1_global_message, "result": {"ok": False, "error": type(hata).__name__}}

    return {**api_v1_global_message, "result": {"ok": True, "count": len(kayit)}}
