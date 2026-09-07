# NetMovies — Yerel OTA. APK zaten evdeki sunucuda dururken istemcinin onu
# GitHub'dan indirmesi gereksiz: internet kesikse güncelleme hiç gelmiyor,
# GitHub'ın saatlik 60 istek sınırı ev ağının tamamını kilitliyor ve 20 MB
# dışarıdan iniyor. Bu uç, `/data/apk/` altındaki en yeni APK'yı LAN'dan sunar.
#
# Dosya adı sürümü taşır: NetMovies-TV-v0.1.55.apk
# İstemci önce burayı sorar, boş dönerse GitHub Releases'e düşer.

import re

from pathlib import Path

from Core import Request, FileResponse, JSONResponse
from .    import api_v1_router, api_v1_global_message

APK_DIR = Path("/data/apk")
_NAME_RE = re.compile(r"v(\d+)\.(\d+)\.(\d+)", re.IGNORECASE)


def _version(path: Path) -> tuple[int, int, int] | None:
    match = _NAME_RE.search(path.name)
    if not match:
        return None
    return tuple(int(part) for part in match.groups())


def _latest() -> tuple[Path, tuple[int, int, int]] | None:
    """En yüksek sürüm numaralı APK. Ad sürüm taşımıyorsa yok sayılır —
    'app-debug.apk' gibi bir dosya sürümü belirsiz olduğu için sunulmaz."""
    if not APK_DIR.is_dir():
        return None
    adaylar = [(p, v) for p in APK_DIR.glob("*.apk") if (v := _version(p))]
    return max(adaylar, key=lambda item: item[1], default=None)


@api_v1_router.get("/app_update")
async def app_update(request: Request):
    """Yerel APK varsa sürümü ve indirme adresi; yoksa boş sonuç."""
    bulunan = _latest()
    if not bulunan:
        return {**api_v1_global_message, "result": None}

    path, version = bulunan
    taban = str(request.base_url).rstrip("/")
    return {
        **api_v1_global_message,
        "result": {
            "tag" : "v{}.{}.{}-poc".format(*version),
            "url" : f"{taban}/api/v1/app_update/download",
            "size": path.stat().st_size,
            "name": path.name,
        },
    }


@api_v1_router.get("/app_update/download")
async def app_update_download(request: Request):
    bulunan = _latest()
    if not bulunan:
        return JSONResponse(status_code=404, content={"hata": "Yerel APK yok"})

    path, _ = bulunan
    return FileResponse(
        path=str(path),
        media_type="application/vnd.android.package-archive",
        filename=path.name,
    )
