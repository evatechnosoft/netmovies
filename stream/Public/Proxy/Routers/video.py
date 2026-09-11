# Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

from CLI                  import konsol
from fastapi              import Request, Response
from starlette.background import BackgroundTask
from fastapi.responses    import StreamingResponse
from .                    import proxy_router
from ..Libs.helpers       import prepare_request_headers, prepare_response_headers, detect_hls_from_url, stream_wrapper, rewrite_hls_manifest, is_hls_segment, open_upstream, parse_extra_headers, url_is_public
from ..Libs.segment_cache import segment_cache
from ..Libs.proxy_token   import validate_proxy_token

import asyncio
from urllib.parse import urljoin

# Ön-yükleme: kaç segment, ve görevlerin GC'ye yem olmaması için referans kümesi.
PREFETCH_COUNT   = 3
# Gövdeye bakarak manifest tespiti yapılırken okunacak üst sınır: segmentler
# megabaytlarca, manifest en fazla birkaç yüz KB (718 segmentlik varyant ~60 KB).
_MANIFEST_TAVANI = 1_000_000
_prefetch_tasks  : set[asyncio.Task] = set()


async def _prefetch(urls: list[str], request_headers: dict):
    for segment_url in urls:
        if await segment_cache.get(segment_url):
            continue
        try:
            response = await open_upstream(segment_url, request_headers)
        except Exception:
            continue
        try:
            if response.status_code < 400:
                content = await response.aread()
                if len(content) <= segment_cache.max_item_bytes:
                    await segment_cache.set(segment_url, content)
        except Exception:
            pass
        finally:
            await response.aclose()


def prefetch_segments(manifest: bytes, manifest_url: str, request_headers: dict):
    """Varyant manifestindeki ilk segmentleri arka planda cache'e çeker.

    Master manifestte satırlar başka m3u8'dir (segment değil) — `is_hls_segment`
    onları eler, o yüzden ayrıca ayrım yapmaya gerek yok.
    """
    hedefler: list[str] = []
    for line in manifest.decode("utf-8", "ignore").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        segment_url = urljoin(manifest_url, line)
        if is_hls_segment(segment_url):
            hedefler.append(segment_url)
        if len(hedefler) >= PREFETCH_COUNT:
            break
    if not hedefler:
        return
    gorev = asyncio.create_task(_prefetch(hedefler, request_headers))
    _prefetch_tasks.add(gorev)
    gorev.add_done_callback(_prefetch_tasks.discard)


