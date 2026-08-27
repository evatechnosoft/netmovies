# NetMovies — Birleşik "Yeni Çıkanlar"
# Kullanıcı kaynak kaynak gezmesin: tüm eklentilerin "Son/Yeni Filmler" ve
# "Son/Yeni Diziler" kategorilerini PARALEL çekip tek listede birleştirir.
# Hata veren/çalışmayan kaynak sessizce atlanır → yalnızca çalışan (yeşil) kaynaklar gelir.

import asyncio

from Core   import Request, JSONResponse
from .      import api_v1_router, api_v1_global_message
from ..Libs import plugin_manager

from urllib.parse import quote_plus

# type -> kategori adı ipuçları (öncelik sırasıyla)
_HINTS = {
    "movie": [("son", "film"), ("yeni", "film"), ("film",)],
    # Some providers label their freshest feed as "Son Bölümler" rather
    # than "Yeni Diziler". Keep this provider-independent so a source's
    # naming convention cannot hide it from the home page.
    "serie": [
        ("son", "dizi"),
        ("yeni", "dizi"),
        ("son", "bölüm"),
        ("dizi",),
        ("bölüm",),
    ],
}


def _pick_category(main_page: dict, media_type: str):
    """Bir eklentinin main_page'inden 'yeni/son + tür' eşleşen ilk kategoriyi seçer."""
    hints = _HINTS.get(media_type, [])
    items = list(main_page.items())  # [(url, category), ...]
    for needles in hints:
        for url, cat in items:
            low = str(cat).lower()
            if all(n in low for n in needles):
                return url, cat
    return None, None


async def _fetch_from(name: str, page: int, media_type: str):
    plugin = plugin_manager.select_plugin(name)
    url, cat = _pick_category(plugin.main_page, media_type)
    if not url:
        return []
    results = await plugin.get_main_page(page, url, cat)
    out = []
    for item in results or []:
        item_url = getattr(item, "url", "") or ""
        item_title = getattr(item, "title", None)
        item_category = str(getattr(item, "category", cat) or cat)
        media_hint = f"{item_title or ''} {item_url} {item_category}".lower()
        if "dublaj" in media_hint and "dublaj" not in item_category.lower():
            item_category = f"{item_category} · Dublaj"
        elif ("altyaz" in media_hint or "sub" in media_hint) and "altyaz" not in item_category.lower():
            item_category = f"{item_category} · Altyazı"
        out.append({
            "plugin":   name,
            "title":    item_title,
            "url":      quote_plus(item_url),
            "poster":   getattr(item, "poster", None),
            "category": item_category,
        })
    return out


@api_v1_router.get("/aggregate_new")
async def aggregate_new(request: Request):
    istek       = request.state.veri or {}
    media_type  = istek.get("type", "movie")
    page        = istek.get("page", "1")
    page        = int(page) if str(page).isdigit() else 1

    names = plugin_manager.get_plugin_names()
    batches = await asyncio.gather(
        *(_fetch_from(n, page, media_type) for n in names),
        return_exceptions=True,
    )

    merged = []
    for b in batches:
        if isinstance(b, Exception):
            continue  # çalışmayan kaynağı atla
        merged.extend(b)

    return {**api_v1_global_message, "result": {"type": media_type, "count": len(merged), "items": merged}}
