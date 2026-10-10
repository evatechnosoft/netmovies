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


async def ytdlp_json(args: list[str], url: str, timeout: float = 60.0, dogrudan_once: bool = False) -> dict | None:
    """yt-dlp JSON çıktısı; bir yol başarısızsa öbürü (WARP ↔ ev bağlantısı).

    Yetişkin siteler ev ISP'sinde engelli → WARP önce. YouTube ev IP'sinden çalışır
    ve WARP çöktüğünde (10 Eki: DNS %91 zaman aşımı, proxy 503, istek 42 sn) her
    çözümleme boşa düşüyordu → `dogrudan_once`: önce ev, sonra WARP.
    """
    import asyncio
    import json

    for proxy in ((None, WARP_PROXY) if dogrudan_once else (WARP_PROXY, None)):
        proc = await asyncio.create_subprocess_exec(
            "yt-dlp", *args, *(["--proxy", proxy] if proxy else []), url,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        try:
            stdout, _ = await asyncio.wait_for(proc.communicate(), timeout=timeout)
        except asyncio.TimeoutError:
            proc.kill()
            continue
        if proc.returncode != 0 or not stdout:
            continue
        try:
            return json.loads(stdout)
        except ValueError:
            continue
    return None


async def ytdlp_info(url: str, timeout: float = 60.0, dogrudan_once: bool = False) -> dict | None:
    """yt-dlp ile video bilgisi + format listesi.

    httpx bazı sitelerde TLS parmak izinden 403 alıyor (PornHub video sayfası
    curl'de 200, httpx'te 403). Sayfayı HTML olarak kazımak yerine yt-dlp'ye
    bırakmak hem bu engeli aşıyor hem site şablonu değişince kırılmıyor.
    """
    # --ignore-no-formats-error: xHamster'ın formatları "Untested" işaretli gelir ve
    # varsayılan format seçimi başarısız olup TÜM çıktıyı iptal ediyor. Formatlar
    # zaten JSON'da; seçimi biz yapıyoruz.
    return await ytdlp_json(
        ["--no-warnings", "--no-playlist", "--ignore-no-formats-error", "-j"], url, timeout, dogrudan_once,
    )
