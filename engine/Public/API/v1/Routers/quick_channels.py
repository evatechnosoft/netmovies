"""Small, fast channel feed for the home page."""

import asyncio
import time
from urllib.parse import urlsplit

import httpx

from CLI  import konsol
from Core import Request
from .    import api_v1_router, api_v1_global_message
from ..Libs import plugin_manager
from ..Libs import epg

# Ölü yayınlar listeden düşürülür: iptv-org listesi bayatlıyor ve süresi dolmuş
# adresler kanal kanal denenip "açılmıyor" olarak yaşanıyordu.
#
# Kontrol AKIŞ ADRESİ başına yapılır, host başına değil: sunucu ayakta olduğu
# hâlde tek bir kanalın yolu 404 dönebiliyor — ATV ve Beyaz TV tam olarak böyle
# ölüydü, host süzgeci ikisini de canlı sayıyordu. İstekler paralel; maliyet
# liste tazelenirken bir kez ödenir.
#
# Önbellek ASİMETRİK: "canlı" uzun, "ölü" kısa yaşar. Yayın sunucuları anlık
# tökezliyor (tek yavaş yanıt = timeout) ve simetrik önbellekte ATV gibi ana bir
# kanal tek kötü denemeyle yarım gün listeden düşüyordu. Ölü sonucu kısa tutmak
# kanala bir sonraki tazelemede yeniden şans verir.
_CANLI_TTL   = 6 * 3600
_OLU_TTL     = 15 * 60
_stream_cache: dict[str, tuple[float, bool]] = {}
# Aynı anda açılan bağlantı tavanı — liste büyüdükçe (kategori listeleri yüzlerce
# kanal getirebilir) engine'i kendi sağlık taramasıyla boğmamak için.
_ESZAMANLI   = 40


async def _stream_ok(client: httpx.AsyncClient, url: str, kapi: asyncio.Semaphore) -> bool:
    if not urlsplit(url).netloc:
        return False

    hit = _stream_cache.get(url)
    if hit and time.monotonic() - hit[0] < (_CANLI_TTL if hit[1] else _OLU_TTL):
        return hit[1]

    ok = False
    try:
        async with kapi:
            # Akışın kendisi indirilmez: bağlantı kurulup ilk yanıt başlığı yeter.
            resp = await client.get(url, headers={"User-Agent": "Mozilla/5.0"})
            ok   = resp.status_code < 400
    except Exception:
        ok = False

    _stream_cache[url] = (time.monotonic(), ok)
    return ok


async def collect_live_channels(check_health: bool = True) -> list[dict[str, str | None]]:
    """M3U tabanlı eklentilerin tüm gruplarındaki kanalları düz listeye açar.

    M3U grup adları listenin kendisinden gelir (iptv-org'da "Animation", "News"
    gibi İngilizce), yani kategori adına bakan ipucu eşleşmesiyle bulunamazlar.
    Canlı akışın tek kaynağı burasıdır; aggregate_new de bunu kullanır.
    """
    channels: list[dict[str, str | None]] = []
    for name in plugin_manager.get_plugin_names():
        plugin = plugin_manager.select_plugin(name)
        if plugin.main_url != "m3u://local":
            continue
        for category_url, category in plugin.main_page.items():
            for item in await plugin.get_main_page(1, category_url, category):
                channels.append({"plugin": name, "title": item.title, "url": item.url, "poster": item.poster, "category": item.category})

    if not check_health or not channels:
        return channels

    adresler = sorted({c["url"] or "" for c in channels})
    kapi     = asyncio.Semaphore(_ESZAMANLI)
    async with httpx.AsyncClient(timeout=12, follow_redirects=True) as client:
        sonuc = await asyncio.gather(*(_stream_ok(client, url, kapi) for url in adresler))
    canli = {url for url, ok in zip(adresler, sonuc) if ok}

    elenen = [c for c in channels if (c["url"] or "") not in canli]
    if elenen:
        konsol.log(f"[yellow]∅ canlı:[/] {len(elenen)} kanal elendi · ölü: {sorted({str(c['title']) for c in elenen})[:20]}")

    return [c for c in channels if (c["url"] or "") in canli]


@api_v1_router.get("/quick_channels")
async def quick_channels(request: Request):
    del request
    kanallar = await collect_live_channels()

    # Rehber ilk çağrıda indirilir (6 saat tazedir); indirilemezse kanal listesi
    # yine döner, yalnız "şimdi" alanı boş kalır — canlı TV rehber yüzünden
    # açılmamazlık etmesin.
    await epg.tazele()
    for kanal in kanallar:
        bilgi = epg.simdi(str(kanal.get("title") or ""))
        if bilgi:
            kanal["simdi"] = bilgi

    return {**api_v1_global_message, "result": kanallar}
