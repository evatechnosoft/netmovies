# NetMovies — Merkezi Admin Yapılandırması
# Tek kullanıcı, çok cihaz senaryosu için sunucu tarafında JSON olarak saklanır;
# telefon, Mibox ve PC aynı ayarları görür. Karma mantık:
#   - hidden_providers  : gizlenen kaynaklar (kara liste)
#   - hidden_categories : gizlenen tür/kategoriler (kara liste)
#   - featured          : öne çıkan/izlenecekler (beyaz liste, ana sayfada üstte)
#   - min_rating        : puan eşiği (TMDB puanı olan öğelerde uygulanır)

from __future__ import annotations

import os
import json
import threading
from pathlib import Path

_LOCK = threading.Lock()

# /data kalıcı volume'u varsa oraya, yoksa proje köküne yaz.
_DEFAULT_PATH = "/data/admin.json" if Path("/data").is_dir() else "admin.json"
_CONFIG_PATH  = Path(os.getenv("ADMIN_CONFIG_PATH", _DEFAULT_PATH))

# Kullanıcı "Asya izlemeyeceğim" dedi → varsayılan gizli kategoriler.
DEFAULT_CONFIG: dict = {
    "hidden_providers": [],
    "hidden_categories": [
        "Asya Dizileri", "Asya", "Animeler", "Anime", "Kore Dizileri",
        "Hint Dizileri", "Belgeseller", "Belgesel",
    ],
    "featured": [],      # [{provider, url, title, poster, rating}]
    "min_rating": 0.0,
    # Uzak KekikStreamAPI "geniş katalog" sağlayıcısı (opsiyonel). Boşsa yerel motor
    # kullanılır. Doluysa tüm cihazlar (telefon/Mibox/PC) bu sağlayıcıyı görür —
    # 200+ eklenti + CF/domain bakımı upstream'de. Bedeli: istekler o sunucudan geçer.
    "provider_url": "",
    # CloudStream benzeri özel GitHub / harici eklenti repoları
    "custom_repos": [
        {
            "name": "Kekik-cloudstream (Resmi)",
            "url": "https://raw.githubusercontent.com/keyiflerolsun/Kekik-cloudstream/master/repo.json",
            "enabled": True,
        }
    ],
}


def _normalize(cfg: dict) -> dict:
    out = dict(DEFAULT_CONFIG)
    out.update({k: v for k, v in (cfg or {}).items() if k in DEFAULT_CONFIG})
    out["hidden_providers"]  = list(dict.fromkeys(out.get("hidden_providers") or []))
    out["hidden_categories"] = list(dict.fromkeys(out.get("hidden_categories") or []))
    out["featured"]          = out.get("featured") or []
    out["custom_repos"]      = out.get("custom_repos") or list(DEFAULT_CONFIG["custom_repos"])
    try:
        out["min_rating"] = float(out.get("min_rating") or 0.0)
    except (TypeError, ValueError):
        out["min_rating"] = 0.0
    # provider_url: normalize (strip, protokol ekle). Boş bırakılabilir → yerel motor.
    _pu = str(out.get("provider_url") or "").strip().rstrip("/")
    if _pu and not _pu.startswith(("http://", "https://")):
        _pu = f"https://{_pu}"
    out["provider_url"] = _pu
    return out


def load_config() -> dict:
    with _LOCK:
        if _CONFIG_PATH.exists():
            try:
                return _normalize(json.loads(_CONFIG_PATH.read_text(encoding="utf-8")))
            except Exception:
                pass
        return dict(DEFAULT_CONFIG)


def save_config(cfg: dict) -> dict:
    normalized = _normalize(cfg)
    with _LOCK:
        try:
            _CONFIG_PATH.parent.mkdir(parents=True, exist_ok=True)
            _CONFIG_PATH.write_text(
                json.dumps(normalized, ensure_ascii=False, indent=2), encoding="utf-8"
            )
        except Exception:
            pass
    return normalized


# --------------------------------------------------------------------------- Süzme
def _rating_of(item) -> float | None:
    """Bir öğeden (dict) sayısal puanı çıkarır; yoksa None."""
    raw = item.get("rating") if isinstance(item, dict) else None
    if raw is None:
        return None
    try:
        return float(str(raw).replace(",", ".").split()[0])
    except (ValueError, IndexError):
        return None


def filter_plugins(plugins: list, cfg: dict | None = None) -> list:
    """get_all_plugins çıktısını admin config'e göre süzer.
    Gizli kaynakları çıkarır, her kaynağın main_page'inden gizli kategorileri atar."""
    cfg = cfg or load_config()
    hidden_p = set(cfg["hidden_providers"])
    hidden_c = set(cfg["hidden_categories"])

    result = []
    for plugin in plugins or []:
        if not isinstance(plugin, dict):
            continue
        if plugin.get("name") in hidden_p:
            continue
        main_page = plugin.get("main_page") or {}
        # Kategori değerleri quote_plus'lu gelebilir; ham karşılaştırma için ikisini de dene.
        from urllib.parse import unquote_plus
        filtered_mp = {
            url: cat for url, cat in main_page.items()
            if unquote_plus(str(cat)) not in hidden_c and str(cat) not in hidden_c
        }
        plugin = {**plugin, "main_page": filtered_mp}
        result.append(plugin)
    return result


def filter_items(items: list, cfg: dict | None = None) -> list:
    """İçerik listesini puan eşiğine göre süzer (puanı olmayan öğeler korunur)."""
    cfg = cfg or load_config()
    threshold = cfg["min_rating"]
    if threshold <= 0:
        return items or []
    out = []
    for item in items or []:
        rating = _rating_of(item)
        if rating is None or rating >= threshold:
            out.append(item)
    return out


def filter_aggregate_items(items: list, cfg: dict | None = None) -> list:
    """Birleşik 'Yeni Çıkanlar' öğelerini admin config'e göre süzer:
    gizli kaynak (plugin) + gizli kategori dışlanır, sonra puan eşiği uygulanır.
    Öğe şekli: {plugin, title, url, poster, category}."""
    from urllib.parse import unquote_plus

    cfg      = cfg or load_config()
    hidden_p = set(cfg["hidden_providers"])
    hidden_c = set(cfg["hidden_categories"])

    out = []
    for item in items or []:
        if not isinstance(item, dict):
            continue
        if item.get("plugin") in hidden_p:
            continue
        cat = item.get("category")
        if cat is not None and (unquote_plus(str(cat)) in hidden_c or str(cat) in hidden_c):
            continue
        out.append(item)
    return filter_items(out, cfg)
