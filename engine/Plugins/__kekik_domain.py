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


def discover_main_url(kt_path: str, fallback: str, env_var: str | None = None) -> str:
    """Bir eklentinin güncel ana adresini döndürür.

    kt_path : Kekik-cloudstream içindeki .kt yolu
              (ör. "RecTV/src/main/kotlin/com/keyiflerolsun/RecTV.kt")
    fallback: hiçbir kaynak çalışmazsa kullanılacak son bilinen domain
    env_var : elle sabitlemek için ortam değişkeni adı (ör. "RECTV_URL")
    """
    # 1) Manuel override
    if env_var:
        manual = os.getenv(env_var)
        if manual:
            return manual.rstrip("/")

    # 2) Kekik-cloudstream'den güncel domain
    try:
        req = urllib.request.Request(
            f"{_RAW_BASE}/{kt_path}",
            headers={"User-Agent": "Mozilla/5.0"},
        )
        with urllib.request.urlopen(req, timeout=8) as resp:
            text = resp.read().decode("utf-8", "ignore")
        match = re.search(r'mainUrl\s*=\s*"([^"]+)"', text)
        if match:
            return match.group(1).rstrip("/")
    except Exception:
        pass

    # 3) Gömülü son bilinen domain
    return fallback.rstrip("/")