@proxy_router.get("/video")
@proxy_router.head("/video")
async def video_proxy(request: Request, url: str, proxy_token: str = None, referer: str = None, user_agent: str = None, force_proxy: str = None, title: str = None, subtitle_url: str = None, extra_headers: str = None):
    """Video proxy endpoint'i"""
    target_url           = url
    if not proxy_token or not validate_proxy_token(proxy_token, target_url):
        konsol.print("[red]⛔ Proxy token geçersiz/süresi dolmuş[/red]")
        return Response(status_code=403, content="Geçersiz veya süresi dolmuş proxy token")
    if not await url_is_public(target_url):
        return Response(status_code=403, content="Hedef adres proxy'lenemez")
    parsed_extra_headers = parse_extra_headers(extra_headers)
    request_headers      = prepare_request_headers(request, target_url, referer, user_agent, parsed_extra_headers)
    is_force_proxy       = force_proxy == "1"

    # HLS segment ise cache'i kontrol et
    if is_hls_segment(target_url):
        cached_content = await segment_cache.get(target_url)
        if cached_content:
            # konsol.print(f"[green]✓ Cache HIT:[/green] {target_url[-50:]}")
            return Response(
                content     = cached_content,
                status_code = 200,
                headers     = {
                    "Content-Type"                : "video/MP2T" if target_url.endswith('.ts') else "video/iso.segment",
                    "Cache-Control"               : "public, max-age=30",
                    "Access-Control-Allow-Origin" : "*",
                },
            )

    try:
        # GET isteğini başlat (engelli kaynak WARP'a düşer)
        response = await open_upstream(target_url, request_headers)

        if response.status_code >= 400:
            await response.aclose()
            # Sessiz dönmüyoruz: izleme ortada koptuğunda "neden" sorusunun tek cevabı bu
            # satır. Kaynak imzası bayatladıysa 403, dosya taşındıysa 404 görünür.
            konsol.print(f"[red]⛔ Upstream {response.status_code}:[/red] {target_url[:110]}")
            return Response(status_code=response.status_code, content=f"Upstream Error: {response.status_code}")

        # 3. HLS Tespiti (URL + Header + GÖVDE)
        is_hls       = detect_hls_from_url(target_url)
        content_type = response.headers.get("content-type", "").lower()
        if "mpegurl" in content_type or "m3u8" in content_type:
            is_hls = True

        # Son çare gövde: kimi sağlayıcı manifesti uzantısız yolda ve yanlış
        # content-type ile veriyor (MolyStream: `/embed/<id>/q/1`, `text/html`).
        # URL kalıbına bakan tespit MASTER'ı tanıyıp ALT playlist'i kaçırıyordu;
        # alt playlist yeniden yazılmayınca segmentler ham CDN adresiyle
        # istemciye gidiyor, proxy jetonu o host'u kapsamıyor ve oynatma birkaç
        # saniye sonra kesiliyordu (Dean: "başlıyor, birkaç sn sonra bulunamadı").
        # Kalıp listesine her sağlayıcı için satır eklemek kırılgan; `#EXTM3U`
        # kesin imzadır.
        onden_okunan: bytes | None = None
        if not is_hls:
            uzunluk = response.headers.get("content-length")
            # Segmentler megabaytlarca; manifest en fazla birkaç yüz KB. Boyut
            # bilinmiyorsa da okunur — chunked manifest de var.
            if uzunluk is None or uzunluk.isdigit() and int(uzunluk) <= _MANIFEST_TAVANI:
                onden_okunan = await response.aread()
                if onden_okunan.lstrip()[:7] == b"#EXTM3U":
                    is_hls = True


        detected_content_type = "application/vnd.apple.mpegurl" if is_hls else None

        # Response headerlarını hazırla
        final_headers = prepare_response_headers(dict(response.headers), target_url, detected_content_type)
        # HEAD isteği ise stream yapma, kapat ve dön
        if request.method == "HEAD":
            await response.aclose()
            return Response(
                content     = b"",
                status_code = response.status_code,
                headers     = final_headers,
                media_type  = final_headers.get("Content-Type")
            )

        # HLS manifest ise içeriği yeniden yaz
        if is_hls:
            # Gövde tespiti için zaten okunmuşsa ikinci kez okunmaz (stream tükenir).
            content = onden_okunan if onden_okunan is not None else await response.aread()
            await response.aclose()

            # Manifest URL'lerini yeniden yaz
            rewritten_content = rewrite_hls_manifest(content, target_url, referer, user_agent, is_force_proxy, parsed_extra_headers, proxy_token)

            # Oynatma başlarken ilk segmentler daha istenmeden çekilsin: oynatıcı
            # varyant manifestini aldığı anda ilk N segmenti arka planda cache'e
            # alıyoruz, istemci sırası geldiğinde bellekten servis ediliyor.
            prefetch_segments(content, target_url, request_headers)

            # Content-Length güncelle
            final_headers["Content-Length"] = str(len(rewritten_content))

            return Response(
                content     = rewritten_content,
                status_code = response.status_code,
                headers     = final_headers,
                media_type  = final_headers.get("Content-Type")
            )

        # Gövde tespit için okunmuş ama manifest çıkmamışsa: akış tüketildi,
        # yeniden okunamaz — elde olan baytlar doğrudan döner.
        if onden_okunan is not None:
            await response.aclose()
            final_headers["Content-Length"] = str(len(onden_okunan))
            return Response(
                content     = onden_okunan,
                status_code = response.status_code,
                headers     = final_headers,
                media_type  = final_headers.get("Content-Type"),
            )

        # HLS segment ise ve cache'in tekil sınırına sığıyorsa belleğe al, aksi halde stream et
        if is_hls_segment(target_url):
            content_length = int(response.headers.get("content-length", "0"))
            # Sınır cache'in kendi ayarı (SEGMENT_ITEM_MB): burada 5MB sabiti vardı,
            # gerçek segmentler 3–8MB olduğu için çoğu hiç cache'lenmiyordu.
            if 0 < content_length <= segment_cache.max_item_bytes:
                content = await response.aread()
                await response.aclose()

                # Cache'e ekle
                await segment_cache.set(target_url, content)

                return Response(
                    content     = content,
                    status_code = response.status_code,
                    headers     = final_headers,
                    media_type  = final_headers.get("Content-Type")
                )

        # Normal video veya büyük/chunked segment - StreamingResponse döndür
        return StreamingResponse(
            stream_wrapper(response),
            status_code = response.status_code,
            headers     = final_headers,
            media_type  = final_headers.get("Content-Type"),
            background  = BackgroundTask(response.aclose)
        )

    except Exception as e:
        konsol.print(f"[red]⛔ Proxy hatası:[/red] {type(e).__name__}: {e} · {target_url[:90]}")
        # İstisna metni istemciye gitmez (iç adres/kütüphane detayı sızdırır); log'da duruyor.
        return Response(status_code=502, content="Kaynağa ulaşılamadı")
