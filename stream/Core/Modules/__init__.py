# Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

from fastapi    import FastAPI
from contextlib import asynccontextmanager
from CLI        import konsol
from contextlib import suppress
import asyncio

_availability_checked = True
_availability_lock    = asyncio.Lock()

async def _check_plugin(name: str, plugin, sem: asyncio.Semaphore) -> tuple[str, bool, int | None]:
    if not getattr(plugin, "main_url", None):
        return name, False, None

    async with sem:
        try:
            istek = await plugin.httpx.get(plugin.main_url)
            return name, istek.status_code == 200, istek.status_code
        except Exception:
            return name, False, None

# "Yeni Çıkanlar" soğuk agregasyonu ~40 sn sürüyor ve TTL 600 sn. TTL dolduktan
# sonra ilk isteyen bu bedeli ödüyordu — saat uygulamasında açılış yarım dakika
# boş ekran demekti. Cache TTL dolmadan arka planda tazelenir: isteyen hep sıcak
# cache'i görür. Aralık TTL'in altında olmalı, yoksa arada soğuk pencere kalır.
_ISITMA_ARALIGI = 480
_ISITILAN_TIPLER = ("movie", "serie")


async def _cache_isit() -> None:
    from Public.API.v1.Libs import fuck_dmca

    while True:
        for tip in _ISITILAN_TIPLER:
            with suppress(Exception):
                # İstemcinin gönderdiği parametrelerle BİREBİR aynı olmalı:
                # cache anahtarı params'tan üretiliyor, fazladan bir alan ıskalar.
                await fuck_dmca("/aggregate_new", params={"type": tip}, timeout=90.0)
        await asyncio.sleep(_ISITMA_ARALIGI)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """FastAPI lifespan events - startup ve shutdown"""
    global _availability_checked

    async with _availability_lock:
        if not _availability_checked:
            try:
                from Public.API.v1.Libs import plugin_manager

                plugin_items = list(plugin_manager.plugins.items())
                sem          = asyncio.Semaphore(10)
                checks       = [_check_plugin(name, plugin, sem) for name, plugin in plugin_items]
                results      = await asyncio.gather(*checks)

                removed_count = 0
                for name, is_available, status_code in results:
                    if name in ["WebteIzle"]:
                        continue

                    if is_available:
                        continue

                    plugin = plugin_manager.plugins.pop(name, None)
                    if plugin:
                        with suppress(Exception):
                            await plugin.close()
                        removed_count += 1
                        konsol.log(f"[red][!] Eklentiye erişilemiyor ({status_code or 'ERR'}) : {plugin.name} | {plugin.main_url}")

                _availability_checked = True
                if plugin_items:
                    konsol.log(
                        f"[green]Eklenti erişim kontrolleri tamamlandı. "
                        f"({len(plugin_manager.plugins)}/{len(plugin_items)} aktif, {removed_count} kaldırıldı, max 10 eşzamanlı)"
                    )
            except Exception as hata:
                konsol.log(f"[yellow][!] Eklenti erişim kontrolü atlandı: {hata}")

    isitici = asyncio.create_task(_cache_isit())

    yield

    # Shutdown
    isitici.cancel()
    with suppress(Exception, asyncio.CancelledError):
        await isitici

    with suppress(Exception):
        from Public.Proxy.Libs.helpers import shared_client, warp_client
        await shared_client.aclose()
        if warp_client is not None:
            await warp_client.aclose()

    with suppress(Exception):
        from Public.Home.Libs.provider_client import close_all_provider_clients
        await close_all_provider_clients()
