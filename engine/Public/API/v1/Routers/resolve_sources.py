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
from ..Libs.arama_varyant import baslik_uyusuyor, query_variants
from ..Libs.bolum_esle import basliktan_bolum, bolum_sirasi, int_or_none
from ..Libs.kisa_klip import kisa_klip_mi
from .plugin_health import run_plugin_health

from urllib.parse import quote_plus

# Sıra ve bütçe Libs'te: router Core'u tetiklediği için oradan test edilemiyordu.
from ..Libs.tarama_sirasi import ALTERNATIVE_ORDER, ALTERNATIVE_TIMEOUT, tarama_sirasi, tum_saglayicilar

class Diagnostics:
    """İstemciye de dönen teşhis kaydı — 'neden açılmadı' sorusu artık cevaplanabilir."""

    def __init__(self) -> None:
        self.entries: list[dict[str, str]] = []

    def add(self, level: str, stage: str, message: str) -> None:
        self.entries.append({"level": level, "stage": stage, "message": message})
        mark = {"info": "[green]•[/]", "warn": "[yellow]![/]", "fail": "[red]x[/]"}.get(level, "•")
        konsol.log(f"{mark} resolve: {stage} — {message}")


async def _links_for(plugin_name: str, content_url: str, episode_index: int, diag: Diagnostics, episode_no: int | None = None, season_no: int | None = None) -> tuple[list[dict], list[dict]]:
    """Bir sağlayıcıdan link listesi (ve varsa bölüm listesi) çıkarır.

    `content_url` DÜZ url'dir (kodlanmış değil): eklentiler httpx'e doğrudan verir.
    """
    plugin   = plugin_manager.select_plugin(plugin_name)
    episodes : list[dict] = []
    target   = content_url

    async def _load(url: str) -> list:
        try:
            return await plugin.load_links(url) or []
        except Exception as hata:
            diag.add("fail", "link", f"{plugin_name} · {type(hata).__name__}: {hata}")
            return []

    async def _episode_objects() -> list:
        try:
            info = await plugin.load_item(target)
            return getattr(info, "episodes", None) or []
        except Exception as hata:
            diag.add("fail", "bölüm", f"{plugin_name} · {type(hata).__name__}: {hata}")
            return []

    # Kullanıcı BELİRLİ bir bölüm seçtiyse listeyi ÖNCE çöz. Kart bir bölüm
    # sayfası olabiliyor (DiziMom "Son Bölümler" böyle veriyor); o sayfa tek
    # başına oynatılabilir olduğu için aşağıdaki ilk deneme tutuyor ve seçilen
    # bölüm hiç dikkate alınmıyordu — hangi bölüme basılsa karttaki (son) bölüm
    # açılıyordu. index 0 "seçim yok"tur: fazladan istek atılmaz, eski yol işler.
    if episode_index > 0 or episode_no is not None:
        secilenler = await _episode_objects()
        if secilenler:
            episodes = [
                {
                    "title"  : getattr(ep, "title", None),
                    "url"    : quote_plus(getattr(ep, "url", "") or ""),
                    "season" : getattr(ep, "season", None),
                    "episode": getattr(ep, "episode", None),
                }
                for ep in secilenler
            ]
            # Sağlayıcılar aynı diziyi FARKLI kapsamda veriyor: DDizi bölümleri
            # sayfaladığı için listesi 3. bölümden başlarken DiziMom 1'den
            # başlıyordu. Sıra numarasıyla eşleştirmek alternatif sağlayıcıda
            # BAŞKA bölümü açar — kullanıcı 3. bölüme basıp 5. bölümü izler.
            # Önce gerçek bölüm numarası aranır, bulunamazsa sıraya düşülür.
            sira = bolum_sirasi(secilenler, episode_index, episode_no, season_no)
            if sira is not None:
                target = getattr(secilenler[sira], "url", "") or target
                etiket = f" · bölüm no {episode_no}" if episode_no is not None else ""
                diag.add("info", "bölüm", f"{plugin_name} · seçilen bölüm {sira + 1}/{len(secilenler)}{etiket}")

    links = await _load(target)

    if not links:
        # Dizi ana sayfası olabilir: bölüm listesini çöz, seçili bölümü dene.
        episode_objects = await _episode_objects()

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
            # Burada da numara sıraya yeğdir (yukarıdaki gerekçe).
            sira = bolum_sirasi(episode_objects, episode_index, episode_no, season_no)
            if sira is None:
                sira = 0
            chosen = episode_objects[sira]
            links  = await _load(getattr(chosen, "url", "") or "")

    klipler = [l for l in links if kisa_klip_mi(l.url)]
    if klipler:
        # Kaldırılan bölümün yerine konan ~55 sn'lik klip (googlevideo `dur=`):
        # TV'de "kesik kesik" oynayıp başa dönüyordu (Teşkilat 186, DiziMom).
        diag.add("warn", "link", f"{plugin_name} · {len(klipler)} kısa klip elendi")
        links = [l for l in links if l not in klipler]

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


