# NetMovies — /tv: televizyon arayüzü (LG webOS ve diğer eski TV tarayıcıları).
#
# Ana web arayüzü fare/dokunma için yazıldı ve modern sözdizimi kullanıyor;
# LG'nin tarayıcısı Chrome 68 olduğu için orada script blokları SyntaxError ile
# ölüyor (kanıt: TV DevTools, "Unexpected token ."). Bu sayfa bilerek ayrı:
# site şablonunu miras almaz, kendi ES2017 kodunu taşır, yalnız D-pad bilir.

from Core import Request, HTMLResponse
from .    import home_router, home_template, build_context


@home_router.get("/tv", response_class=HTMLResponse)
async def tv(request: Request):
    context = await build_context(request)
    context.update({
        "title"       : "NetMovies TV",
        "description" : "Televizyon için D-pad arayüzü.",
    })
    return home_template.TemplateResponse(
        request = request,
        name    = "pages/tv.html.j2",
        context = context,
        # webOS'un uygulama tarayıcısı sayfayı agresif önbelleğe alıyor: sunucu yeni
        # sürümü verirken televizyon günler öncesinin kodunu çalıştırabiliyor
        # (kanıt: TV DevTools'ta eski script, sunucuda yenisi). Tek sayfalık arayüz,
        # önbellekten kazanacağı bir şey yok.
        headers = {"Cache-Control": "no-store, must-revalidate", "Pragma": "no-cache"},
    )
