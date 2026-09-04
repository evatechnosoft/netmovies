# NetMovies — İzleme Geçmişi / Favoriler / Kaynak İstatistikleri (SQLite)
#
# Tek kullanıcı, çok cihaz senaryosu için sunucu tarafında SQLite'ta saklanır;
# telefon, Mibox ve PC aynı geçmişi/favorileri görür. admin_config.py ile aynı
# persistence deseni: /data kalıcı volume varsa oraya, yoksa proje köküne yaz;
# WATCH_DB_PATH env ile override edilebilir.
#
# KRİTİK TASARIM — content_key SİTE-AGNOSTİK:
#   Kayıt anahtarı plugin (site) İÇERMEZ. Aynı film/dizi başka bir sitede de
#   bulunsa aynı content_key üretilir; böylece "site değişse de kayıtlı" kalır.
#   plugin sadece "en son nerede bulundu" bilgisi olarak saklanır, anahtarın
#   parçası değildir.

from __future__ import annotations

import os
import re
import time
import sqlite3
import threading
import unicodedata
from pathlib import Path

_LOCK = threading.Lock()

# /data kalıcı volume'u varsa oraya, yoksa proje köküne yaz (admin_config ile aynı mantık).
_DEFAULT_PATH = "/data/netmovies.db" if Path("/data").is_dir() else "netmovies.db"
_DB_PATH      = Path(os.getenv("WATCH_DB_PATH", _DEFAULT_PATH))

# Tek bağlantı, çok thread: check_same_thread=False + _LOCK ile tüm yazma/okuma serileştirilir.
# (Uvicorn tek worker + async; ağır eşzamanlılık yok, global kilit güvenli ve basit.)
_CONN: sqlite3.Connection | None = None


# --------------------------------------------------------------------------- Şema
_SCHEMA = """
CREATE TABLE IF NOT EXISTS watch_history (
    content_key      TEXT PRIMARY KEY,
    plugin           TEXT,
    title            TEXT,
    poster           TEXT,
    media_type       TEXT,
    episode          TEXT,
    position_seconds REAL,
    duration_seconds REAL,
    updated_at       INTEGER
);

CREATE TABLE IF NOT EXISTS favorites (
    content_key TEXT PRIMARY KEY,
    plugin      TEXT,
    title       TEXT,
    poster      TEXT,
    media_type  TEXT,
    added_at    INTEGER
);

CREATE TABLE IF NOT EXISTS user_lists (
    content_key TEXT NOT NULL,
    list_name   TEXT NOT NULL,
    plugin      TEXT,
    title       TEXT,
    poster      TEXT,
    media_type  TEXT,
    added_at    INTEGER,
    PRIMARY KEY (content_key, list_name)
);

CREATE INDEX IF NOT EXISTS idx_watch_updated ON watch_history(updated_at DESC);
"""


def _connect() -> sqlite3.Connection:
    """Tekil bağlantıyı (lazy) döndürür; şemayı idempotent oluşturur."""
    global _CONN
    if _CONN is None:
        _DB_PATH.parent.mkdir(parents=True, exist_ok=True)
        conn = sqlite3.connect(str(_DB_PATH), check_same_thread=False)
        conn.row_factory = sqlite3.Row
        conn.executescript(_SCHEMA)
        # content_url sonradan eklendi; iki tabloda da idempotent ALTER ile gelir.
        # Favoride URL olmadan kayit ACILAMIYORDU (istemci icerige gidemiyordu).
        for table in ("watch_history", "favorites"):
            columns = {row[1] for row in conn.execute(f"PRAGMA table_info({table})")}
            if "content_url" not in columns:
                conn.execute(f"ALTER TABLE {table} ADD COLUMN content_url TEXT")
        conn.commit()
        _CONN = conn
    return _CONN


# --------------------------------------------------------------------- content_key
# Türkçe karakter sadeleştirme haritası (unicode NFKD tek başına ı/İ/ş/ğ'yi tam çözmez).
_TR_MAP = str.maketrans({
    "ç": "c", "Ç": "c",
    "ğ": "g", "Ğ": "g",
    "ı": "i", "İ": "i",
    "ö": "o", "Ö": "o",
    "ş": "s", "Ş": "s",
    "ü": "u", "Ü": "u",
})

_YEAR_RE = re.compile(r"\b(19|20)\d{2}\b")


