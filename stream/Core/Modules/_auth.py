# NetMovies — Basit Basic Auth (public erişim için)
# AUTH_USER boşsa tamamen devre dışıdır (upstream davranışı korunur).
# Muaf yollar: /proxy (harici oynatıcı ve hls.js auth header göndermez),
# sağlık/statik/PWA uçları — böylece Nova/MX ve "ana ekrana ekle" çalışır.

import os
import base64
import secrets

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.responses       import Response

AUTH_USER = os.getenv("AUTH_USER", "")
AUTH_PASS = os.getenv("AUTH_PASS", "")

# Bu öneklerle başlayan yollar kimlik doğrulamadan muaf
_EXEMPT_PREFIXES = (
    "/proxy",          # video/altyazı segmentleri — harici oynatıcı auth taşımaz
    "/static",
    "/sw.js",
    "/favicon",
    "/manifest",
    "/resmi-kaynak",  # allowlisted broadcaster iframe player shell
    "/health",
    "/api/v1/health",
)


ADMIN_PASS = os.getenv("ADMIN_PASS", "")

# Yalnız yönetim yüzeyi — izleme tarafı açık kalır
_ADMIN_PREFIXES = (
    "/admin",
    "/api/admin",
)


class AdminAuthMiddleware(BaseHTTPMiddleware):
    """Sadece /admin ve /api/admin'i parola ile korur.

    Genel Basic Auth kapalıyken (AUTH_USER boş) bile yönetim paneli public tünelden
    şifresiz erişilebiliyordu; kaynak/kategori ayarları herkese açıktı. Kullanıcı adı
    önemsizdir, yalnız ADMIN_PASS doğrulanır — kumandayla girmesi kolay olsun diye.
    ADMIN_PASS boşsa koruma devre dışıdır (eski davranış).
    """

    async def dispatch(self, request, call_next):
        if not ADMIN_PASS:
            return await call_next(request)

        if not request.url.path.startswith(_ADMIN_PREFIXES):
            return await call_next(request)

        header = request.headers.get("Authorization", "")
        if header.startswith("Basic "):
            try:
                decoded  = base64.b64decode(header[6:]).decode("utf-8")
                _, _, pwd = decoded.partition(":")
                if secrets.compare_digest(pwd, ADMIN_PASS):
                    return await call_next(request)
            except Exception:
                pass

        return Response(
            status_code = 401,
            headers     = {"WWW-Authenticate": 'Basic realm="NetMovies Admin"'},
            content     = "Yönetim paneli parolası gerekli",
        )


class BasicAuthMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request, call_next):
        # Auth kapalı (kullanıcı/şifre verilmemiş) → dokunma
        if not AUTH_USER:
            return await call_next(request)

        path = request.url.path
        if any(path.startswith(p) for p in _EXEMPT_PREFIXES):
            return await call_next(request)

        header = request.headers.get("Authorization", "")
        if header.startswith("Basic "):
            try:
                decoded = base64.b64decode(header[6:]).decode("utf-8")
                user, _, pwd = decoded.partition(":")
                if secrets.compare_digest(user, AUTH_USER) and secrets.compare_digest(pwd, AUTH_PASS):
                    return await call_next(request)
            except Exception:
                pass

        return Response(
            status_code = 401,
            headers     = {"WWW-Authenticate": 'Basic realm="NetMovies"'},
            content     = "Giriş gerekli",
        )
