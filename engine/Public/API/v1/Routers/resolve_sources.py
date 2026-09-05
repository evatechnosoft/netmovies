# NetMovies — oynatma kaynağı çözümleyicisi (TEK uç, tüm istemciler için).
#
# Aynı film/dizi birden çok sitede var. Eskiden bu zinciri her istemci kendi
# içinde kuruyordu: TV uygulaması altı sağlayıcıyı tek tek arıyor, web yalnız
# seçili sağlayıcıyla yetiniyordu. Aynı iş iki yerde, iki farklı davranış.
#
# Artık zincir burada:
#   seçili sağlayıcı → (dizi ise bölüm çözme) → alternatif sağlayıcılarda arama
#   → link toplama → teşhis kaydı
# İstemci (TV / telefon / web) yalnızca listeyi tüketir.
#
#   /api/v1/resolve_sources?plugin=X&encoded_url=Y&title=Z&episode=0&mode=fast|full
#
# mode=fast : yalnız seçili sağlayıcı — ilk oynatma beklemesin.
# mode=full : alternatifler dahil — istemci oynatırken arka planda çağırır.

import asyncio

from CLI    import konsol
from Core   import Request, JSONResponse
from .      import api_v1_router, api_v1_global_message
from ..Libs import plugin_manager
from .plugin_health import run_plugin_health

from urllib.parse import quote_plus

# Alternatif tarama sırası — dublaj ağırlıklı kaynaklar önde.
ALTERNATIVE_ORDER = ["HDFilmCehennemi", "DiziBox", "DiziYou", "DiziMom", "Dizilla", "RecTV"]

# Bir alternatif sağlayıcıya ayrılan üst süre. Ölü site (DNS/connect timeout) eskiden
# httpx'in kendi süresine kadar zinciri bekletiyordu; bütçe aşılırsa o sağlayıcı atlanır.
ALTERNATIVE_TIMEOUT = 25

# Arama başlığındaki site gürültüsü.
_NOISE = (
    "izle", "full hd", "hd", "4k", "1080p", "1080", "720p", "720",
    "türkçe", "turkce", "dublaj", "altyazılı", "altyazili", "altyazı", "altyazi",
    "dizisi", "filmi",
)


def clean_title(title: str | None) -> str:
    text = (title or "").lower()
    for noise in _NOISE:
        text = text.replace(noise, " ")
    return " ".join(text.split()).strip(" -·:")


class Diagnostics:
    """İstemciye de dönen teşhis kaydı — 'neden açılmadı' sorusu artık cevaplanabilir."""

    def __init__(self) -> None:
        self.entries: list[dict[str, str]] = []

    def add(self, level: str, stage: str, message: str) -> None:
        self.entries.append({"level": level, "stage": stage, "message": message})
        mark = {"info": "[green]•[/]", "warn": "[yellow]![/]", "fail": "[red]x[/]"}.get(level, "•")
        konsol.log(f"{mark} resolve: {stage} — {message}")


async def _links_for(plugin_name: str, encoded_url: str, episode_index: int, diag: Diagnostics) -> tuple[list[dict], list[dict]]:
    """Bir sağlayıcıdan link listesi (ve varsa bölüm listesi) çıkarır."""
    plugin   = plugin_manager.select_plugin(plugin_name)
    episodes : list[dict] = []
    target   = encoded_url

    async def _load(url: str) -> list:
        try:
            return await plugin.load_links(url) or []
        except Exception as hata:
            diag.add("fail", "link", f"{plugin_name} · {type(hata).__name__}: {hata}")
            return []

    links = await _load(target)

    if not links:
        # Dizi ana sayfası olabilir: bölüm listesini çöz, seçili bölümü dene.
        try:
            info     = await plugin.load_item(target)
            episode_objects = getattr(info, "episodes", None) or []
        except Exception as hata:
            diag.add("fail", "bölüm", f"{plugin_name} · {type(hata).__name__}: {hata}")
            episode_objects = []

        if episode_objects:
            episodes = [
                {
                    "title"  : getattr(ep, "title", None),
                    "url"    : quote_plus(getattr(ep, "url", "") or ""),
                    "season" : getattr(ep, "season", None),
                    "episode": getattr(ep, "episode", None),
                }
                for ep in episode_objects
            ]
            diag.add("info", "bölüm", f"{plugin_name} · {len(episodes)} bölüm")
            chosen = episode_objects[episode_index] if 0 <= episode_index < len(episode_objects) else episode_objects[0]
            links  = await _load(getattr(chosen, "url", "") or "")

    if not links:
        diag.add("warn", "link", f"{plugin_name} · oynatılabilir kaynak vermedi")
        return [], episodes

    diag.add("info", "link", f"{plugin_name} · {len(links)} kaynak")
    return [
        {
            "plugin"     : plugin_name,
            "name"       : f"{plugin_name} · {(link.name or 'Oynatıcı')}",
            "url"        : link.url,
            "referer"    : link.referer or "",
            "user_agent" : link.user_agent or "",
            "extra_headers": getattr(link, "extra_headers", None) or {},
            "subtitles"  : [sub.model_dump() for sub in (link.subtitles or [])],
        }
        for link in links
    ], episodes


