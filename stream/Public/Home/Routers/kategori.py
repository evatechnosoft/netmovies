# Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

from Core         import Request, HTMLResponse
from .            import home_router, home_template, build_context, get_provider_client, fuck_dmca, get_client_headers
from ..Libs        import admin_config
from urllib.parse import quote_plus, unquote

@home_router.get("/kategori/{eklenti_adi}", response_class=HTMLResponse)
async def kategori(request: Request, eklenti_adi: str, kategori_url: str, kategori_adi: str, sayfa: int = 1):
    context      = await build_context(request)
    provider_url = context.get("provider_url")

    try:
        items = []
        if provider_url:
            client = await get_provider_client(provider_url)
            items  = await client.get_main_page(eklenti_adi, kategori_url, sayfa, kategori_adi)
        else:
            items = await fuck_dmca("/get_main_page", params={
                "plugin"           : eklenti_adi,
                "page"             : str(sayfa),
                "encoded_url"      : kategori_url,
                "encoded_category" : kategori_adi
            }, client_headers=get_client_headers(request))

        # Admin puan eşiği süzmesi
        items = admin_config.filter_items(items)

        # Sayfalama yeteneği: yalnızca kategori URL'inde "SAYFA" placeholder'ı olan
        # kaynaklar gerçek sayfalama yapar (DiziYou, RecTV). HDFilmCehennemi gibi
        # placeholder'sız kaynaklar her sayfada AYNI içeriği döndürür — bu durumda
        # "sonraki sayfa" butonu gösterilmez (aksi halde tıklayınca içerik tekrar eder).
        can_paginate = "SAYFA" in unquote(kategori_url or "")

        context.update({
            "title"        : context["tr"]("title_category", provider_name=context["provider_name"], provider=eklenti_adi, category=kategori_adi),
            "description"  : context["tr"]("category_desc", provider=eklenti_adi, category=kategori_adi),
            "title_key"    : "title_category",
            "title_vars"   : {"provider_name": context["provider_name"], "provider": eklenti_adi, "category": kategori_adi},
            "desc_key"     : "category_desc",
            "desc_vars"    : {"provider": eklenti_adi, "category": kategori_adi},
            "eklenti_adi"  : eklenti_adi,
            "baslik"       : kategori_adi,
            "items"        : items,
            "kategori_url" : kategori_url,
            "kategori_adi" : kategori_adi,
            "sayfa"        : sayfa,
            "can_paginate" : can_paginate
        })

        return home_template.TemplateResponse(request=request, name="pages/category.html.j2", context=context)
    except Exception as hata:
        context = await build_context(
            request     = request,
            title       = "",
            description = "",
            title_key   = "title_error",
            title_vars  = {"context": f"{eklenti_adi} - {kategori_adi}"},
            desc_key    = "error_desc",
            desc_vars   = {},
            hata        = hata
        )
        context["title"]       = f"{context['tr']('error_title')} - {eklenti_adi} - {kategori_adi}"
        context["description"] = context["tr"]("error_desc")
        return home_template.TemplateResponse(request=request, name="pages/error.html.j2", context=context)
