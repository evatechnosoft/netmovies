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
    # Türk (yerli) diziler — kaynak "Yerli Diziler" gibi etiketler.
    "serie_local": [("yerli",), ("türk",)],
    # Yabancı diziler — kaynak "Yabancı Diziler" gibi etiketler.
    "serie_foreign": [("yabanc",)],
    # Canlı TV / kanallar.
    "live": [("canlı", "tv"), ("canlı",), ("kanal",)],
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

    # Canlı TV: M3U listelerinin grup adları listeden gelir ("News", "Sports"…),
    # bu yüzden _HINTS'teki Türkçe ipuçlarıyla bulunamaz ve raf boş kalıyordu.
    # Canlı akışın tek kaynağı quick_channels toplayıcısıdır.
    if media_type == "live":
        from .quick_channels import collect_live_channels

        live_items = [
            {
                "plugin":   channel["plugin"],
                "title":    channel["title"],
                # Diğer tiplerle aynı sözleşme: istemciler encoded url bekliyor.
                "url":      quote_plus(channel["url"] or ""),
                "poster":   channel["poster"],
                "category": channel["category"],
            }
            for channel in await collect_live_channels()
        ]
        return {**api_v1_global_message, "result": {"type": media_type, "count": len(live_items), "items": live_items}}

    names = plugin_manager.get_plugin_names()

    # Ölü domainli kaynakları atla: aksi halde her biri timeout'a kadar (6s)
    # bekletir ve ana sayfayı yavaşlatır. Sağlık bilinmiyorsa hepsini dene.
    try:
        from .plugin_health import run_plugin_health
        health = await run_plugin_health()
        healthy = {p["plugin"] for p in health.get("plugins", []) if p.get("ok")}
        if healthy:
            names = [n for n in names if n in healthy]
    except Exception:
        pass

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


# Ana sayfa "sabit kategori kartları": içerik döndürmese bile HER ZAMAN tıklanabilir
# giriş noktaları. Her kart, tercih edilen kaynak sırasından main_page'inde uygun
# kategorisi bulunan İLK eklentinin (domain runtime'da resolve edilmiş) kategori
# URL'ine bağlanır. Kaynak canlılığından bağımsız — link her zaman geçerlidir.
_HOME_CARDS = [
    {"key": "movie",   "title": "Filmler",         "icon": "fa-film",            "type": "movie",         "plugins": ["HDFilmCehennemi", "RecTV"]},
    {"key": "tv_local","title": "Türk Diziler",     "icon": "fa-tv",              "type": "serie_local",   "plugins": ["DiziMom", "DiziBox"]},
    {"key": "tv_foreign","title": "Yabancı Diziler","icon": "fa-globe",           "type": "serie_foreign", "plugins": ["DiziMom", "Dizilla", "DiziYou"]},
    {"key": "live",    "title": "Canlı TV",         "icon": "fa-broadcast-tower", "type": "live",          "plugins": ["RecTV"]},
]


@api_v1_router.get("/home_categories")
async def home_categories(request: Request):
    """Ana sayfa sabit kategori kartları — her kartın resolved kategori URL'i.

    Yalnızca SAĞLIKLI (erişilebilir) kaynaklar kart olur: ölü domainli kaynak
    kart yapılmaz → kullanıcı 'ulaşılamıyor' hatasıyla karşılaşmaz. Bir kart için
    tercih sırasındaki ilk sağlıklı kaynak seçilir; hiçbiri sağlıklı değilse kart
    hiç görünmez.
    """
    from .plugin_health import run_plugin_health

    available = set(plugin_manager.get_plugin_names())
    try:
        health  = await run_plugin_health()
        healthy = {p["plugin"] for p in health.get("plugins", []) if p.get("ok")}
    except Exception:
        healthy = available  # sağlık bilinmiyorsa hepsini dene (kart kaybetme)

    cards = []
    for card in _HOME_CARDS:
        for name in card["plugins"]:
            if name not in available or name not in healthy:
                continue
            try:
                plugin = plugin_manager.select_plugin(name)
                url, cat = _pick_category(plugin.main_page, card["type"])
            except Exception:
                url, cat = None, None
            if url:
                cards.append({
                    "key":          card["key"],
                    "title":        card["title"],
                    "icon":         card["icon"],
                    "plugin":       name,
                    "kategori_url": quote_plus(url),
                    "kategori_adi": cat,
                })
                break  # bu kart için ilk uygun kaynak yeter

    return {**api_v1_global_message, "result": cards}