def normalize_key(title: str, media_type: str = "", year: str | int | None = None) -> str:
    """SİTE-AGNOSTİK içerik anahtarı üretir.

    Adımlar: küçült → Türkçe karakter sadeleştir → aksan/diakritik at →
    alfanümerik dışını boşluğa çevir → sık geçen 'izle/hd/türkçe dublaj/altyazılı'
    gibi ekleri temizle → tek boşluğa indir → yıl + media_type ekle.

    Örn: 'İnception (2010) Türkçe Dublaj izle' ve 'inception 2010 HD' → aynı key.

    plugin (site) BİLEREK dışarıda; site değişse de anahtar sabit kalır.
    """
    raw = str(title or "")

    # Başlıkta yıl geçiyorsa ve ayrı yıl verilmediyse, oradan yakala.
    if year is None:
        m = _YEAR_RE.search(raw)
        if m:
            year = m.group(0)

    s = raw.lower().translate(_TR_MAP)
    # Kalan aksanları at (é→e vb.).
    s = "".join(c for c in unicodedata.normalize("NFKD", s) if not unicodedata.combining(c))
    # Yılı çekirdek metinden çıkar; yıl yalnızca ayrı ekte dursun. Aksi halde
    # 'inception 2010' vs yıl-paramlı 'inception' farklı key üretir (site-agnostik bozulur).
    s = _YEAR_RE.sub(" ", s)
    # Alfanümerik olmayanı boşluğa çevir.
    s = re.sub(r"[^a-z0-9]+", " ", s)

    # Site/kalite gürültüsü — anahtarı kirleten sık ekler.
    _NOISE = {
        "izle", "izlesene", "hd", "full", "fullhd", "1080p", "720p", "4k",
        "turkce", "dublaj", "altyazi", "altyazili", "tr", "dublajli",
        "the", "sezon", "bolum", "part",
    }
    tokens = [t for t in s.split() if t and t not in _NOISE]
    core = " ".join(tokens).strip()

    parts = [core]
    if year:
        parts.append(str(year))
    mt = re.sub(r"[^a-z0-9]+", "", str(media_type or "").lower())
    if mt:
        parts.append(mt)

    return "|".join(p for p in parts if p)


def _now(now: int | None = None) -> int:
    """Zaman damgası; çağıran 'now' geçebilir, yoksa time.time() kullanılır."""
    return int(now) if now is not None else int(time.time())


# ------------------------------------------------------------------ watch_history
def upsert_progress(
    content_key: str,
    *,
    plugin: str = "",
    title: str = "",
    poster: str = "",
    media_type: str = "",
    content_url: str = "",
    episode: str = "",
    position_seconds: float = 0.0,
    duration_seconds: float = 0.0,
    now: int | None = None,
) -> None:
    """İzleme ilerlemesini kaydeder/günceller (content_key birincil anahtar)."""
    ts = _now(now)
    with _LOCK:
        conn = _connect()
        conn.execute(
            """
            INSERT INTO watch_history
                (content_key, plugin, title, poster, media_type, episode, content_url,
                 position_seconds, duration_seconds, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(content_key) DO UPDATE SET
                plugin           = excluded.plugin,
                title            = excluded.title,
                poster           = excluded.poster,
                media_type       = excluded.media_type,
                episode          = excluded.episode,
                content_url      = excluded.content_url,
                position_seconds = excluded.position_seconds,
                duration_seconds = excluded.duration_seconds,
                updated_at       = excluded.updated_at
            """,
            (content_key, plugin, title, poster, media_type, episode, content_url,
             float(position_seconds or 0.0), float(duration_seconds or 0.0), ts),
        )
        conn.commit()


def get_progress(content_key: str) -> dict | None:
    """Tek bir içeriğin ilerleme kaydını döndürür; yoksa None."""
    with _LOCK:
        conn = _connect()
        row = conn.execute(
            "SELECT * FROM watch_history WHERE content_key = ?", (content_key,)
        ).fetchone()
    return dict(row) if row else None


def list_continue_watching(limit: int = 20) -> list[dict]:
    """Devam edilecekler: en son güncellenenden başlayarak, tamamlanmamış olanlar.

    'Tamamlanmış' = süre biliniyor ve pozisyon süresinin %92'sini geçmiş.
    Süre bilinmiyorsa (duration<=0) listede tutulur.
    """
    lim = max(1, int(limit or 1))
    with _LOCK:
        conn = _connect()
        rows = conn.execute(
            """
            SELECT * FROM watch_history
            WHERE duration_seconds <= 0
               OR position_seconds < duration_seconds * 0.92
            ORDER BY updated_at DESC
            LIMIT ?
            """,
            (lim,),
        ).fetchall()
    return [dict(r) for r in rows]


