# NetMovies — /api/v1/markers : bölümün açılış ve jenerik işaretleri.
#
# İstemci oynatmaya başlayıp süreyi öğrenince burayı çağırır; dönen işaretlerle
# "Açılışı Atla" düğmesini ve jenerikte geri sayımlı sonraki-bölüm kartını
# gösterir. İşaret bulunamazsa alanlar None döner — istemci hiçbir şey göstermez,
# eski davranış sürer.
#
# Ağır iş SUNUCUDA: altyazı dosyası burada çekilir ve ayrıştırılır, TV'ye yalnız
# üç sayı gider. Ayrıştırma saf ve test edilebilir (Libs/markers.py).

from __future__ import annotations

import json
import os
import time
from pathlib import Path

from Core import Request
from .    import api_v1_router, api_v1_global_message

from ..Libs.markers import isaretleri_cikar
from Public.Proxy.Libs.helpers import shared_client, url_is_public, prepare_request_headers

# Aynı bölüm tekrar açıldığında altyazıyı yeniden indirmemek için küçük bir kayıt.
# prefs.py ile aynı desen: /data varsa oraya, yoksa proje köküne.
_DEFAULT_PATH = "/data/markers.json" if Path("/data").is_dir() else "markers.json"
_PATH         = Path(os.getenv("MARKERS_PATH", _DEFAULT_PATH))
_TTL_SN       = 90 * 24 * 3600      # işaret bayatlamaz; eski kayıtlar yine de temizlensin
_MAX_KAYIT    = 2000
# Altyazı metin dosyasıdır; bundan büyüğü altyazı değildir, indirmeyi kes.
_MAX_BYTE     = 4 * 1024 * 1024


def _oku() -> dict:
    try:
        if _PATH.exists():
            veri = json.loads(_PATH.read_text(encoding="utf-8"))
            return veri if isinstance(veri, dict) else {}
    except Exception:
        pass
    return {}


def _yaz(veri: dict) -> None:
    try:
        _PATH.parent.mkdir(parents=True, exist_ok=True)
        gecici = _PATH.with_suffix(".tmp")
        gecici.write_text(json.dumps(veri, ensure_ascii=False), encoding="utf-8")
        gecici.replace(_PATH)
    except Exception:
        pass    # önbellek yazılamazsa işaret yine döner; kayıp değil, yavaşlık


@api_v1_router.get("/markers")
async def markers(request: Request, url: str = "", duration: float = 0.0):
    """Altyazı URL'i + toplam süre → açılış/jenerik işaretleri.

    `url`      : bölümün altyazı dosyası (VTT/SRT). Boşsa işaret üretilemez.
    `duration` : toplam süre (saniye). Jenerik boşluğu buna göre ölçülür.
    """
    bos = {"intro_start": None, "intro_end": None, "credits_start": None, "source": None}

    if not url or duration <= 0:
        return {**api_v1_global_message, "result": {**bos, "source": "yok"}}

    # SSRF kapısı: proxy uçlarıyla aynı kontrol — iç ağ adresleri çekilmez.
    if not await url_is_public(url):
        return {**api_v1_global_message, "result": {**bos, "source": "engellendi"}}

    anahtar = f"{url}|{int(duration)}"
    kayit   = _oku()
    onceki  = kayit.get(anahtar)
    if isinstance(onceki, dict) and (time.time() - onceki.get("at", 0)) < _TTL_SN:
        return {**api_v1_global_message, "result": onceki.get("result", bos)}

    try:
        basliklar = prepare_request_headers(request, url, None, None)
        yanit     = await shared_client.get(url, headers=basliklar)
        if yanit.status_code >= 400 or len(yanit.content) > _MAX_BYTE:
            return {**api_v1_global_message, "result": {**bos, "source": "altyazi-yok"}}
        metin = yanit.content.decode("utf-8", errors="replace")
    except Exception:
        return {**api_v1_global_message, "result": {**bos, "source": "altyazi-yok"}}

    sonuc = isaretleri_cikar(metin, duration)

    # En eski kayıtları at: bu dosya sınırsız büyümemeli.
    if len(kayit) >= _MAX_KAYIT:
        for eski in sorted(kayit, key=lambda k: kayit[k].get("at", 0) if isinstance(kayit[k], dict) else 0)[:_MAX_KAYIT // 4]:
            kayit.pop(eski, None)
    kayit[anahtar] = {"at": int(time.time()), "result": sonuc}
    _yaz(kayit)

    return {**api_v1_global_message, "result": sonuc}
