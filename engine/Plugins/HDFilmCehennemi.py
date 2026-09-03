# NetMovies — HDFilmCehennemi eklentisi
#
# Site 2026 Eylül'ünde `hdfilmcehennemi.now` adresine taşındı ve TEMA DEĞİŞTİ:
# eski özel tema yerine WordPress "oldmovie" (DooPlay türevi) kullanılıyor.
# Eski parser'ın seçicileri (`div.section-content a.poster`, `/search?q=` JSON ucu,
# `div.alternative-links`) yeni sitede hiç eşleşmiyordu → eklenti tamamen boş
# dönüyordu. Bu dosya yeni yapıya göre yazılmıştır.
#
# Oynatma zinciri (dördü de zorunlu, biri atlanırsa link çıkmaz):
#   1. İçerik sayfası      → `videoAjax.nonce` + `data-post-id` + `data-player-name`
#   2. wp-admin/admin-ajax → action=get_video_url  → setplay.shop/player/?t=...
#   3. setplay sayfası     → `SPG.cerceve(id, veri, anahtar)` XOR ile gizlenmiş
#                            iç oynatıcı adresi (fastplay.mom/video/<id>)
#   4. fastplay sayfası    → `window.FSP.stream` (HLS master) + `SPG_A` koruma
#                            parametreleri. Manifest isteği `X-Sp` "oynatıcı kanıtı"
#                            başlığı olmadan 404 döner (sitenin kendi notu böyle
#                            diyor: adresi tekrar oynatmak yetmez, başlık şart).

from __future__ import annotations

import base64
import json
import random
import re

from KekikStream.Core import (
    PluginBase,
    MainPageResult,
    SearchResult,
    MovieInfo,
    SeriesInfo,
    Episode,
    ExtractResult,
    Subtitle,
    HTMLHelper,
)

try:
    from Plugins.__kekik_domain import discover_main_url
except Exception:
    import sys, os as _os
    sys.path.insert(0, _os.path.dirname(__file__))
    from __kekik_domain import discover_main_url

# Upstream (Kekik-cloudstream) hâlâ ölü `.nl` adresini gösteriyor; `.nl` 403,
# `.now` 200 döndüğü için bu aile override edilir. HDFC_URL ile elle sabitlenebilir.
_MAIN_URL = discover_main_url(
    "HDFilmCehennemi/src/main/kotlin/com/keyiflerolsun/HDFilmCehennemi.kt",
    "https://www.hdfilmcehennemi.now",
    "HDFC_URL",
)
if "hdfilmcehennemi" in _MAIN_URL and not _MAIN_URL.endswith(".now"):
    _MAIN_URL = "https://www.hdfilmcehennemi.now"

_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"


def _unxor(data_b64: str, key_b64: str) -> str:
    """setplay'in iç oynatıcı adresini gizlediği XOR şeması (base64 veri ⊕ base64 anahtar)."""
    data = base64.b64decode(data_b64)
    key  = base64.b64decode(key_b64)
    return "".join(chr(data[i] ^ key[i % len(key)]) for i in range(len(data)))


def _base36(value: int) -> str:
    digits = "0123456789abcdefghijklmnopqrstuvwxyz"
    if value == 0:
        return "0"
    out = ""
    while value:
        value, rem = divmod(value, 36)
        out = digits[rem] + out
    return out


def _fnv1a(text: str) -> str:
    h = 2166136261
    for ch in text:
        h ^= ord(ch)
        h = (h * 16777619) & 0xFFFFFFFF
    return format(h, "x")


def _player_proof(sp: str, sp_t: int) -> str:
    """fastplay'in `X-Sp` başlığı: <zaman>.<rastgele36>.<fnv1a(sp|zaman|rastgele)>."""
    rand = _base36(int(2176782336 * random.random()))
    return f"{sp_t}.{rand}.{_fnv1a(f'{sp}|{sp_t}|{rand}')}"


