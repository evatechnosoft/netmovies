# NetMovies — Yerel OTA. APK zaten evdeki sunucuda dururken istemcinin onu
# GitHub'dan indirmesi gereksiz: internet kesikse güncelleme hiç gelmiyor,
# GitHub'ın saatlik 60 istek sınırı ev ağının tamamını kilitliyor ve 20 MB
# dışarıdan iniyor. Bu uç, `/data/apk/` altındaki en yeni APK'yı LAN'dan sunar.
#
# Dosya adı hem HEDEFİ hem sürümü taşır: `NetMovies-TV-v0.1.77.apk`,
# `NetMovies-Wear-v0.1.0.apk`. İstemci `?target=tv|wear` ile kendi APK'sını ister;
# hedef verilmezse TV varsayılır (eski istemciler parametre göndermiyor).
# İstemci önce burayı sorar, boş dönerse GitHub Releases'e düşer.

import re

from pathlib import Path

from Core import Request, FileResponse, JSONResponse
from .    import api_v1_router, api_v1_global_message

APK_DIR = Path("/data/apk")
_NAME_RE = re.compile(r"v(\d+)\.(\d+)\.(\d+)", re.IGNORECASE)

# Hedef → dosya adı öneki. Saat APK'sı TV'ye, TV APK'sı saate inmesin.
_ONEKLER = {"tv": "netmovies-tv-", "wear": "netmovies-wear-"}


def _version(path: Path) -> tuple[int, int, int] | None:
    match = _NAME_RE.search(path.name)
    if not match:
        return None
    return tuple(int(part) for part in match.groups())


def _hedef(istek: dict | None) -> str:
    deger = str((istek or {}).get("target") or "tv").strip().lower()
    return deger if deger in _ONEKLER else "tv"


def _latest(hedef: str = "tv") -> tuple[Path, tuple[int, int, int]] | None:
    """Hedefin en yüksek sürümlü APK'sı. Ad sürüm taşımıyorsa yok sayılır —
    'app-debug.apk' gibi bir dosya sürümü belirsiz olduğu için sunulmaz."""
    if not APK_DIR.is_dir():
        return None
    onek = _ONEKLER[hedef]
    adaylar = [
        (p, v) for p in APK_DIR.glob("*.apk")
        if p.name.lower().startswith(onek) and (v := _version(p))
    ]
    return max(adaylar, key=lambda item: item[1], default=None)


@api_v1_router.get("/app_update")
async def app_update(request: Request):
    """Yerel APK varsa sürümü ve indirme adresi; yoksa boş sonuç."""
    hedef   = _hedef(request.state.veri)
    bulunan = _latest(hedef)
    if not bulunan:
        return {**api_v1_global_message, "result": None}

    path, version = bulunan
    taban = str(request.base_url).rstrip("/")
    return {
        **api_v1_global_message,
        "result": {
            "tag" : "v{}.{}.{}-poc".format(*version),
            "url" : f"{taban}/api/v1/app_update/download?target={hedef}",
            "size": path.stat().st_size,
            "name": path.name,
        },
    }


@api_v1_router.get("/app_update/download")
async def app_update_download(request: Request):
    bulunan = _latest(_hedef(request.state.veri))
    if not bulunan:
        return JSONResponse(status_code=404, content={"hata": "Yerel APK yok"})

    path, _ = bulunan
    return FileResponse(
        path=str(path),
        media_type="application/vnd.android.package-archive",
        filename=path.name,
    )
