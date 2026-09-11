# NetMovies — Admin Panel (route + API)
# Merkezi yapılandırma: kaynak/kategori gizleme, öne çıkanlar, puan eşiği ve
# canlı kaynak sağlık durumu. Ayarlar sunucuda saklanır (admin_config).

from Core         import Request, HTMLResponse, JSONResponse
from .            import home_router, home_template, build_context, fuck_dmca, get_client_headers, get_provider_client
from ..Libs       import admin_config
from urllib.parse import unquote_plus

import os
import re


# --------------------------------------------------------------------------- Sayfa
@home_router.get("/admin", response_class=HTMLResponse)
async def admin_page(request: Request):
    context = await build_context(request)
    context.update({
        "title"       : "Yönetim - NetMovies",
        "description" : "Kaynak, kategori ve içerik yönetimi",
        "admin_config": admin_config.load_config(),
    })
    return home_template.TemplateResponse(request=request, name="pages/admin.html.j2", context=context)


# --------------------------------------------------------------------------- Config
@home_router.get("/api/admin/config")
async def admin_get_config():
    # Sırlar maskeli döner (Gemini anahtarı) — panel dolu/boş olduğunu görür,
    # değeri görmez.
    return JSONResponse(admin_config.masked_config())


@home_router.post("/api/admin/config")
async def admin_set_config(request: Request):
    try:
        body = await request.json()
    except Exception:
        return JSONResponse(status_code=400, content={"ok": False, "error": "Geçersiz JSON"})
    # Panel maskeli anahtarı geri gönderirse mevcut anahtar korunur.
    saved = admin_config.save_config(admin_config.merge_secrets(body))
    return JSONResponse({"ok": True, "config": admin_config.masked_config(saved)})


@home_router.post("/api/admin/featured")
async def admin_toggle_featured(request: Request):
    """Bir içeriği öne çıkanlara ekler/çıkarır (içerik sayfasındaki 'Öne çıkar' butonu)."""
    try:
        body = await request.json()
    except Exception:
        return JSONResponse(status_code=400, content={"ok": False, "error": "Geçersiz JSON"})

    url = (body or {}).get("url")
    if not url:
        return JSONResponse(status_code=400, content={"ok": False, "error": "url gerekli"})

    cfg      = admin_config.load_config()
    featured = cfg.get("featured", [])
    existing = next((f for f in featured if f.get("url") == url), None)
    if existing:
        featured = [f for f in featured if f.get("url") != url]
        state = "removed"
    else:
        featured.append({
            "provider": body.get("provider", ""),
            "url":      url,
            "title":    body.get("title", url),
            "poster":   body.get("poster", ""),
            "rating":   body.get("rating", ""),
        })
        state = "added"
    cfg["featured"] = featured
    admin_config.save_config(cfg)
    return JSONResponse({"ok": True, "state": state, "count": len(featured)})


# --------------------------------------------------------------------------- Katalog (ham)
@home_router.get("/api/admin/catalog")
async def admin_catalog(request: Request):
    """Tüm kaynak ve kategorileri (gizlenmemiş ham hali) döndürür — admin işaretleme için."""
    context      = await build_context(request)
    provider_url = context.get("provider_url")
    try:
        if provider_url:
            client  = await get_provider_client(provider_url)
            plugins = await client.get_plugins()
        else:
            plugins = await fuck_dmca("/get_all_plugins", request.state.veri, client_headers=get_client_headers(request))
    except Exception as hata:
        return JSONResponse(status_code=502, content={"ok": False, "error": str(hata)})

    catalog = []
    for p in plugins or []:
        cats = [unquote_plus(str(c)) for c in (p.get("main_page") or {}).values()]
        catalog.append({"name": p.get("name"), "main_url": p.get("main_url", ""), "categories": sorted(set(cats))})
    return JSONResponse({"ok": True, "plugins": catalog})


# --------------------------------------------------------------------------- Sağlık
@home_router.get("/api/admin/health")
async def admin_health(request: Request):
    """Yerel motor eklenti sağlığı + (varsa) uzak sağlayıcı erişilebilirliği."""
    context      = await build_context(request)
    provider_url = context.get("provider_url")

    if provider_url:
        # Uzak sağlayıcı: tekil erişilebilirlik kontrolü
        try:
            client = await get_provider_client(provider_url)
            await client.get_provider_name()
            return JSONResponse({"ok": True, "mode": "remote", "provider": provider_url,
                                 "result": {"total": 1, "healthy": 1, "unhealthy": 0,
                                            "plugins": [{"plugin": "Uzak Sağlayıcı", "main_url": provider_url, "ok": True, "status": "reachable"}]}})
        except Exception as hata:
            return JSONResponse({"ok": True, "mode": "remote", "provider": provider_url,
                                 "result": {"total": 1, "healthy": 0, "unhealthy": 1,
                                            "plugins": [{"plugin": "Uzak Sağlayıcı", "main_url": provider_url, "ok": False, "status": "unreachable", "note": str(hata)}]}})

    # Yerel motor: engine'in plugin_health endpoint'ini forward et.
    # ?force=1 → engine'in 6 saatlik cache'ini atla, tüm domainleri CANLI yeniden tara
    # ("Yeniden kontrol et"/Tazele butonu bunu tetikler).
    force  = request.query_params.get("force") == "1"
    veri   = {"force": "1"} if force else None
    try:
        result = await fuck_dmca("/plugin_health", veri, client_headers=get_client_headers(request))
        return JSONResponse({"ok": True, "mode": "local", "result": result})
    except Exception as hata:
        return JSONResponse(status_code=502, content={"ok": False, "error": str(hata)})


