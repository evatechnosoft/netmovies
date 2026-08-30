# Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

import asyncio

from Core import Request, HTMLResponse, JSONResponse
from .    import home_router, home_template, build_context, get_provider_client, fuck_dmca, get_client_headers
from ..Libs import admin_config
from ..Libs.official_sources import get_official_sources

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
                    data = await asyncio.wait_for(client.get_aggregate_new(media_type), timeout=6)
                else:
                    data = await fuck_dmca(
                        "/aggregate_new",
                        params         = {"type": media_type},
                        timeout        = 6,
                        client_headers = get_client_headers(request),
                    )
                items = data.get("items", []) if isinstance(data, dict) else []
                return admin_config.filter_aggregate_items(items[:40], admin_cfg)
            except Exception:
                return []  # kaynak yeni-çıkanlar veremezse ana sayfa yine açılsın

        async def _quick_channels() -> list:
            try:
                if provider_url:
                    return (await asyncio.wait_for(client.get_quick_channels(), timeout=3))[:60]
                quick_payload = await fuck_dmca(
                    "/quick_channels", timeout=3, client_headers=get_client_headers(request)
                )
                return quick_payload[:60] if isinstance(quick_payload, list) else []
            except Exception:
                return []

        async def _home_categories() -> list:
            # Sabit kategori kartları — içerik gerektirmez, her zaman tıklanabilir.
            try:
                if provider_url:
                    return await asyncio.wait_for(client.get_home_categories(), timeout=3)
                payload = await fuck_dmca(
                    "/home_categories", timeout=3, client_headers=get_client_headers(request)
                )
                return payload if isinstance(payload, list) else []
            except Exception:
                return []

        (
            yeni_filmler,
            yeni_turk_diziler,
            yeni_yabanci_diziler,
            quick_channels,
            home_categories,
        ) = await asyncio.gather(
            _aggregate("movie"),
            _aggregate("serie_local"),
            _aggregate("serie_foreign"),
            _quick_channels(),
            _home_categories(),
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
            "official_sources": get_official_sources(),
            "home_categories": home_categories,
            "yeni_filmler" : yeni_filmler,
            "yeni_turk_diziler" : yeni_turk_diziler,
            "yeni_yabanci_diziler" : yeni_yabanci_diziler,
            "quick_channels": quick_channels,
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
