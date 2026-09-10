# Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

import asyncio

from CLI    import konsol
from Core   import Request
from .      import api_v1_router, api_v1_global_message
from ..Libs import fuck_dmca, get_client_headers

# Ön-ısıtma görevleri: referans tutulmazsa GC çalışan Task'ı iptal eder.
_isitma_gorevleri: set[asyncio.Task] = set()


async def _isit(params: dict, client_headers: dict):
    """Detay ekranı açılır açılmaz çözümlemeyi arka planda başlatır.

    Kullanıcı "oynat"a bastığında beklediği 1.8–7.7 sn'lik çözümleme burada,
    o daha afişe bakarken yapılıyor; sonuç fuck_dmca cache'ine düşer (180 sn).
    """
    try:
        await fuck_dmca("/resolve_sources", params=params, timeout=25.0, client_headers=client_headers)
    except Exception as hata:
        konsol.log(f"[yellow]ön-ısıtma atlandı:[/] {type(hata).__name__}")


@api_v1_router.get("/load_item")
async def load_item(request:Request):
    veri           = dict(request.state.veri or {})
    client_headers = get_client_headers(request)
    result         = await fuck_dmca("/load_item", params=veri, client_headers=client_headers)

    # Dizide hangi bölümün açılacağı belli değil; film sayfası (bölüm listesi yok)
    # doğrudan oynatılacağı için ısıtmaya değer.
    if isinstance(result, dict) and not result.get("episodes") and veri.get("encoded_url"):
        gorev = asyncio.create_task(_isit(
            {"plugin": veri.get("plugin"), "encoded_url": veri["encoded_url"], "mode": "fast"},
            client_headers,
        ))
        _isitma_gorevleri.add(gorev)
        gorev.add_done_callback(_isitma_gorevleri.discard)

    return {**api_v1_global_message, "result": result}
