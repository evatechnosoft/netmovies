# NetMovies — kaynak (sağlayıcı) puanlaması.
#
# NEDEN: alternatif tarama sırası elle yazılı bir listeydi
# (engine `ALTERNATIVE_ORDER`). Zincir ilk çalışan kaynakta durduğu için sıra
# doğrudan bekleme süresidir; listedeki sağlayıcı o hafta bozulduysa her
# çözümleme onun bütçesini (25 sn) harcayıp geçiyordu. Artık gerçekten
# oynayan kaynak öne geçer.
#
# Puan: başarı +50, başarısızlık -50 (AutoStream deseni). Hiçbir sağlayıcı
# yasaklanmaz — yalnız sıraya girer, en dipteki bile denenmeye devam eder.
# Yarı ömür 7 gün: geçen ayın hüküm giymiş sağlayıcısı bugün temiz sayfayla
# başlasın, bir haftalık kanıt bu haftayı bağlasın.
#
# Depo: prefs.py ile aynı desen (JSON + atomik taşıma). Sağlayıcı sayısı
# onlarla ölçülüyor, tablo açmaya değmez.

from __future__ import annotations

import json
import math
import os
import time
from pathlib import Path

_DEFAULT_PATH = "/data/source_score.json" if Path("/data").is_dir() else "source_score.json"


def _yol() -> Path:
    """Depo yolu her çağrıda okunur: modül yüklenirken sabitlenirse testler
    birbirinin verisini görür ve ortam değişkeni geç ayarlanamaz."""
    return Path(os.getenv("SOURCE_SCORE_PATH", _DEFAULT_PATH))

BASARI_PUANI   = 50.0
HATA_PUANI     = -50.0
# Puan tavanı: bir sağlayıcı 20 başarıyla dokunulmaz olmasın, bir kötü hafta
# sıralamayı hâlâ değiştirebilsin.
TAVAN          = 500.0
YARI_OMUR_SN   = 7 * 24 * 3600
# Yıldızlı sağlayıcı (Gözat'taki kullanıcı tercihi) en ağır oy: Dean bir kaynağı
# işaretlediyse istatistik onu geçmemeli.
FAVORI_PUANI   = 100.0


def _oku() -> dict:
    yol = _yol()
    try:
        if yol.exists():
            veri = json.loads(yol.read_text(encoding="utf-8"))
            return veri if isinstance(veri, dict) else {}
    except Exception:
        pass
    return {}


def _yaz(veri: dict) -> None:
    yol = _yol()
    try:
        yol.parent.mkdir(parents=True, exist_ok=True)
        gecici = yol.with_suffix(".tmp")
        gecici.write_text(json.dumps(veri, ensure_ascii=False, indent=2), encoding="utf-8")
        gecici.replace(yol)
    except OSError:
        # Puan kaybı oynatmayı durdurmaz: disk yoksa sıralama sabit listeye düşer.
        pass


def _soluk(puan: float, yazim_zamani: float, simdi: float) -> float:
    """Yarı ömür uygulanmış puan."""
    gecen = max(0.0, simdi - yazim_zamani)
    return puan * math.pow(0.5, gecen / YARI_OMUR_SN)


def kaydet(plugin: str, basarili: bool, simdi: float | None = None) -> float:
    """Bir oynatma denemesinin sonucunu işler; yeni puanı döndürür."""
    if not plugin or not isinstance(plugin, str):
        return 0.0
    simdi = time.time() if simdi is None else simdi

    veri  = _oku()
    kayit = veri.get(plugin) or {}
    onceki = _soluk(float(kayit.get("puan", 0.0)), float(kayit.get("at", simdi)), simdi)

    puan = onceki + (BASARI_PUANI if basarili else HATA_PUANI)
    puan = max(-TAVAN, min(TAVAN, puan))

    veri[plugin] = {
        "puan"   : round(puan, 2),
        "at"     : simdi,
        "basari" : int(kayit.get("basari", 0)) + (1 if basarili else 0),
        "hata"   : int(kayit.get("hata", 0)) + (0 if basarili else 1),
    }
    _yaz(veri)
    return puan


def puanlar(simdi: float | None = None) -> dict[str, float]:
    """Yarı ömür uygulanmış güncel puanlar."""
    simdi = time.time() if simdi is None else simdi
    return {
        ad: _soluk(float(k.get("puan", 0.0)), float(k.get("at", simdi)), simdi)
        for ad, k in _oku().items()
        if isinstance(k, dict)
    }


def sirala(adaylar: list[str], favoriler: list[str] | None = None, simdi: float | None = None) -> list[str]:
    """Adayları puana göre sıralar. Eşit puanda gelen sıra korunur (soğuk başlangıç).

    Sıra KORUNARAK sıralamak önemli: hiç veri yokken sonuç, engine'in elle
    yazılmış listesinin aynısıdır — puanlama açmak ilk gün hiçbir şeyi bozmaz.
    """
    p   = puanlar(simdi)
    fav = set(favoriler or [])
    return sorted(adaylar, key=lambda ad: -(p.get(ad, 0.0) + (FAVORI_PUANI if ad in fav else 0.0)))


def ozet(simdi: float | None = None) -> list[dict]:
    """Yönetim/teşhis için okunur döküm."""
    simdi = time.time() if simdi is None else simdi
    veri  = _oku()
    cikti = []
    for ad, k in veri.items():
        if not isinstance(k, dict):
            continue
        cikti.append({
            "plugin" : ad,
            "puan"   : round(_soluk(float(k.get("puan", 0.0)), float(k.get("at", simdi)), simdi), 1),
            "basari" : int(k.get("basari", 0)),
            "hata"   : int(k.get("hata", 0)),
            "son_at" : int(float(k.get("at", 0))),
        })
    return sorted(cikti, key=lambda x: -x["puan"])
