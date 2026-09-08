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


@home_router.get("/rc", response_class=HTMLResponse)
async def kumanda(request: Request):
    context = await build_context(request)
    context.update({
        "title"       : "NetMovies Kumanda",
        "description" : "Telefondan televizyonu sür: ara, oynat, duraklat, yaz.",
    })
    return home_template.TemplateResponse(request=request, name="pages/rc.html.j2", context=context)
