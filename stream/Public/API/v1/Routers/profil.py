# NetMovies — Profil seçimi (yüklü uygulamalar). Cihaz `X-NM-Cihaz` başlığını yollar;
# ProfileMiddleware cihazı ilk görüşte atanmamış olarak kaydeder (kendi geçmişi).
# Uygulama açılışta GET /profil çağırır; `current` değişince yerel liste önbelleğini siler.
#
# Bu uçlar PIN kapısının önünde (TV çerez taşımaz); kaba kuvvete karşı fren
# watch_store.select_profile'daki profil başına deneme kilidi.

from Core import Request
from .    import api_v1_router, api_v1_global_message

from Core.Modules._pin import site_pin
from Public.Home.Libs  import watch_store


def _device(request: Request) -> str:
    return (request.headers.get("x-nm-cihaz") or "").strip()[:64]


@api_v1_router.get("/profil")
async def profil_get(request: Request):
    veri   = request.state.veri or {}
    device = _device(request)
    current = watch_store.profile_for_device(device, str(veri.get("label") or "")) if device else "dean"
    return {**api_v1_global_message, "result": {
        "current" : current,
        "profiles": watch_store.list_profiles(),
    }}


@api_v1_router.post("/profil/pin")
async def profil_pin(request: Request):
    """Yeni cihazın tek seferlik PIN'i: eşleşen profil cihaza atanır."""
    veri   = request.state.veri or {}
    device = _device(request)
    if not device:
        return {**api_v1_global_message, "result": {"status": "no_device"}}
    status, profile = watch_store.claim_by_pin(device, str(veri.get("pin") or ""))
    return {**api_v1_global_message, "result": {"status": status, "current": profile}}


@api_v1_router.post("/profil/sec")
async def profil_sec(request: Request):
    veri    = request.state.veri or {}
    device  = _device(request)
    profile = str(veri.get("profile") or "")
    if not device:
        return {**api_v1_global_message, "result": {"status": "no_device"}}
    # Dean'in kendi PIN'i yoksa site PIN'i geçerli (bugünkü tek kapı).
    fallback = site_pin() if profile == "dean" else ""
    status = watch_store.select_profile(device, profile, str(veri.get("pin") or ""), fallback)
    return {**api_v1_global_message, "result": {"status": status}}
