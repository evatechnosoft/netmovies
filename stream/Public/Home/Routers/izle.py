# Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

from CLI  import konsol
from Core import Request, HTMLResponse, HTTPException
from uuid import NAMESPACE_URL, uuid5
from .    import home_router, home_template, build_context, get_provider_client, fuck_dmca, get_client_headers

from json         import loads, dumps
from urllib.parse import urlparse, parse_qs, unquote, unquote_plus
from Settings     import PROXY_URL, PROXY_FALLBACK_URL
from Public.Proxy.Libs.proxy_token import issue_proxy_token
from Public.API.v1.Libs.language     import language_name, language_rank, order_by_language
from ..Libs.official_sources import get_official_sources


def _normalize_encoded_payload(value: str | None) -> str:
    """
    Query içinden gelen encoded_url/content_url için normalize:
    - JSON ise kompakt canonical JSON string'e çevirir
    - Düz metin ise bir tur unquote_plus yapar
    """
    if not value:
        return ""

    try:
        parsed = loads(value)
        return dumps(parsed, ensure_ascii=False, separators=(",", ":"))
    except Exception:
        pass

    return unquote_plus(str(value))


@home_router.get("/resmi-kaynak", response_class=HTMLResponse)
async def resmi_kaynak(request: Request, url: str):
    """Render an allowlisted broadcaster page inside the PWA player shell."""
    source = next((item for item in get_official_sources() if item["url"] == url), None)
    if source is None:
        raise HTTPException(status_code=404, detail="Resmi kaynak bulunamadı")

    context = await build_context(request)
    context.update({
        "title": f"{source['title']} - {context['provider_name']}",
        "description": source["description"],
        "official_source": source,
    })
    return home_template.TemplateResponse(
        request=request,
        name="pages/official_player.html.j2",
        context=context,
    )


