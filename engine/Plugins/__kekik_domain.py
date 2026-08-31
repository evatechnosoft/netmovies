# NetMovies — Çok Katmanlı Akıllı Domain Keşfi & İmza Doğrulayıcı
# Kaynak siteler domain değiştirdiğinde (ör. b.prectv38.sbs, dizipal950, dizilla):
# 1) Ortam değişkeni override'ı (RECTV_URL vb.)
# 2) Mevcut/gömülü adres canlılık kontrolü
# 3) GitHub Upstream (keyiflerolsun/Kekik-cloudstream) .kt mainUrl ayrıştırma
# 4) Numaralı örüntü tarama (Sequential Pattern Probe) + HTML İmza Doğrulaması
# 5) Telegram Web (t.me/s/...) duyuru kanalı regex yakalama

import os
import re
import urllib.request
import urllib.error

_RAW_BASE = "https://raw.githubusercontent.com/keyiflerolsun/Kekik-cloudstream/master"


def _is_alive(url: str, signature: str | None = None, headers: dict | None = None) -> bool:
    """Domain gerçekten cevap veriyor mu ve sahte/boş yönlendirme değil mi?"""
    if not url or not url.startswith(("http://", "https://")):
        return False

    req_headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"}
    if headers:
        req_headers.update(headers)

    for method in ("GET", "HEAD"):
        try:
            req = urllib.request.Request(url, headers=req_headers, method=method)
            with urllib.request.urlopen(req, timeout=2.5) as resp:
                status = getattr(resp, "status", 200)
                if status >= 500:
                    continue
                if signature and method == "GET":
                    body = resp.read(8192).decode("utf-8", "ignore")
                    if signature.lower() not in body.lower():
                        return False
                return True
        except urllib.error.HTTPError as exc:
            if exc.code in (403, 451, 404, 410):
                return False
            return exc.code < 500
        except Exception:
            continue
    return False


def probe_pattern(template: str, start: int, end: int, signature: str | None = None, headers: dict | None = None) -> str | None:
    """Numaralı domain örüntülerini (ör. b.prectv{N}.sbs veya dizipal{N}.org) hızlıca tarar."""
    req_headers = {"User-Agent": "okhttp/4.12.0"} if "prectv" in template else {"User-Agent": "Mozilla/5.0"}
    if headers:
        req_headers.update(headers)

    for i in range(start, end + 1):
        candidate = template.format(n=i)
        if _is_alive(candidate, signature=signature, headers=req_headers):
            return candidate
    return None


def probe_telegram(channel: str, regex_pattern: str | None = None) -> str | None:
    """Telegram public web kanalından (t.me/s/...) en son paylaşılan linki çeker."""
    try:
        req = urllib.request.Request(
            f"https://t.me/s/{channel}",
            headers={"User-Agent": "Mozilla/5.0"},
        )
        with urllib.request.urlopen(req, timeout=3.0) as resp:
            html = resp.read().decode("utf-8", "ignore")
        
        pattern = regex_pattern or r'https?://[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}'
        matches = re.findall(pattern, html)
        # Telegram kendi linklerini filtrele
        filtered = [m for m in matches if not any(x in m for x in ("t.me", "telegram.org", "w3.org"))]
        if filtered:
            return filtered[-1].rstrip("/")
    except Exception:
        pass
    return None


def discover_main_url(
    kt_path: str,
    fallback: str,
    env_var: str | None = None,
    pattern: str | None = None,
    pattern_range: tuple[int, int] = (35, 65),
    signature: str | None = None,
    telegram_channel: str | None = None,
) -> str:
    """Bir eklentinin güncel ana adresini çok katmanlı olarak keşfeder."""
    # 1) Manuel override
    if env_var:
        manual = os.getenv(env_var)
        if manual:
            return manual.rstrip("/")

    fallback = fallback.rstrip("/")
    if os.getenv("AUTO_DISCOVER_DOMAINS", "0").lower() not in ("1", "true", "yes"):
        return fallback

    # 2) Gömülü adres hâlâ canlıysa devam
    if _is_alive(fallback, signature=signature):
        return fallback

    # 3) GitHub Upstream'den güncel domaini al
    discovered = None
    try:
        req = urllib.request.Request(
            f"{_RAW_BASE}/{kt_path}",
            headers={"User-Agent": "Mozilla/5.0"},
        )
        with urllib.request.urlopen(req, timeout=3) as resp:
            text = resp.read().decode("utf-8", "ignore")
        match = re.search(r'mainUrl\s*=\s*"([^"]+)"', text)
        if match:
            discovered = match.group(1).rstrip("/")
    except Exception:
        pass

    if discovered and _is_alive(discovered, signature=signature):
        return discovered

    # 4) Numaralı örüntü taraması (Pattern probe)
    if pattern:
        p_res = probe_pattern(pattern, pattern_range[0], pattern_range[1], signature=signature)
        if p_res:
            return p_res

    # 5) Telegram kanalı taraması
    if telegram_channel:
        t_res = probe_telegram(telegram_channel)
        if t_res and _is_alive(t_res, signature=signature):
            return t_res

    # 6) Hiçbiri doğrulanamadı — en iyi tahmin
    return discovered or fallback
