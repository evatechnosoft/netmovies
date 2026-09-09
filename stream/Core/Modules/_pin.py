# NetMovies — PIN kapısı: siteyi tünelden açarken tarayıcı tarafına giriş ekranı.
#
# Neden Basic Auth DEĞİL: TV istemcisi (client-tv, Retrofit) Authorization başlığı
# taşımıyor. AUTH_USER doldurulduğu anda televizyon 401 alıp katalogsuz kalıyor —
# .env'de o yüzden boş duruyor. Burada koruma ÇEREZLE yapılır: tarayıcı çerezi
# taşır, TV taşımaz, ikisi de çalışır.
#
# Neyi korur: tarayıcı yüzeyi (sayfalar) ve KUMANDA uçları. Kumanda uçları kritik —
# tünel açıkken adresi bilen biri televizyonu sürebilirdi.
#
# Neyi korumaz (bilerek): TV istemcisinin çağırdığı katalog/oynatma uçları. Onlar
# PIN taşıyamaz. Yani ham JSON katalog tünelden görülebilir; korunan şey siteye
# girmek, televizyonu sürmek ve yönetim.

import hashlib
import hmac
import ipaddress
import os
import secrets

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.responses       import RedirectResponse, Response

_COOKIE = "nm_giris"

# Tarayıcının PIN olmadan da alması gerekenler: giriş ekranının kendisi, statik
# dosyalar, PWA kabuğu, sağlık ucu ve video akışı (harici oynatıcı çerez taşımaz;
# o taraf zaten imzalı jetonla korunuyor).
_MUAF = (
    "/giris",
    "/proxy",
    "/static",
    "/sw.js",
    "/favicon",
    "/manifest",
    "/health",
    "/api/v1/health",
)

# TV istemcisinin ASLA çağırmadığı, yalnız kumandanın kullandığı uçlar. Tünel
# açıkken bunlar korunmazsa yabancı biri televizyonda içerik başlatabilir.
_KORUMALI_API = (
    "/api/v1/remote/command",
    "/api/v1/remote/play",
    "/api/v1/voice",
    "/api/v1/search_all",
)


def site_pin() -> str:
    """PIN: önce yönetim panelinden, yoksa .env'den. Boş = kapı kapalı değil, YOK."""
    try:
        from Public.Home.Libs import admin_config
        deger = str(admin_config.load_config().get("site_pin") or "").strip()
    except Exception:
        deger = ""
    return deger or os.getenv("SITE_PIN", "").strip()


def cerez_degeri(pin: str) -> str:
    """Çerez PIN'in kendisini TAŞIMAZ; PIN'den türetilmiş bir imza taşır.

    Böylece çerezi okuyan PIN'i öğrenemez ve PIN değiştirildiğinde eskiden
    girilmiş bütün cihazlar kendiliğinden düşer."""
    gizli = os.getenv("PROXY_TOKEN_SECRET", "") or "netmovies"
    return hmac.new(gizli.encode(), f"pin:{pin}".encode(), hashlib.sha256).hexdigest()[:32]


def girisli_mi(request) -> bool:
    pin = site_pin()
    if not pin:
        return True
    return secrets.compare_digest(request.cookies.get(_COOKIE, ""), cerez_degeri(pin))


def lan_istegi(request) -> bool:
    """İstek ev ağından mı geldi? Kapı tünel (w.evaitec.com) için kuruldu; evdeki
    telefon uygulaması (okhttp, çerez taşımaz) `remote/play` atınca 401 alıyordu.
    Docker ardında istemci IP'si hep 172.31.0.1 göründüğü için ayrım Host'tan:
    Cloudflare tünel isteklerinin Host'u her zaman tünel alan adı, özel IP olamaz."""
    host = request.url.hostname or ""
    if host in ("localhost", "127.0.0.1"):
        return True
    try:
        return ipaddress.ip_address(host).is_private
    except ValueError:
        return False


class SitePinMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request, call_next):
        pin = site_pin()
        if not pin:
            return await call_next(request)

        yol = request.url.path
        if yol.startswith(_MUAF):
            return await call_next(request)

        korumali = yol.startswith(_KORUMALI_API) or not yol.startswith("/api/")
        if not korumali or lan_istegi(request) or girisli_mi(request):
            return await call_next(request)

        # API'de yönlendirme işe yaramaz (fetch HTML'i JSON sanır) — açık 401.
        if yol.startswith("/api/"):
            return Response(status_code=401, content='{"result":{"ok":false,"error":"giris gerekli"}}',
                            media_type="application/json")

        hedef = request.url.path
        if request.url.query:
            hedef += f"?{request.url.query}"
        return RedirectResponse(f"/giris?devam={hedef}", status_code=303)
