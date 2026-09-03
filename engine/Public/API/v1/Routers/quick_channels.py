"""Small, fast channel feed for the home page."""

from Core import Request
from . import api_v1_router, api_v1_global_message
from ..Libs import plugin_manager


async def collect_live_channels() -> list[dict[str, str | None]]:
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
    return channels


@api_v1_router.get("/quick_channels")
async def quick_channels(request: Request):
    del request
    return {**api_v1_global_message, "result": await collect_live_channels()}