class HDFilmCehennemi(PluginBase):
    name        = "HDFilmCehennemi"
    language    = "tr"
    main_url    = _MAIN_URL
    favicon     = f"https://www.google.com/s2/favicons?domain={_MAIN_URL}&sz=64"
    description = "HDFilmCehennemi — Türkçe dublaj/altyazı film ve dizi kaynağı."

    # Yeni sitenin gerçek bölümleri. Diziler ve bölümler artık ayrı ayrı listeleniyor
    # (eski `/yabancidiziizle-2` yolu 404).
    main_page = {
        f"{main_url}"                       : "Yeni Eklenen Filmler",
        f"{main_url}/dizi/"                 : "Diziler",
        f"{main_url}/bolum/"                : "Son Bölümler",
        f"{main_url}/tur/aksiyon/"          : "Aksiyon",
        f"{main_url}/tur/komedi/"           : "Komedi",
        f"{main_url}/tur/korku/"            : "Korku",
        f"{main_url}/tur/bilim-kurgu/"      : "Bilim Kurgu",
        f"{main_url}/tur/animasyon/"        : "Animasyon",
        f"{main_url}/tur/dram/"             : "Dram",
        f"{main_url}/tur/gerilim/"          : "Gerilim",
    }

    # ------------------------------------------------------------------ Ana sayfa
    async def get_main_page(self, page: int, url: str, category: str) -> list[MainPageResult]:
        target   = url if page <= 1 else f"{url.rstrip('/')}/page/{page}/"
        response = await self.httpx.get(target, headers={"User-Agent": _UA})
        secici   = HTMLHelper(response.text)

        results: list[MainPageResult] = []
        # Tema iki kart biçimi kullanıyor: ana sayfa/arşiv (`article .poster`) ve
        # arama sonucu (`.result-item`). İkisi de aynı yapıya indirgeniyor.
        for node in secici.select("div.items article, div.result-item article"):
            href = node.select_attr("a", "href")
            if not href or "/tur/" in href:
                continue

            title = (
                node.select_text("div.title a")
                or node.select_text("div.data h3 a")
                or node.select_attr("img", "alt")
            )
            if not title:
                continue
            # Kart başlığı yoksa img alt'ına düşülüyor; tema oraya "… Poster" yazıyor.
            title = re.sub(r"\s*Poster$", "", title.strip())

            poster = node.select_attr("img", "data-src") or node.select_attr("img", "src")
            results.append(
                MainPageResult(
                    category = category,
                    title    = title,
                    url      = self.fix_url(href),
                    poster   = self.fix_url(poster) if poster else None,
                )
            )
        return results

    # ------------------------------------------------------------------ Arama
    async def search(self, query: str) -> list[SearchResult]:
        # Eski `/search?q=` JSON ucu 404; WordPress'in kendi arama sayfası kullanılıyor.
        response = await self.httpx.get(
            f"{self.main_url}/?s={query}",
            headers={"User-Agent": _UA},
        )
        secici = HTMLHelper(response.text)

        results: list[SearchResult] = []
        for node in secici.select("div.result-item article"):
            href  = node.select_attr("a", "href")
            title = node.select_text("div.title a") or node.select_attr("img", "alt")
            if not href or not title:
                continue
            title = re.sub(r"\s*Poster$", "", title.strip())

            poster = node.select_attr("img", "data-src") or node.select_attr("img", "src")
            results.append(
                SearchResult(
                    title  = title,
                    url    = self.fix_url(href),
                    poster = self.fix_url(poster) if poster else None,
                )
            )
        return results

    # ------------------------------------------------------------------ Detay
    async def load_item(self, url: str) -> MovieInfo | SeriesInfo:
        response = await self.httpx.get(url, headers={"User-Agent": _UA})
        secici   = HTMLHelper(response.text)

        title = secici.select_text("div.data h1") or secici.og_title or ""
        title = re.sub(r"\s*(izle|Türkçe Dublaj|Türkçe Altyazılı).*$", "", title).strip()

        poster      = secici.og_poster or secici.select_attr("div.poster img", "src")
        description = secici.select_text("div.wp-content p") or secici.og_description
        tags        = secici.select_texts("div.sgeneros a")
        year        = secici.regex_first(r"(\d{4})", secici.select_text("span.date") or "")
        rating      = secici.regex_first(r"([\d.,]+)", secici.select_text("span.dt_rating_vgs") or "")
        actors      = secici.select_texts("div.person div.name a")

        # Dizi mi? Bölüm listesi `/bolum/` linkleriyle geliyor.
        episode_nodes = [
            node for node in secici.select("ul.episodios li")
            if node.select_attr("a", "href")
        ]

        if episode_nodes:
            episodes: list[Episode] = []
            for node in episode_nodes:
                href    = node.select_attr("a", "href")
                ep_name = node.select_text("div.episodiotitle a") or node.select_text("a") or ""
                numer   = node.select_text("div.numerando") or ""
                # Tema numarayı ya `div.numerando` içinde ("1 - 3") ya da bölüm
                # başlığında ("1x3") veriyor; ikisi de aynı şekilde okunur.
                se_ep   = re.findall(r"(\d+)", numer) or re.findall(r"(\d+)\s*x\s*(\d+)", ep_name)
                if se_ep and isinstance(se_ep[0], tuple):
                    se_ep = list(se_ep[0])
                season  = int(se_ep[0]) if len(se_ep) > 0 else 1
                number  = int(se_ep[1]) if len(se_ep) > 1 else None
                # Tema numaralandırmayı "1 - 3" diye veriyor; kumandada okunur hâle getir.
                label   = f"{season}. Sezon {number}. Bölüm" if number else f"{season}. Sezon"
                if ep_name.strip() and not re.fullmatch(r"[\dxX\s-]+", ep_name.strip()):
                    label = f"{label} · {ep_name.strip()}"
                episodes.append(
                    Episode(
                        season  = season,
                        episode = number,
                        title   = label,
                        url     = self.fix_url(href),
                    )
                )
            return SeriesInfo(
                url         = url,
                title       = title,
                poster      = self.fix_url(poster) if poster else None,
                description = description,
                tags        = tags,
                rating      = rating,
                year        = year,
                actors      = actors,
                episodes    = episodes,
            )

        return MovieInfo(
            url         = url,
            title       = title,
            poster      = self.fix_url(poster) if poster else None,
            description = description,
            tags        = tags,
            rating      = rating,
            year        = year,
            actors      = actors,
        )

    # ------------------------------------------------------------------ Linkler
    async def load_links(self, url: str) -> list[ExtractResult]:
        page = await self.httpx.get(url, headers={"User-Agent": _UA})
        html = page.text

        nonce_match = re.search(r"videoAjax\s*=\s*\{[^}]*nonce:\s*'([^']+)'", html)
        post_match  = re.search(r'data-post-id="(\d+)"', html)
        if not nonce_match or not post_match:
            return []

        nonce   = nonce_match.group(1)
        post_id = post_match.group(1)
        players = [
            name for name in dict.fromkeys(re.findall(r'data-player-name="([^"]+)"', html))
            if name and "'" not in name
        ] or ["SetPlay"]

        results: list[ExtractResult] = []
        for player_name in players:
            try:
                extracted = await self._resolve_player(url, nonce, post_id, player_name)
            except Exception:
                continue
            if extracted:
                results.append(extracted)

        return self.deduplicate(results)

    async def _resolve_player(self, page_url: str, nonce: str, post_id: str, player_name: str) -> ExtractResult | None:
        """Tek bir oynatıcı seçeneğini HLS akışına kadar çözer."""
        ajax = await self.httpx.post(
            f"{self.main_url}/wp-admin/admin-ajax.php",
            data    = {
                "action"     : "get_video_url",
                "nonce"      : nonce,
                "post_id"    : post_id,
                "player_name": player_name,
                "part_key"   : "",
            },
            headers = {
                "User-Agent"      : _UA,
                "Referer"         : page_url,
                "X-Requested-With": "XMLHttpRequest",
            },
            timeout = 15.0,
        )
        payload = ajax.json()
        embed   = (payload.get("data") or {}).get("url")
        if not embed:
            return None

        return await self._resolve_embed(embed, player_name)

    async def _resolve_embed(self, embed_url: str, player_name: str) -> ExtractResult | None:
        """setplay → (XOR) → fastplay → HLS master. Zincirin son üç halkası."""
        setplay = await self.httpx.get(
            embed_url,
            headers = {"User-Agent": _UA, "Referer": f"{self.main_url}/"},
            timeout = 15.0,
        )

        frame = re.search(r'SPG\.cerceve\("[^"]+","([^"]+)","([^"]+)"\)', setplay.text)
        if not frame:
            return None

        inner = _unxor(frame.group(1).replace("\\/", "/"), frame.group(2))
        origin_match = re.match(r"https?://[^/]+", inner)
        if not origin_match:
            return None
        origin = origin_match.group(0)

        fast = await self.httpx.get(
            inner,
            headers = {"User-Agent": _UA, "Referer": "https://setplay.shop/"},
            timeout = 15.0,
        )

        stream_match = re.search(r'stream:\s*"([^"]+)"', fast.text)
        if not stream_match:
            return None

        stream = stream_match.group(1)
        stream = stream if stream.startswith("http") else origin + stream

        # Manifest, oynatıcı kanıtı (X-Sp) olmadan 404 döner — VE kanıt TEK
        # KULLANIMLIK: aynısı ikinci istekte yine 404 verir (kanıtlandı). Bu yüzden
        # imzanın kendisi değil MALZEMESİ taşınır; proxy her istekte yeniden üretir
        # (bkz. stream/Public/Proxy/Libs/player_proof.py).
        proof_headers: dict[str, str] = {}
        spg_match = re.search(r"SPG_A=(\{.*?\});", fast.text)
        if spg_match:
            try:
                spg = json.loads(spg_match.group(1))
                if spg.get("sp") and spg.get("spT"):
                    proof_headers["X-Sp-Secret"] = str(spg["sp"])
                    proof_headers["X-Sp-Time"]   = str(int(spg["spT"]))
            except Exception:
                pass

        subtitles: list[Subtitle] = []
        for name, sub_url in re.findall(r'\{\s*"?label"?\s*:\s*"([^"]+)"\s*,\s*"?file"?\s*:\s*"([^"]+)"', fast.text):
            subtitles.append(Subtitle(name=name, url=sub_url if sub_url.startswith("http") else origin + sub_url))

        return ExtractResult(
            name          = f"{self.name} | {player_name}",
            url           = stream,
            referer       = inner,
            user_agent    = _UA,
            subtitles     = subtitles,
            extra_headers = {"Origin": origin, **proof_headers},
        )