async def _search_match(plugin_name: str, query: str, diag: Diagnostics) -> str | None:
    """Alternatif sağlayıcıda aynı başlığı arar, en olası eşleşmenin URL'ini döner."""
    try:
        plugin  = plugin_manager.select_plugin(plugin_name)
        results = await plugin.search(query) or []
    except Exception as hata:
        diag.add("fail", "arama", f"{plugin_name} · {type(hata).__name__}: {hata}")
        return None

    if not results:
        diag.add("warn", "arama", f"{plugin_name} · sonuç yok")
        return None

    def matches(result) -> bool:
        title = (getattr(result, "title", "") or "").lower()
        return query in title or title in query

    chosen = next((r for r in results if matches(r)), results[0])
    diag.add("info", "arama", f"{plugin_name} · eşleşti: {getattr(chosen, 'title', '?')}")
    return getattr(chosen, "url", None)


async def _with_budget(coro, plugin_name: str, stage: str, diag: Diagnostics):
    """Süre bütçesini aşan sağlayıcıyı atlar; zincir tek ölü siteye takılmaz."""
    try:
        return await asyncio.wait_for(coro, timeout=ALTERNATIVE_TIMEOUT)
    except asyncio.TimeoutError:
        diag.add("warn", stage, f"{plugin_name} · {ALTERNATIVE_TIMEOUT}sn bütçesi aşıldı — atlandı")
        return None


@api_v1_router.get("/resolve_sources")
async def resolve_sources(request: Request):
    istek        = request.state.veri or {}
    plugin_names = plugin_manager.get_plugin_names()

    selected = istek.get("plugin")
    content  = istek.get("encoded_url")
    if selected not in plugin_names or not content:
        return JSONResponse(status_code=410, content={
            "hata": f"{request.url.path}?plugin=<eklenti>&encoded_url=<icerik>&title=<baslik>&mode=fast|full",
        })

    title   = istek.get("title") or ""
    mode    = (istek.get("mode") or "full").lower()
    episode = istek.get("episode", "0")
    episode = int(episode) if str(episode).isdigit() else 0

    diag = Diagnostics()
    diag.add("info", "oturum", f"{title or '?'} · seçili sağlayıcı: {selected} · mod: {mode}")

    sources, episodes = await _links_for(selected, content, episode, diag)

    if mode != "fast":
        query = clean_title(title)
        if not query:
            diag.add("warn", "arama", "başlık boş — alternatif sağlayıcılar taranamadı")
        else:
            # Sağlıksız kaynak taranmaz: aggregate_new'de zaten uygulanan süzme burada
            # yoktu, ölü site (RecTV ConnectError) her çözümlemede tur harcıyordu.
            # Sağlık bilinmiyorsa hepsi denenir — kart kaybetme.
            try:
                health = await run_plugin_health()
                dead   = {p["plugin"] for p in health.get("plugins", []) if not p.get("ok")}
            except Exception as hata:
                dead = set()
                diag.add("warn", "sağlık", f"sağlık raporu okunamadı ({type(hata).__name__}) — tümü denenecek")
            if dead:
                diag.add("info", "sağlık", f"atlanan sağlıksız kaynak: {', '.join(sorted(dead))}")

            candidates = [
                name for name in ALTERNATIVE_ORDER
                if name in plugin_names and name != selected and name not in dead
            ]
            matches    = await asyncio.gather(
                *(_with_budget(_search_match(name, query, diag), name, "arama", diag) for name in candidates),
                return_exceptions=True,
            )
            for name, match in zip(candidates, matches):
                if isinstance(match, Exception) or not match:
                    continue
                found, found_episodes = await _with_budget(
                    _links_for(name, quote_plus(match), episode, diag), name, "link", diag,
                ) or ([], [])
                sources.extend(found)
                if not episodes and found_episodes:
                    episodes = found_episodes

    if sources:
        diag.add("info", "sonuç", f"{len(sources)} kaynak hazır")
    else:
        diag.add("fail", "sonuç", "hiçbir sağlayıcı oynatılabilir kaynak vermedi")

    return {**api_v1_global_message, "result": {
        "mode"        : mode,
        "count"       : len(sources),
        "sources"     : sources,
        "episodes"    : episodes,
        "diagnostics" : diag.entries,
    }}
