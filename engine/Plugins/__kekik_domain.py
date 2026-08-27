# NetMovies — Otomatik domain keşfi
# Kaynak siteler domain değiştirdiğinde (ör. b.prectv38.sbs -> b.prectv39.sbs)
# "kırmızı" olmayı önlemek için: güncel domaini keyiflerolsun/Kekik-cloudstream
# reposundan (sürekli güncel tutulur) otomatik çeker. Kullanıcı elle uğraşmaz.
#
# Öncelik: 1) env override  2) Kekik-cloudstream'deki güncel mainUrl  3) gömülü fallback
#
# Dosya adı "__" ile başladığı için PluginLoader bunu eklenti sanmaz (atlar).

import os
import re
import urllib.request

_RAW_BASE = "https://raw.githubusercontent.com/keyiflerolsun/Kekik-cloudstream/master"


def _is_alive(url: str) -> bool:
    """Domain gerçekten cevap veriyor mu? (upstream Kotlin bayat domain
    tutabiliyor; ör. DiziYou -> diziyou3.com ölü). 5xx altı = canlı sayılır."""
    for method in ("HEAD", "GET"):
        try:
            req = urllib.request.Request(
                url,
                headers = {"User-Agent": "Mozilla/5.0"},
                method  = method,
            )
            with urllib.request.urlopen(req, timeout=6) as resp:
                return getattr(resp, "status", 200) < 500
        except urllib.error.HTTPError as exc:
            # 403/451/404/410 = bloke veya kayıp: scraper içerik çekemez → ÖLÜ say.
            # (Eskiden "sunucu ayakta = canlı" sayılıyordu; bu, bloke domaini seçip
            #  içeriksiz kart/raf üretiyordu.) Diğer 4xx'ler geçici olabilir → canlı.
            if exc.code in (403, 451, 404, 410):
                return False
            return exc.code < 500
        except Exception:
            continue
    return False


def discover_main_url(kt_path: str, fallback: str, env_var: str | None = None) -> str:
    """Bir eklentinin güncel ana adresini döndürür.

    kt_path : Kekik-cloudstream içindeki .kt yolu
              (ör. "RecTV/src/main/kotlin/com/keyiflerolsun/RecTV.kt")
    fallback: hiçbir kaynak çalışmazsa kullanılacak son bilinen domain
    env_var : elle sabitlemek için ortam değişkeni adı (ör. "RECTV_URL")
    """
    # 1) Manuel override (canlılık kontrolü yapılmaz — kullanıcı bilerek verdi)
    if env_var:
        manual = os.getenv(env_var)
        if manual:
            return manual.rstrip("/")

    # 2) Kekik-cloudstream'den güncel domain
    discovered = None
    try:
        req = urllib.request.Request(
            f"{_RAW_BASE}/{kt_path}",
            headers={"User-Agent": "Mozilla/5.0"},
        )
        with urllib.request.urlopen(req, timeout=8) as resp:
            text = resp.read().decode("utf-8", "ignore")
        match = re.search(r'mainUrl\s*=\s*"([^"]+)"', text)
        if match:
            discovered = match.group(1).rstrip("/")
    except Exception:
        pass

    # 3) Aday adresleri canlılığa göre seç: upstream bayat olabilir, fallback güncel
    #    olabilir (veya tersi). İlk canlı olanı döndür.
    for candidate in (discovered, fallback.rstrip("/")):
        if candidate and _is_alive(candidate):
            return candidate

    # 4) Hiçbiri doğrulanamadı — en iyi tahmin (discovered varsa o, yoksa fallback)
    return discovered or fallback.rstrip("/")
