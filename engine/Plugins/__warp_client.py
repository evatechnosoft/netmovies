"""WARP üzerinden çeken ortak HTTP istemcisi.

Yerel ISP yetişkin kaynakları engelliyor; bu eklentiler WARP tüneli üzerinden
gitmek zorunda. Adres compose'tan `WARP_PROXY` ile gelir — IP sabitlemek
kırılgandı, WARP container'ı yeniden yaratılınca adres kayıyordu.
"""

from __future__ import annotations

import os

import httpx

WARP_PROXY = os.getenv("WARP_PROXY") or os.getenv("WARP_PROXY_URL") or "http://netmovies-warp:8080"

_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"


def warp_client(timeout: float = 15.0, **kwargs) -> httpx.AsyncClient:
    headers = {"User-Agent": _UA, **kwargs.pop("headers", {})}
    return httpx.AsyncClient(proxy=WARP_PROXY, headers=headers, timeout=timeout, follow_redirects=True, **kwargs)


async def ytdlp_info(url: str, timeout: float = 60.0) -> dict | None:
    """yt-dlp ile video bilgisi + format listesi (WARP üzerinden).

    httpx bazı sitelerde TLS parmak izinden 403 alıyor (PornHub video sayfası
    curl'de 200, httpx'te 403). Sayfayı HTML olarak kazımak yerine yt-dlp'ye
    bırakmak hem bu engeli aşıyor hem site şablonu değişince kırılmıyor.
    """
    import asyncio
    import json

    proc = await asyncio.create_subprocess_exec(
        # --ignore-no-formats-error: xHamster'ın formatları "Untested" işaretli gelir ve
        # varsayılan format seçimi başarısız olup TÜM çıktıyı iptal ediyor. Formatlar
        # zaten JSON'da; seçimi biz yapıyoruz.
        "yt-dlp", "--no-warnings", "--no-playlist", "--ignore-no-formats-error", "-j",
        "--proxy", WARP_PROXY, url,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )
    try:
        stdout, _ = await asyncio.wait_for(proc.communicate(), timeout=timeout)
    except asyncio.TimeoutError:
        proc.kill()
        return None

    if proc.returncode != 0 or not stdout:
        return None
    try:
        return json.loads(stdout)
    except ValueError:
        return None