@home_router.get("/izle/{eklenti_adi}", response_class=HTMLResponse)
async def izle(
    request: Request,
    eklenti_adi: str,
    url: str,
    baslik: str,
    bolum_adi: str | None = None,
    provider_id: str | None = None,
    content_url: str | None = None,
    content_id: str | None = None,
    poster_url: str | None = None,
    year: str | None = None,
    rating: str | None = None,
    season: str | None = None,
    episode: str | None = None,
):
    # --- Parameter Cleanup ---
    url         = _normalize_encoded_payload(url)
    baslik      = unquote_plus(baslik)
    bolum_adi   = unquote_plus(bolum_adi) if bolum_adi else None
    content_url = _normalize_encoded_payload(content_url) if content_url else url
    poster_url  = unquote(poster_url) if poster_url else None
    # -------------------------

    context              = await build_context(request)
    provider_url         = context.get("provider_url")
    provider_base_url    = (provider_url or str(request.base_url)).strip().rstrip("/")
    resolved_provider_id = (provider_id or "").strip() or (
        str(uuid5(NAMESPACE_URL, provider_base_url)) if provider_base_url else ""
    )

    # Enhance title if it's a series episode
    full_baslik = baslik
    if season and episode:
        sezon_bolum = f" - S{season} E{episode}"
        if sezon_bolum not in full_baslik:
            full_baslik += sezon_bolum

        if bolum_adi:
            full_baslik += f" - {bolum_adi}"

    try:
        load_links_data = []

        if provider_url:
            client          = await get_provider_client(provider_url)
            load_links_data = await client.load_links(eklenti_adi, url)
            proxy_urls      = await client.get_proxy_urls()
        else:
            # Local provider için Settings'den proxy bilgilerini al
            proxy_urls = {
                "proxy_url"          : PROXY_URL or str(request.base_url).rstrip("/"),
                "proxy_fallback_url" : PROXY_FALLBACK_URL,
            }

            # Zincirin tamamı sunucuda: seçili sağlayıcı + alternatifler + dil sırası.
            # Web de artık TV ile aynı kaynak havuzunu görüyor (eskiden yalnız
            # seçili sağlayıcının linkleriyle yetiniyordu).
            resolved = await fuck_dmca("/resolve_sources", params={
                "plugin"      : eklenti_adi,
                "encoded_url" : url,
                "title"       : baslik or "",
                "mode"        : "full",
            }, timeout=60.0, client_headers=get_client_headers(request))

            load_links_data = (resolved or {}).get("sources") or []
            for entry in (resolved or {}).get("diagnostics") or []:
                konsol.log(f"[dim]· izle:[/] {entry.get('stage')} — {entry.get('message')}")


        links = []
        for link in load_links_data:
            # link might be an object or dict
            if isinstance(link, dict):
                links.append(
                    {
                        "name"          : link.get("name"),
                        "url"           : link.get("url"),
                        "referer"       : link.get("referer") or "",
                        "user_agent"    : link.get("user_agent") or "",
                        "extra_headers" : link.get("extra_headers") or {},
                        "subtitles"     : link.get("subtitles") or [],
                    }
                )

            else:
                subtitles = []
                if link.subtitles:
                    subtitles = [sub.model_dump() for sub in link.subtitles]

                links.append(
                    {
                        "name"          : link.name,
                        "url"           : link.url,
                        "referer"       : link.referer or "",
                        "user_agent"    : link.user_agent or "",
                        "extra_headers" : getattr(link, "extra_headers", None) or {},
                        "subtitles"     : subtitles,
                    }
                )

        # Dil tercihi: Türkçe dublaj → Türkçe altyazı → bilinmiyor (API ile aynı kural).
        links = order_by_language(links)
        if links:
            konsol.log(
                f"[green]▶ izle:[/] {eklenti_adi} · {len(links)} kaynak · ilk sıra: "
                f"{language_name(language_rank(links[0]))}"
            )
        else:
            konsol.log(f"[yellow]∅ izle:[/] {eklenti_adi} · oynatılabilir kaynak yok")

        proxy_urls_for_token = [str(item.get("url") or "") for item in links]
        proxy_urls_for_token += [
            str(subtitle.get("url") or "")
            for item in links
            for subtitle in item.get("subtitles", [])
            if isinstance(subtitle, dict)
        ]
        proxy_token = issue_proxy_token(proxy_urls_for_token)

        referer    = request.headers.get("referer")
        icerik_url = None
        if referer:
            parsed = urlparse(referer)
            params = parse_qs(parsed.query)
            if "url" in params and params["url"]:
                icerik_url = params["url"][0]

        context.update(
            {
                "title": context["tr"](
                    "title_player", provider_name=context["provider_name"], title=full_baslik
                ),
                "description"   : context["tr"]("player_desc", title=full_baslik),
                "title_key"     : "title_player",
                "title_vars"    : {
                    "provider_name" : context["provider_name"],
                    "title"         : full_baslik,
                },
                "desc_key"           : "player_desc",
                "desc_vars"          : {"title": full_baslik},
                "eklenti_adi"        : f"{eklenti_adi}",
                "baslik"             : full_baslik,
                "icerik_url"         : icerik_url,
                "links"              : links,
                "provider_name"      : context.get("provider_name"),
                "proxy_url"          : proxy_urls["proxy_url"],
                "proxy_fallback_url" : proxy_urls["proxy_fallback_url"],
                "proxy_token"        : proxy_token,
                "media_meta"         : {
                    "provider_id"        : resolved_provider_id,
                    "provider_base_url"  : provider_base_url or "",
                    "plugin_name"        : eklenti_adi or "",
                    "content_id"         : content_id or "",
                    "content_url"        : content_url,
                    "poster_url"         : poster_url or "",
                    "year"               : year or "",
                    "rating"             : rating or "",
                    "season"             : season or "",
                    "episode"            : episode or "",
                },
            }
        )

        return home_template.TemplateResponse(request=request, name="pages/player.html.j2", context=context)
    except Exception as hata:
        context = await build_context(
            request     = request,
            title       = "",
            description = "",
            title_key   = "title_error",
            title_vars  = {"context": f"{eklenti_adi} - {baslik}"},
            desc_key    = "error_desc",
            desc_vars   = {},
            hata        = hata,
        )
        context["title"] = f"{context['tr']('error_title')} - {eklenti_adi} - {baslik}"
        context["description"] = context["tr"]("error_desc")
        return home_template.TemplateResponse(request=request, name="pages/error.html.j2", context=context)
