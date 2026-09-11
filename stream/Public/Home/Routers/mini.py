# NetMovies — /mini: küçük ekran kumandası (saat / ana ekran kısayolu).
#
# `/rc` iki sekmeli, klavyeli tam kumanda; mini ise tek ekran: yay üzerinde
# yuvarlak posterler, yön pad (ya da dokunmatik yüzey) ve iki tuş. Kumandadan
# çıkarıldı (Dean: "mini orada olmasına gerek yok") — burada kendi sayfası.
#
# Uçlar ortak (`remote/command`, `remote/play`); sayfa site şablonunu miras
# almaz, başlık/arama çubuğu bu ekranda yer israfı olur.

from Core import Request, HTMLResponse
from .    import home_router, home_template, build_context


@home_router.get("/mini", response_class=HTMLResponse)
async def mini(request: Request):
    context = await build_context(request)
    context.update({
        "title"       : "NetMovies Mini",
        "description" : "Saat ve ana ekran için küçük kumanda.",
    })
    return home_template.TemplateResponse(request=request, name="pages/mini.html.j2", context=context)
