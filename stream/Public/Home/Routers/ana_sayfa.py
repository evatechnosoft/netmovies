# Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

import asyncio

from Core import Request, HTMLResponse, JSONResponse
from .    import home_router, home_template, build_context, get_provider_client, fuck_dmca, get_client_headers
from ..Libs import admin_config

@home_router.get("/health")
@home_router.head("/health")
async def health_check():
    """API sağlık kontrolü"""
    return JSONResponse({"success": True, "status": "healthy"})

@home_router.get("/", response_class=HTMLResponse)
async def ana_sayfa(request: Request):
    """Ana sayfa - Tüm eklentileri listeler"""
    context      = await build_context(request)
    provider_url = context.get("provider_url")

    plugins = []
    try:
        if provider_url:
            client  = await get_provider_client(provider_url)
            plugins = await client.get_plugins()
        else:
            plugins = await fuck_dmca("/get_all_plugins", request.state.veri, client_headers=get_client_headers(request))

        # Admin panel süzmesi: gizli kaynak/kategoriler çıkarılır
        admin_cfg = admin_config.load_config()
        plugins   = admin_config.filter_plugins(plugins, admin_cfg)

        # Birleşik "Yeni Çıkanlar": kullanıcı kaynak kaynak gezmesin diye tüm
        # çalışan eklentilerin yeni film/dizilerini tek yatay rafta topla.
        async def _aggregate(media_type: str) -> list:
            try:
                if provider_url:
                    data = await client.get_aggregate_new(media_type)
                else:
                    data = await fuck_dmca(
                        "/aggregate_new",
                        params         = {"type": media_type},
                        client_headers = get_client_headers(request),
                    )
                items = data.get("items", []) if isinstance(data, dict) else []
                return admin_config.filter_aggregate_items(items, admin_cfg)
            except Exception:
                return []  # kaynak yeni-çıkanlar veremezse ana sayfa yine açılsın

        yeni_filmler, yeni_diziler = await asyncio.gather(
            _aggregate("movie"), _aggregate("serie")
        )

        context.update({
            "title"        : context["tr"]("home_title", provider_name=context["provider_name"]),
            "description"  : context["tr"]("home_desc"),
            "title_key"    : "home_title",
            "title_vars"   : {"provider_name": context["provider_name"]},
            "desc_key"     : "home_desc",
            "desc_vars"    : {},
            "plugins"      : plugins,
            "featured"     : admin_cfg.get("featured", []),
            "yeni_filmler" : yeni_filmler,
            "yeni_diziler" : yeni_diziler
        })

        response = home_template.TemplateResponse(request=request, name="pages/home.html.j2", context=context)

        # Query'den gelen provider varsa cookie'ye kaydet (Normalleştirilmiş URL'i kaydet)
        if provider_url and request.query_params.get("provider"):
            response.set_cookie(key="provider_url", value=provider_url, max_age=31536000, samesite="lax") # 1 year

        return response
    except Exception as hata:
        context = await build_context(
            request     = request,
            title       = "",
            description = "",
            title_key   = "title_error",
            title_vars  = {"context": context["provider_name"]},
            desc_key    = "error_desc",
            desc_vars   = {},
            hata        = hata
        )
        context["title"]       = f"{context['tr']('error_title')} - {context['provider_name']}"
        context["description"] = context["tr"]("error_desc")
        return home_template.TemplateResponse(request=request, name="pages/error.html.j2", context=context)