# ---------------------------------------------------------------------- favorites
def add_favorite(
    content_key: str,
    *,
    plugin: str = "",
    title: str = "",
    poster: str = "",
    media_type: str = "",
    content_url: str = "",
    now: int | None = None,
) -> None:
    """Favori ekler/günceller (idempotent)."""
    ts = _now(now)
    with _LOCK:
        conn = _connect()
        conn.execute(
            """
            INSERT INTO favorites (content_key, plugin, title, poster, media_type, content_url, added_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(content_key) DO UPDATE SET
                plugin      = excluded.plugin,
                title       = excluded.title,
                poster      = excluded.poster,
                media_type  = excluded.media_type,
                -- Bos gelen URL kayitli olani EZMESIN (web url gondermiyor).
                content_url = CASE WHEN excluded.content_url <> '' THEN excluded.content_url
                                   ELSE favorites.content_url END
            """,
            (content_key, plugin, title, poster, media_type, content_url, ts),
        )
        conn.commit()


def remove_favorite(content_key: str) -> None:
    """Favoriyi kaldırır."""
    with _LOCK:
        conn = _connect()
        conn.execute("DELETE FROM favorites WHERE content_key = ?", (content_key,))
        conn.commit()


def is_favorite(content_key: str) -> bool:
    """İçerik favoride mi?"""
    with _LOCK:
        conn = _connect()
        row = conn.execute(
            "SELECT 1 FROM favorites WHERE content_key = ? LIMIT 1", (content_key,)
        ).fetchone()
    return row is not None


def toggle_favorite(
    content_key: str,
    *,
    plugin: str = "",
    title: str = "",
    poster: str = "",
    media_type: str = "",
    content_url: str = "",
    now: int | None = None,
) -> bool:
    """Favori durumunu değiştirir. Dönüş: yeni durum (True=favoride)."""
    if is_favorite(content_key):
        remove_favorite(content_key)
        return False
    add_favorite(content_key, plugin=plugin, title=title, poster=poster,
                 media_type=media_type, content_url=content_url, now=now)
    return True


def list_favorites() -> list[dict]:
    """Tüm favoriler, en son eklenen üstte."""
    with _LOCK:
        conn = _connect()
        rows = conn.execute(
            "SELECT * FROM favorites ORDER BY added_at DESC"
        ).fetchall()
    return [dict(r) for r in rows]


# ------------------------------------------------------------------------ lists
ALLOWED_LISTS = ("izlenecek", "planlandi", "takip")


def toggle_user_list(
    content_key: str,
    list_name: str,
    *,
    plugin: str = "",
    title: str = "",
    poster: str = "",
    media_type: str = "",
    now: int | None = None,
) -> bool:
    """İçeriği kullanıcı listesine ekler/çıkarır; dönüş yeni durumdur."""
    if list_name not in ALLOWED_LISTS:
        raise ValueError("Geçersiz liste")
    ts = _now(now)
    with _LOCK:
        conn = _connect()
        exists = conn.execute(
            "SELECT 1 FROM user_lists WHERE content_key = ? AND list_name = ?",
            (content_key, list_name),
        ).fetchone() is not None
        if exists:
            conn.execute(
                "DELETE FROM user_lists WHERE content_key = ? AND list_name = ?",
                (content_key, list_name),
            )
        else:
            conn.execute(
                """
                INSERT INTO user_lists
                    (content_key, list_name, plugin, title, poster, media_type, added_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                (content_key, list_name, plugin, title, poster, media_type, ts),
            )
        conn.commit()
    return not exists


def list_user_list(list_name: str, limit: int = 100) -> list[dict]:
    """Belirli kullanıcı listesini son eklenenden başlayarak döndürür."""
    if list_name not in ALLOWED_LISTS:
        raise ValueError("Geçersiz liste")
    lim = max(1, int(limit or 1))
    with _LOCK:
        conn = _connect()
        rows = conn.execute(
            "SELECT * FROM user_lists WHERE list_name = ? ORDER BY added_at DESC LIMIT ?",
            (list_name, lim),
        ).fetchall()
    return [dict(row) for row in rows]


# ------------------------------------------------------------------- source_stats
def get_source_stats() -> list[dict]:
    """Kaynak (plugin) istatistiklerini watch_history'den TÜRETİR.

    Ayrı source_stats tablosu tutulmuyor (tercih: türetim). Neden: item_count'un
    otomatik/tutarlı bakımı ayrı bir yazma yolu gerektirir ve senkron kalması zor;
    izlenme sayısı zaten watch_history'de mevcut, tek doğruluk kaynağı orası.
    Dönüş: [{plugin, watch_count, last_watched_at}], izlenme sayısına göre azalan.
    """
    with _LOCK:
        conn = _connect()
        rows = conn.execute(
            """
            SELECT plugin,
                   COUNT(*)        AS watch_count,
                   MAX(updated_at) AS last_watched_at
            FROM watch_history
            WHERE plugin IS NOT NULL AND plugin <> ''
            GROUP BY plugin
            ORDER BY watch_count DESC, last_watched_at DESC
            """
        ).fetchall()
    return [dict(r) for r in rows]