# --------------------------------------------------------------------------- Eklenti Repoları (GitHub / CloudStream)
@home_router.get("/api/admin/repos")
async def admin_get_repos():
    """Kayıtlı eklenti repolarını ve eklentilerini döndürür."""
    import httpx
    cfg   = admin_config.load_config()
    repos = cfg.get("custom_repos", [])
    
    results = []
    async with httpx.AsyncClient(timeout=5.0) as client:
        for r in repos:
            url = r.get("url", "").strip()
            item = {"name": r.get("name", "Bilinmeyen Repo"), "url": url, "enabled": r.get("enabled", True), "plugins": [], "error": None}
            if not url:
                continue
            try:
                # repo.json veya plugins.json
                res = await client.get(url)
                if res.status_code == 200:
                    data = res.json()
                    # repo.json ise pluginLists'e bak
                    if isinstance(data, dict) and "pluginLists" in data:
                        plist_url = data["pluginLists"][0]
                        plist_res = await client.get(plist_url)
                        if plist_res.status_code == 200:
                            data = plist_res.json()
                    
                    if isinstance(data, list):
                        item["plugins"] = [{"name": p.get("name"), "version": p.get("version"), "authors": p.get("authors", []), "description": p.get("description", "")} for p in data]
                else:
                    item["error"] = f"HTTP {res.status_code}"
            except Exception as exc:
                item["error"] = str(exc)
            results.append(item)
            
    return JSONResponse({"ok": True, "repos": results})


@home_router.post("/api/admin/repos")
async def admin_save_repos(request: Request):
    """Repo listesini günceller."""
    try:
        body = await request.json()
        repos = body.get("repos", [])
    except Exception:
        return JSONResponse(status_code=400, content={"ok": False, "error": "Geçersiz JSON"})
        
    cfg = admin_config.load_config()
    cfg["custom_repos"] = repos
    admin_config.save_config(cfg)
    return JSONResponse({"ok": True, "custom_repos": cfg["custom_repos"]})


# --------------------------------------------------------------------------- Canlı kanallar
# Kendi kanal listesi. Satır biçimi kasten sade: `Ad | URL | Grup` — M3U yazmak
# kullanıcıya kalmasın. Adres m3u8 olabileceği gibi yayıncının RESMİ YouTube canlı
# yayını da olabilir (ATV, Show TV…); eklenti YouTube'u yt-dlp ile çözer.
#
# Engine bu dosyayı `/data/lists/ozel.m3u` olarak salt-okunur bağlıyor; stream
# aynı klasörü `/lists` altında yazılabilir görüyor. Eklenti dosyanın değişme
# zamanına bakıyor, kaydetmek için yeniden başlatma gerekmiyor.
_KANAL_DOSYASI = "/lists/ozel.m3u"
_KANAL_SATIRI  = re.compile(r'^#EXTINF:-1(?P<oz>[^,]*),(?P<ad>.*)$')


def _kanallari_oku() -> list[dict[str, str]]:
    try:
        with open(_KANAL_DOSYASI, "r", encoding="utf-8") as dosya:
            satirlar = dosya.read().splitlines()
    except OSError:
        return []

    kanallar : list[dict[str, str]] = []
    bekleyen : dict[str, str] | None = None
    for satir in satirlar:
        eslesme = _KANAL_SATIRI.match(satir.strip())
        if eslesme:
            grup = re.search(r'group-title="([^"]*)"', eslesme.group("oz"))
            bekleyen = {"ad": eslesme.group("ad").strip(), "grup": grup.group(1) if grup else "Genel", "url": ""}
        elif bekleyen and satir.strip() and not satir.startswith("#"):
            bekleyen["url"] = satir.strip()
            kanallar.append(bekleyen)
            bekleyen = None
    return kanallar


@home_router.get("/api/admin/kanallar")
async def admin_get_kanallar():
    return JSONResponse({"ok": True, "kanallar": _kanallari_oku()})


@home_router.post("/api/admin/kanallar")
async def admin_save_kanallar(request: Request):
    body     = await request.json()
    kanallar = body.get("kanallar")
    if not isinstance(kanallar, list):
        return JSONResponse({"ok": False, "hata": "kanallar listesi bekleniyor"}, status_code=400)

    satirlar = ["#EXTM3U"]
    temiz    : list[dict[str, str]] = []
    for kanal in kanallar[:200]:
        if not isinstance(kanal, dict):
            continue
        ad  = str(kanal.get("ad") or "").strip()[:80]
        url = str(kanal.get("url") or "").strip()[:500]
        # Yalnız http(s): dosya yolu yazılırsa engine kabının içine bakar, kafa karıştırır.
        if not ad or not url.startswith(("http://", "https://")):
            continue
        grup = (str(kanal.get("grup") or "").strip() or "Genel")[:40]
        satirlar.append(f'#EXTINF:-1 group-title="{grup}",{ad}')
        satirlar.append(url)
        temiz.append({"ad": ad, "url": url, "grup": grup})

    try:
        os.makedirs(os.path.dirname(_KANAL_DOSYASI), exist_ok=True)
        with open(_KANAL_DOSYASI, "w", encoding="utf-8") as dosya:
            dosya.write("\n".join(satirlar) + "\n")
    except OSError as hata:
        return JSONResponse({"ok": False, "hata": f"yazılamadı: {hata}"}, status_code=500)

    return JSONResponse({"ok": True, "kanallar": temiz})
