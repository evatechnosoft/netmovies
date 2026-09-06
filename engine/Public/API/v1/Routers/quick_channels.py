"""Small, fast channel feed for the home page."""

import asyncio
import time
from urllib.parse import urlsplit

import httpx

from CLI  import konsol
from Core import Request
from .    import api_v1_router, api_v1_global_message
from ..Libs import plugin_manager

# Ölü yayın sunucuları listeden düşürülür: iptv-org listesi bayatlıyor ve süresi
# dolmuş domainler (park sayfasına düşen `nord.ayakkabiparti.lol` gibi) kanal
# kanal denenip "açılmıyor" olarak yaşanıyordu. Kontrol HOST başına yapılır —
# tek domain onlarca kanalı taşıyor, kanal başına istek gereksiz.
_HOST_TTL   = 6 * 3600
_host_cache: dict[str, tuple[float, bool]] = {}


async def _host_ok(client: httpx.AsyncClient, url: str) -> bool:
    host = urlsplit(url).netloc
    if not host:
        return False

    hit = _host_cache.get(host)
    if hit and time.monotonic() - hit[0] < _HOST_TTL:
        return hit[1]

    ok = False
    try:
        # Akışın kendisi indirilmez: bağlantı kurulup ilk yanıt başlığı yeter.
        resp = await client.get(url, headers={"User-Agent": "Mozilla/5.0"})
        ok   = resp.status_code < 400
    except Exception:
        ok = False

    _host_cache[host] = (time.monotonic(), ok)
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

    hosts = {urlsplit(c["url"] or "").netloc: (c["url"] or "") for c in channels}
    async with httpx.AsyncClient(timeout=6, follow_redirects=True) as client:
        sonuc = await asyncio.gather(*(_host_ok(client, url) for url in hosts.values()))
    canli = {host for host, ok in zip(hosts, sonuc) if ok}

    elenen = [c for c in channels if urlsplit(c["url"] or "").netloc not in canli]
    if elenen:
        konsol.log(f"[yellow]∅ canlı:[/] {len(elenen)} kanal elendi · ölü sunucu: {sorted({urlsplit(c['url'] or '').netloc for c in elenen})}")

    return [c for c in channels if urlsplit(c["url"] or "").netloc in canli]


@api_v1_router.get("/quick_channels")
async def quick_channels(request: Request):
    del request
    return {**api_v1_global_message, "result": await collect_live_channels()}
