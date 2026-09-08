# NetMovies — PIN giriş ekranı (tünelden açılan site için).
#
# Bilerek sade: PIN kapısı yalnız "adresi tesadüfen bulan biri içeri girmesin"
# içindir, kimlik yönetimi değil. Doğru PIN girilince çerez bir yıl kalır; TV
# kumandasıyla ya da telefondan tek seferlik girilir.

import secrets

from Core            import Request, HTMLResponse, RedirectResponse
from Core.Modules._pin import _COOKIE, cerez_degeri, site_pin
from .                import home_router, home_template, build_context

# Kaba kuvvete karşı: aynı IP'den arka arkaya yanlış denemede gecikme yerine
# sayaç — PIN 4 hane, sınırsız deneme birkaç dakikada kırardı.
_YANLIS: dict[str, int] = {}
_TAVAN  = 10


@home_router.get("/giris", response_class=HTMLResponse)
async def giris_sayfasi(request: Request, devam: str = "/", hata: str = ""):
    context = await build_context(request)
    context.update({
        "title"       : "NetMovies — Giriş",
        "description" : "Devam etmek için PIN girin.",
        "devam"       : devam if devam.startswith("/") else "/",
        "giris_hatasi": hata,
    })
    return home_template.TemplateResponse(request=request, name="pages/giris.html.j2", context=context)


@home_router.post("/giris")
async def giris_dogrula(request: Request):
    veri  = request.state.veri or {}
    pin   = site_pin()
    devam = str(veri.get("devam") or "/")
    if not devam.startswith("/"):
        devam = "/"

    ip = request.headers.get("X-Forwarded-For", "").split(",")[0].strip() or (request.client.host if request.client else "?")
    if _YANLIS.get(ip, 0) >= _TAVAN:
        return RedirectResponse("/giris?hata=Cok+fazla+deneme.+Sunucuyu+yeniden+baslatin.", status_code=303)

    if not pin or not secrets.compare_digest(str(veri.get("pin") or ""), pin):
        _YANLIS[ip] = _YANLIS.get(ip, 0) + 1
        return RedirectResponse(f"/giris?hata=PIN+hatali&devam={devam}", status_code=303)

    _YANLIS.pop(ip, None)
    yanit = RedirectResponse(devam, status_code=303)
    yanit.set_cookie(
        _COOKIE, cerez_degeri(pin),
        max_age  = 365 * 24 * 3600,
        httponly = True,
        samesite = "lax",
        # Tünelde HTTPS, evde düz HTTP — secure=True LAN'da çerezi öldürürdü.
        secure   = request.url.scheme == "https",
    )
    return yanit
