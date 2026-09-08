# NetMovies — /rc: telefon kumandası (Remote Control).
#
# Kendi başına duran tek sayfa: site şablonunu (header/footer/SEO) MİRAS ALMAZ.
# Kumandanın tamamı ekrana sığmalı ve başparmakla sürülmeli; site başlığı, arama
# çubuğu ve ayar paneli burada yer israfı olurdu.
#
# Aynı PWA scope'unda (`/`) yaşar: telefona zaten kurulu NetMovies uygulamasının
# kısayolu doğrudan buraya düşer (manifest.webmanifest → shortcuts).

from Core import Request, HTMLResponse
from .    import home_router, home_template, build_context
from ..Libs import admin_config


@home_router.get("/rc", response_class=HTMLResponse)
async def kumanda(request: Request):
    context = await build_context(request)
    cfg = admin_config.load_config()
    context.update({
        "title"       : "NetMovies Kumanda",
        "description" : "Telefondan televizyonu sür: ara, oynat, duraklat, yaz.",
        # Panelden açılıp kapanır; aynı değer TV'ye de client_config ile gider.
        "rc_show_recent" : bool(cfg.get("rc_show_recent", True)),
        "rc_text_to_tv"  : bool(cfg.get("rc_text_to_tv", True)),
    })
    return home_template.TemplateResponse(request=request, name="pages/rc.html.j2", context=context)
