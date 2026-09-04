# Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

import os, time
from collections   import OrderedDict
from urllib.parse  import quote, urlsplit
from fastapi       import Request, Response
from fastapi.responses import RedirectResponse
from .             import proxy_router
from ..Libs.helpers import prepare_request_headers, shared_client, DEFAULT_REFERER, host_is_public

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


# Kırık poster negatif cache. Ölü/hotlink-korumalı poster URL'leri her rafta
# tekrar ediyor; her denemede kaynak CDN'e TLS + timeout turu yapılıyordu →
# raf geç doluyordu. Başarısız URL kısa süre hatırlanır, doğrudan fallback'e
# gidilir. TTL kısa: kaynak düzelirse kendiliğinden geri döner.
_NEG_TTL         = 600  # 10 dk
_NEG_MAX_ENTRIES = 2048
_neg_cache: "OrderedDict[str, float]" = OrderedDict()


def _neg_hit(url: str) -> bool:
    expires = _neg_cache.get(url)
    if expires is None:
        return False
    if expires <= time.monotonic():
        _neg_cache.pop(url, None)
        return False
    return True


def _neg_put(url: str) -> None:
    _neg_cache[url] = time.monotonic() + _NEG_TTL
    _neg_cache.move_to_end(url)
    while len(_neg_cache) > _NEG_MAX_ENTRIES:
        _neg_cache.popitem(last=False)


def _fallback(title: str | None, cache_state: str) -> Response:
    """Poster zincirinin son halkası: başlık varsa TMDB'ye yönlendir, yoksa 502.

    502'de istemci placeholder gösterir. Zincirin tamamı sunucuda olduğu için
    web şablonları, JS ve TV istemcisi aynı davranışı ayrı ayrı kurmak zorunda
    kalmaz (eskiden her biri kendi onerror zincirini taşıyordu).
    """
    if title:
        return RedirectResponse(
            url         = f"/tmdb-poster?title={quote(title, safe='')}",
            status_code = 302,
            headers     = {"Cache-Control": "public, max-age=3600", "X-Cache": cache_state},
        )
    return Response(status_code=502, content="Görsel alınamadı", headers={"X-Cache": cache_state})


@proxy_router.get("/image")
async def image_proxy(request: Request, url: str = "", referer: str = None, user_agent: str = None, title: str = ""):
    """Poster zinciri: kaynak → proxy cache → TMDB (başlıkla) → placeholder.

    `title` verilirse kaynak poster boş/kırık olduğunda TMDB fallback'ine
    yönlendirilir; verilmezse 502 döner ve istemci placeholder gösterir.
    """
    if not url:
        return _fallback(title, "SKIP")

    if _neg_hit(url):
        return _fallback(title, "NEG")

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
        # Şema/host hataları kalıcıdır: fallback'e düş ama negatif cache'e yazma
        # (girdi zaten sabit, yeniden denemenin maliyeti yok).
        return _fallback(title, "BAD_SCHEME")
    if not await host_is_public(parts.hostname or ""):
        return _fallback(title, "BLOCKED_HOST")

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
            _neg_put(url)
            return _fallback(title, f"UPSTREAM_{response.status_code}")

        content_type = response.headers.get("content-type", "").split(";")[0].strip().lower()
        if not content_type.startswith("image/"):
            _neg_put(url)
            return _fallback(title, "NOT_IMAGE")

        content = response.content
        if len(content) > _MAX_IMAGE_BYTES:
            _neg_put(url)
            return _fallback(title, "TOO_LARGE")

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
    except Exception:
        # Ağ/timeout hatası: URL'i kısa süre kara listeye al, zincirin bir sonraki
        # halkasına geç (TMDB varsa oraya, yoksa placeholder).
        _neg_put(url)
        return _fallback(title, "FETCH_ERROR")
