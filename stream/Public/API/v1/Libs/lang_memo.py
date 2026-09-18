# NetMovies — içeriğin dil rozetleri (poster üstünde göstermek için).
#
# NEDEN: bir içeriğin Türkçe dublajı var mı, yoksa yalnız orijinal dil mi — bu
# ancak oynatma zinciri koştuktan sonra bilinir (katalog yanıtı dil taşımaz).
# Zinciri poster çizerken koşturmak dakikalar sürerdi. Onun yerine zaten koşan
# her çözümlemenin yan ürünü hatırlanır: bir kez açılan içerik, bir daha
# açılmadan da posterinde rozetini gösterir.
#
# Depo: source_score.py ile aynı desen (JSON + atomik taşıma). Yüz-binlerce
# içerik beklenmiyor; kayıt sayısı tavana dayanınca en eskiler düşer.

from __future__ import annotations

import json
import os
import time
from pathlib import Path

from .language import RANK_BADGES

_DEFAULT_PATH = "/data/lang_memo.json" if Path("/data").is_dir() else "lang_memo.json"

# Sağlayıcı bir içeriğin dublajını sonradan ekleyebilir; kayıt sonsuza kadar
# doğru sayılmaz.
OMUR_SN    = 30 * 24 * 3600
MAX_KAYIT  = 5000


def _yol() -> Path:
    return Path(os.getenv("LANG_MEMO_PATH", _DEFAULT_PATH))


def _oku() -> dict:
    try:
        yol = _yol()
        if yol.exists():
            veri = json.loads(yol.read_text(encoding="utf-8"))
            return veri if isinstance(veri, dict) else {}
    except Exception:
        pass
    return {}


def _yaz(veri: dict) -> None:
    try:
        yol = _yol()
        yol.parent.mkdir(parents=True, exist_ok=True)
        gecici = yol.with_suffix(".tmp")
        gecici.write_text(json.dumps(veri, ensure_ascii=False), encoding="utf-8")
        gecici.replace(yol)
    except OSError:
        # Rozet kaybı oynatmayı durdurmaz: poster rozetsiz çizilir.
        pass


def anahtar(baslik: str) -> str:
    """Kayıt anahtarı BAŞLIKTIR, adres değil: aynı içerik her sağlayıcıda başka
    adreste durur ve kullanıcı için tek içeriktir. `watch_store` da başlıkla
    anahtarlıyor — iki taraf aynı içeriği aynı şeyde birleştirir."""
    return (baslik or "").strip().casefold()


def kaydet(baslik: str, ranks: list[int], simdi: float | None = None) -> list[str]:
    """Çözümlemeden çıkan dil rozetlerini hatırlar; yazılan rozetleri döndürür."""
    key = anahtar(baslik)
    rozetler = sorted({RANK_BADGES[r] for r in ranks if r in RANK_BADGES})
    if not key or not rozetler:
        return []

    simdi = time.time() if simdi is None else simdi
    veri  = _oku()
    veri[key] = {"r": rozetler, "at": simdi}

    if len(veri) > MAX_KAYIT:
        for eski in sorted(veri, key=lambda k: veri[k].get("at", 0))[: len(veri) - MAX_KAYIT]:
            veri.pop(eski, None)

    _yaz(veri)
    return rozetler


def rozetler(simdi: float | None = None) -> dict[str, list[str]]:
    """Süresi dolmamış tüm kayıtlar: başlık anahtarı → rozet listesi."""
    simdi = time.time() if simdi is None else simdi
    cikti: dict[str, list[str]] = {}
    for key, kayit in _oku().items():
        if not isinstance(kayit, dict):
            continue
        if simdi - float(kayit.get("at", 0)) > OMUR_SN:
            continue
        liste = [r for r in (kayit.get("r") or []) if isinstance(r, str)]
        if liste:
            cikti[key] = liste
    return cikti
