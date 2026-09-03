# Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

from CLI            import konsol
from Core           import Request
from .              import api_v1_router, api_v1_global_message
from ..Libs         import fuck_dmca, get_client_headers
from ..Libs.language import language_name, language_rank, order_by_language
from ..Libs.source_proxy import route_through_proxy

@api_v1_router.get("/load_links")
async def load_links(request:Request):
    result = await fuck_dmca("/load_links", params=request.state.veri, timeout=25.0, client_headers=get_client_headers(request))

    # Dil tercihi tek kuralla, tek yerde: Türkçe dublaj → Türkçe altyazı → bilinmiyor.
    # Web ve TV aynı sırayı görür (TV ayrıca kendi tarafında da uygular).
    if isinstance(result, list):
        # Eski istemciler (güncellenmemiş APK) imza başlığı üretemez; imzalı
        # kaynaklar burada da proxy'ye bağlanır ki onlarda da oynatma çalışsın.
        result = route_through_proxy(order_by_language(result), str(request.base_url).rstrip("/"))
        plugin = (request.state.veri or {}).get("plugin", "?")
        if result:
            konsol.log(
                f"[green]▶ load_links:[/] {plugin} · {len(result)} kaynak · ilk sıra: {language_name(language_rank(result[0]))}"
            )
        else:
            konsol.log(f"[yellow]∅ load_links:[/] {plugin} · kaynak yok")

    return {**api_v1_global_message, "result": result}
