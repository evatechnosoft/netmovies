# NetMovies — Eklenti Sağlık Kontrolü
# Her eklentinin hedef sitesine (main_url) hafif bir istek atıp erişilebilirliğini
# raporlar. Site erişilemiyorsa "domain değişmiş olabilir" uyarısı verir; böylece
# hangi kaynakların elden geçirilmesi gerektiği her gün görülebilir.
#
# Sonuçlar TTL ile cache'lenir (varsayılan 6 saat) — sık çağrı kaynak sitelerini
# yormaz. `?force=1` ile taze kontrol yapılır.

import time
import asyncio
import httpx

from Core   import Request
from .      import api_v1_router, api_v1_global_message
from ..Libs import plugin_manager

_TTL_SECONDS = 6 * 3600
_cache: dict = {"checked_at": 0.0, "results": None}


async def _check_one(name: str) -> dict:
    plugin   = plugin_manager.select_plugin(name)
    main_url = getattr(plugin, "main_url", "") or ""

    # Yerel/sanal kaynaklar (örn. M3U) site kontrolü gerektirmez
    if not main_url.startswith(("http://", "https://")):
        return {"plugin": name, "main_url": main_url, "ok": True, "status": "local", "note": "Yerel kaynak"}

    try:
        async with httpx.AsyncClient(timeout=8, follow_redirects=True) as client:
            resp = await client.get(
                main_url,
                headers={"User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 15.7; rv:135.0) Gecko/20100101 Firefox/135.0"},
            )
        ok = resp.status_code < 400
        return {
            "plugin"   : name,
            "main_url" : main_url,
            "ok"       : ok,
            "status"   : resp.status_code,
            "note"     : "" if ok else "Site hata döndü — domain değişmiş olabilir",
        }
    except Exception as hata:
        return {
            "plugin"   : name,
            "main_url" : main_url,
            "ok"       : False,
            "status"   : "unreachable",
            "note"     : f"Erişilemedi ({type(hata).__name__}) — domain değişmiş olabilir",
        }


async def run_plugin_health(force: bool = False) -> dict:
    now = time.monotonic()
    if not force and _cache["results"] is not None and (now - _cache["checked_at"]) < _TTL_SECONDS:
        return _cache["results"]

    names   = plugin_manager.get_plugin_names()
    checks  = await asyncio.gather(*(_check_one(n) for n in names))
    healthy = sum(1 for c in checks if c["ok"])

    payload = {
        "total"      : len(checks),
        "healthy"    : healthy,
        "unhealthy"  : len(checks) - healthy,
        "checked_at" : int(time.time()),
        "plugins"    : sorted(checks, key=lambda c: (c["ok"], c["plugin"])),
    }
    _cache["results"]    = payload
    _cache["checked_at"] = now
    return payload


@api_v1_router.get("/plugin_health")
async def plugin_health(request: Request):
    force  = request.query_params.get("force") == "1"
    result = await run_plugin_health(force=force)
    return {**api_v1_global_message, "result": result}


# ----------------------------------------------------------------------------
# Günlük otomatik kontrol: açılışta bir kez, sonra 24 saatte bir tazeler.
# Sonuç cache'e yazılır; /plugin_health ve UI anında okur.
# ----------------------------------------------------------------------------
from Core import kekik_FastAPI  # noqa: E402


@kekik_FastAPI.on_event("startup")
async def _schedule_daily_health():
    async def _loop():
        while True:
            try:
                await run_plugin_health(force=True)
            except Exception:
                pass
            await asyncio.sleep(24 * 3600)

    asyncio.create_task(_loop())
