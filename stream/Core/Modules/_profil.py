# NetMovies — Profil seçimi: yüklü uygulama `X-NM-Cihaz` başlığıyla cihaz kimliğini
# yollar; sunucu cihazın atandığı profili isteğin bağlamına koyar. watch_store tüm
# geçmiş/favori/liste sorgularını bu bağlamdan okur, router'lar değişmez.
#
# Başlıksız istek (tarayıcı, saat) → varsayılan profil (dean). Atanmamış cihaz kendi
# geçmişini tutar (`cihaz:<id>`); profile bağlama admin'den, uygulamada seçim yok.
# Saf ASGI: BaseHTTPMiddleware'in ayrı görevi bağlamı yutmasın diye.

from Public.Home.Libs import watch_store

_HEADER = b"x-nm-cihaz"
_MODEL  = b"x-nm-model"


class ProfileMiddleware:
    def __init__(self, app) -> None:
        self.app = app

    async def __call__(self, scope, receive, send) -> None:
        if scope["type"] != "http" or not scope["path"].startswith("/api/"):
            await self.app(scope, receive, send)
            return
        headers   = dict(scope["headers"])
        device_id = headers.get(_HEADER, b"").decode("latin-1")
        label     = headers.get(_MODEL, b"").decode("latin-1")
        profile   = watch_store.profile_for_device(device_id, label) if device_id else "dean"
        token = watch_store.CURRENT_PROFILE.set(profile)
        try:
            await self.app(scope, receive, send)
        finally:
            watch_store.CURRENT_PROFILE.reset(token)
