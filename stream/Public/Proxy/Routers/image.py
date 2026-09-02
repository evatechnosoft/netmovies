# Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

import asyncio, ipaddress, os
from collections   import OrderedDict
from urllib.parse  import urlsplit
from fastapi       import Request, Response
from .             import proxy_router
from ..Libs.helpers import prepare_request_headers, shared_client, DEFAULT_REFERER

# Poster/afiş görselleri için proxy. Kaynak CDN'leri sık sık hotlink koruması
# (Referer kontrolü) uyguluyor → tarayıcının doğrudan <img> isteği düşüyor.
# Bu endpoint isteği stream (ev/residential IP) üzerinden, doğru Referer/UA ile
# yeniden yapıp uzun cache ile geri döndürür. Video/altyazı proxy'siyle aynı
# `check_proxy_disabled` guard'ına tabidir (harici PROXY_URL varsa 403).

_MAX_IMAGE_BYTES = 8 * 1024 * 1024  # 8MB — poster için fazlasıyla yeterli
_CACHE_CONTROL   = "public, max-age=604800, immutable"  # 7 gün

# Süreç-içi poster cache. Aynı poster birden çok rafta/ekranda tekrar ediyor ve
# TV istemcisi soğuk açılışta hepsini yeniden istiyordu → her seferinde kaynak
# CDN'e TLS + indirme. Bellekten servis edilince istek ~0 ms'e iniyor.
_IMG_CACHE_MAX_BYTES = int(os.getenv("IMAGE_CACHE_MB", "128")) * 1024 * 1024
_img_cache: "OrderedDict[str, tuple[str, bytes]]" = OrderedDict()
_img_cache_bytes = 0


def _cache_get(key: str) -> tuple[str, bytes] | None:
    entry = _img_cache.get(key)
    if entry is not None:
        _img_cache.move_to_end(key)   # LRU: en son kullanılan sona
    return entry


def _cache_put(key: str, content_type: str, content: bytes) -> None:
    global _img_cache_bytes
    if len(content) > _IMG_CACHE_MAX_BYTES // 4:
        return                        # tek görsel cache'i domine etmesin
    if key in _img_cache:
        _img_cache_bytes -= len(_img_cache[key][1])
    _img_cache[key] = (content_type, content)
    _img_cache.move_to_end(key)
    _img_cache_bytes += len(content)
    while _img_cache_bytes > _IMG_CACHE_MAX_BYTES and _img_cache:
        _, (_, dropped) = _img_cache.popitem(last=False)
        _img_cache_bytes -= len(dropped)


async def _host_is_public(host: str) -> bool:
    """SSRF koruması: host'un çözümlenen tüm IP'leri global (public) olmalı.
    localhost/özel/link-local/loopback adreslerine istek engellenir."""
    if not host:
        return False
    try:
        loop  = asyncio.get_running_loop()
        infos = await loop.getaddrinfo(host, None)
    except Exception:
        return False

    for info in infos:
        sockaddr = info[4]
        try:
            ip = ipaddress.ip_address(sockaddr[0])
        except ValueError:
            return False
        if ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_reserved or ip.is_multicast or ip.is_unspecified:
            return False
    return bool(infos)


@proxy_router.get("/image")
async def image_proxy(request: Request, url: str, referer: str = None, user_agent: str = None):
    """Poster/afiş görsel proxy'si — hotlink korumasını aşar + cache'ler."""
    cached = _cache_get(url)
    if cached:
        return Response(
            content     = cached[1],
            status_code = 200,
            media_type  = cached[0],
            headers     = {
                "Cache-Control"               : _CACHE_CONTROL,
                "Access-Control-Allow-Origin" : "*",
                "X-Cache"                     : "HIT",
            },
        )

    parts = urlsplit(url)
    if parts.scheme not in ("http", "https"):
        return Response(status_code=400, content="Geçersiz şema")
    if not await _host_is_public(parts.hostname or ""):
        return Response(status_code=403, content="Engellenen host")

    # Referer verilmezse kaynağın kendi origin'ini kullan (çoğu hotlink kontrolü
    # same-origin Referer bekler); yoksa DEFAULT_REFERER.
    if not referer or referer == "None":
        referer = f"{parts.scheme}://{parts.netloc}/"

    try:
        request_headers = prepare_request_headers(request, url, referer, user_agent)
        # Range poster için gereksiz — kaldır (kaynaklar 206'ya takılabilir).
        request_headers.pop("Range", None)

        response = await shared_client.get(url, headers=request_headers)
        if response.status_code >= 400:
            return Response(status_code=502, content=f"Görsel kaynağı hatası: {response.status_code}")

        content_type = response.headers.get("content-type", "").split(";")[0].strip().lower()
        if not content_type.startswith("image/"):
            return Response(status_code=415, content="Görsel değil")

        content = response.content
        if len(content) > _MAX_IMAGE_BYTES:
            return Response(status_code=413, content="Görsel çok büyük")

        _cache_put(url, content_type, content)

        return Response(
            content     = content,
            status_code = 200,
            media_type  = content_type,
            headers     = {
                "Cache-Control"               : _CACHE_CONTROL,
                "Access-Control-Allow-Origin" : "*",
                "X-Cache"                     : "MISS",
            },
        )
    except Exception as e:
        # 502 → tarayıcıda <img> onerror tetiklenir → mevcut placeholder gösterilir.
        return Response(status_code=502, content=f"Proxy hatası: {str(e)}")
