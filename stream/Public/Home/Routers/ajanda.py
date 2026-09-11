# NetMovies — /ajanda: "bu hafta ne var, ne zaman".
#
# Veri `/api/v1/agenda`'dan gelir (TMDB, günde bir tazelenir). Sayfa yalnız
# çizer: hesap ve önbellek tek yerde, uçta.

from Core import Request, HTMLResponse
from .    import home_router, home_template, build_context

from Public.API.v1.Routers.agenda import agenda_verisi


@home_router.get("/ajanda", response_class=HTMLResponse)
async def ajanda(request: Request, view: str = "week"):
    view    = "month" if str(view).lower() == "month" else "week"
    context = await build_context(request)

    sonuc = await agenda_verisi(view)
    context.update({
        "title"       : "Ajanda",
        "description" : "Yayınlanacak bölümler ve vizyona girecek filmler.",
        "view"        : view,
        "gunler"      : sonuc.get("gunler") or [],
        "toplam"      : sonuc.get("toplam") or 0,
    })
    return home_template.TemplateResponse(request=request, name="pages/ajanda.html.j2", context=context)