async def _search_match(plugin_name: str, queries: list[str], diag: Diagnostics) -> str | None:
    """Alternatif sağlayıcıda aynı başlığı arar, en olası eşleşmenin URL'ini döner.

    `queries` giderek kısalan varyant listesidir; ilki sonuç vermezse sıradaki
    denenir (bkz. `query_variants`).
    """
    try:
        plugin = plugin_manager.select_plugin(plugin_name)
    except Exception as hata:
        diag.add("fail", "arama", f"{plugin_name} · {type(hata).__name__}: {hata}")
        return None

    # Doğrulama her zaman ASIL başlığa karşı yapılır: varyant yalnız aramayı
    # genişletir, eşleşme kararını gevşetmez. Eşleşmeyen sonuçta "ilkini al"
    # düşüşü yok — yanlış film açmaktansa o sağlayıcı atlanır.
    asil = queries[0]

    for query in queries:
        try:
            results = await plugin.search(query) or []
        except Exception as hata:
            diag.add("fail", "arama", f"{plugin_name} · '{query}' · {type(hata).__name__}: {hata}")
            continue

        chosen = next((r for r in results if baslik_uyusuyor(asil, getattr(r, "title", ""))), None)
        if not chosen:
            continue

        diag.add("info", "arama", f"{plugin_name} · '{query}' → eşleşti: {getattr(chosen, 'title', '?')}")
        return getattr(chosen, "url", None)

    diag.add("warn", "arama", f"{plugin_name} · sonuç yok ({len(queries)} varyant denendi)")
    return None


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

    # İstemci bölümün GERÇEK numarasını da gönderir: seçili sağlayıcı açılmazsa
    # (liste boş döner) alternatiflerde sıra değil numara aranabilsin.
    secili_no  = int_or_none(istek.get("episode_no"))
    secili_sez = int_or_none(istek.get("season_no"))
    # Bölüm sayfası kartı ("Haysiyet 3.Bölüm"): ek aramadan atılır, numara korunur.
    title, baslik_sez, baslik_no = basliktan_bolum(title)
    if secili_no is None and baslik_no is not None:
        secili_no, secili_sez = baslik_no, baslik_sez

    diag = Diagnostics()
    diag.add("info", "oturum", f"{title or '?'} · seçili sağlayıcı: {selected} · mod: {mode}")

    # Seçili sağlayıcıda ilk bölüm için liste ÖNCEDEN çözülmez (hızlı açılış):
    # istemci zaten bölümün kendi adresini gönderiyor.
    sources, episodes = await _links_for(
        selected, content, episode, diag,
        secili_no if episode > 0 else None, secili_sez if episode > 0 else None,
    )
    episodes_plugin   = selected if episodes else None

    # Numara verilmediyse seçili sağlayıcının listesinden okunur.
    if secili_no is None and episodes and 0 <= episode < len(episodes):
        ham = episodes[episode].get("episode")
        secili_no  = ham if isinstance(ham, int) else None
        ham_sez    = episodes[episode].get("season")
        secili_sez = ham_sez if isinstance(ham_sez, int) else None

    if mode != "fast":
        queries = query_variants(title)
        if not queries:
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

            sira = tum_saglayicilar(istek.get("order"), plugin_names)
            if tarama_sirasi(istek.get("order")) is not ALTERNATIVE_ORDER:
                diag.add("info", "sıra", f"puanlı sıra uygulandı: {', '.join(sira[:5])}…")

            candidates = [
                name for name in sira
                if name in plugin_names and name != selected and name not in dead
            ]
            diag.add("info", "kapsam", f"taranacak sağlayıcı ({len(candidates)}): {', '.join(candidates)}")
            matches    = await asyncio.gather(
                *(_with_budget(_search_match(name, queries, diag), name, "arama", diag) for name in candidates),
                return_exceptions=True,
            )
            # `match` zaten düz URL; `_links_for` de düz URL bekliyor (seçili
            # sağlayıcı yolunda `encoded_url` çözülmüş halde geliyor). Burada
            # yeniden kodlanınca eklentiye "https%3A%2F%2F…" gidiyordu ve
            # httpx "Request URL is missing an 'http://' … protocol" diyordu —
            # alternatif sağlayıcıların hiçbiri kaynak veremiyordu.
            #
            # Link çekme de paralel: seri döngüde 9 sağlayıcı × bütçe, istemcinin
            # 60sn'lik süresini aşıyordu — sondaki kaynaklar hiç denenmeden
            # "kaynak bulunamadı" dönüyordu.
            bulunanlar = [(n, m) for n, m in zip(candidates, matches) if m and not isinstance(m, Exception)]
            toplanan   = await asyncio.gather(
                *(_with_budget(_links_for(n, m, episode, diag, secili_no, secili_sez), n, "link", diag) for n, m in bulunanlar),
                return_exceptions=True,
            )
            for (ad, _), sonuc in zip(bulunanlar, toplanan):
                if isinstance(sonuc, Exception) or not sonuc:
                    continue
                found, found_episodes = sonuc
                sources.extend(found)
                if not episodes and found_episodes:
                    episodes = found_episodes
                    # Bölüm adresleri BU sağlayıcıya ait: istemci bölüm seçince
                    # adresi bu adla göndermeli (DiziMom adıyla DDizi adresi açılmaz).
                    episodes_plugin = ad

    if sources:
        diag.add("info", "sonuç", f"{len(sources)} kaynak hazır")
    else:
        diag.add("fail", "sonuç", "hiçbir sağlayıcı oynatılabilir kaynak vermedi")

    return {**api_v1_global_message, "result": {
        "mode"        : mode,
        "count"       : len(sources),
        "sources"     : sources,
        "episodes"    : episodes,
        "episodes_plugin": episodes_plugin,
        "diagnostics" : diag.entries,
    }}
