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
import math
import hmac
import hashlib
import secrets
import sqlite3
import threading
import contextvars
import unicodedata
from pathlib import Path

_LOCK = threading.Lock()

# /data kalıcı volume'u varsa oraya, yoksa proje köküne yaz (admin_config ile aynı mantık).
_DEFAULT_PATH = "/data/netmovies.db" if Path("/data").is_dir() else "netmovies.db"
_DB_PATH      = Path(os.getenv("WATCH_DB_PATH", _DEFAULT_PATH))

# Tek bağlantı, çok thread: check_same_thread=False + _LOCK ile tüm yazma/okuma serileştirilir.
# (Uvicorn tek worker + async; ağır eşzamanlılık yok, global kilit güvenli ve basit.)
_CONN: sqlite3.Connection | None = None

# İsteğin profili; middleware X-NM-Cihaz başlığından çözer. Başlıksız istek (web,
# eski TV sürümü) Dean'dir — profillerden önceki tüm geçmiş de onundur.
CURRENT_PROFILE: contextvars.ContextVar[str] = contextvars.ContextVar("nm_profile", default="dean")


def _p() -> str:
    return CURRENT_PROFILE.get()


# --------------------------------------------------------------------------- Şema
_SCHEMA = """
CREATE TABLE IF NOT EXISTS watch_history (
    profile          TEXT NOT NULL DEFAULT 'dean',
    content_key      TEXT NOT NULL,
    plugin           TEXT,
    title            TEXT,
    poster           TEXT,
    media_type       TEXT,
    episode          TEXT,
    position_seconds REAL,
    duration_seconds REAL,
    updated_at       INTEGER,
    content_url      TEXT,
    PRIMARY KEY (profile, content_key)
);

CREATE TABLE IF NOT EXISTS favorites (
    profile     TEXT NOT NULL DEFAULT 'dean',
    content_key TEXT NOT NULL,
    plugin      TEXT,
    title       TEXT,
    poster      TEXT,
    media_type  TEXT,
    added_at    INTEGER,
    content_url TEXT,
    PRIMARY KEY (profile, content_key)
);

CREATE TABLE IF NOT EXISTS user_lists (
    profile     TEXT NOT NULL DEFAULT 'dean',
    content_key TEXT NOT NULL,
    list_name   TEXT NOT NULL,
    plugin      TEXT,
    title       TEXT,
    poster      TEXT,
    media_type  TEXT,
    added_at    INTEGER,
    content_url TEXT,
    PRIMARY KEY (profile, content_key, list_name)
);

CREATE TABLE IF NOT EXISTS profiles (
    id       TEXT PRIMARY KEY,
    name     TEXT NOT NULL,
    kid      INTEGER NOT NULL DEFAULT 0,
    pin_hash TEXT,
    sort     INTEGER NOT NULL DEFAULT 0
);
INSERT OR IGNORE INTO profiles (id, name, kid, sort) VALUES
    ('dean', 'Dean', 0, 1), ('eva', 'Eva', 1, 2),
    ('profil3', 'Profil 3', 0, 3), ('profil4', 'Profil 4', 0, 4);

CREATE TABLE IF NOT EXISTS devices (
    device_id TEXT PRIMARY KEY,
    profile   TEXT,
    label     TEXT,
    last_seen INTEGER
);
"""

# Profil öncesi şemada tablo sütunları profile'sız; index yeniden kuruluştan SONRA gelir.
_INDEXES = """
CREATE INDEX IF NOT EXISTS idx_watch_profile_updated ON watch_history(profile, updated_at DESC);
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
        for table in ("watch_history", "favorites", "user_lists"):
            columns = {row[1] for row in conn.execute(f"PRAGMA table_info({table})")}
            if "content_url" not in columns:
                conn.execute(f"ALTER TABLE {table} ADD COLUMN content_url TEXT")
        _migrate_type_suffix(conn)
        _migrate_profiles(conn)
        conn.executescript(_INDEXES)
        conn.commit()
        _CONN = conn
    return _CONN


def _migrate_type_suffix(conn: sqlite3.Connection) -> None:
    """Tür son ekli eski anahtarları sadeleştirir; çakışan çift kayıtları birleştirir.

    Aynı içerik iki anahtarla kaydedilmişse (media_type gönderilen/gönderilmeyen
    istemci) EN SON güncellenen kayıt kazanır — kullanıcının gerçekten kaldığı yer.
    """
    for table, order in (("watch_history", "updated_at"), ("favorites", "added_at"), ("user_lists", "added_at")):
        rows = conn.execute(f"SELECT rowid, content_key FROM {table}").fetchall()
        renames = [(r["rowid"], r["content_key"], _TYPE_SUFFIX.sub("", r["content_key"]))
                   for r in rows if _TYPE_SUFFIX.search(r["content_key"] or "")]
        if not renames:
            continue
        for rowid, old_key, new_key in renames:
            if table == "user_lists":
                # (content_key, list_name) bileşik anahtar — çakışan satır varsa eskisini at.
                conn.execute(
                    "DELETE FROM user_lists WHERE content_key = ? AND list_name IN "
                    "(SELECT list_name FROM user_lists WHERE rowid = ?)",
                    (new_key, rowid),
                )
                conn.execute("UPDATE user_lists SET content_key = ? WHERE rowid = ?", (new_key, rowid))
                continue

            rival = conn.execute(
                f"SELECT rowid, {order} FROM {table} WHERE content_key = ?", (new_key,)
            ).fetchone()
            if rival is None:
                conn.execute(f"UPDATE {table} SET content_key = ? WHERE rowid = ?", (new_key, rowid))
                continue

            mine = conn.execute(f"SELECT {order} FROM {table} WHERE rowid = ?", (rowid,)).fetchone()
            if (mine[0] or 0) > (rival[order] or 0):
                conn.execute(f"DELETE FROM {table} WHERE rowid = ?", (rival["rowid"],))
                conn.execute(f"UPDATE {table} SET content_key = ? WHERE rowid = ?", (new_key, rowid))
            else:
                conn.execute(f"DELETE FROM {table} WHERE rowid = ?", (rowid,))


def _migrate_profiles(conn: sqlite3.Connection) -> None:
    """Profil öncesi tabloları (profile, ...) birincil anahtarıyla yeniden kurar.

    SQLite birincil anahtarı ALTER ile değiştiremez: eski tablo kenara alınır, yeni
    şema kurulur, satırlar 'dean' profiline kopyalanır. Mevcut geçmiş Dean'indir.
    """
    pending = [t for t in ("watch_history", "favorites", "user_lists")
               if "profile" not in {row[1] for row in conn.execute(f"PRAGMA table_info({t})")}]
    if not pending:
        return
    # Tek işlem: yarıda kesilirse eski tablolar olduğu gibi kalır, sonraki açılış yeniden dener.
    conn.commit()
    conn.execute("BEGIN")
    try:
        conn.execute("DROP INDEX IF EXISTS idx_watch_updated")
        for table in pending:
            cols = ", ".join(row[1] for row in conn.execute(f"PRAGMA table_info({table})"))
            conn.execute(f"ALTER TABLE {table} RENAME TO {table}_old")
            conn.execute(next(st for st in _SCHEMA.split(";") if f"EXISTS {table} (" in st))
            conn.execute(f"INSERT INTO {table} (profile, {cols}) SELECT 'dean', {cols} FROM {table}_old")
            conn.execute(f"DROP TABLE {table}_old")
        conn.execute("COMMIT")
    except Exception:
        conn.execute("ROLLBACK")
        raise


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

    # media_type BİLEREK anahtarın dışında: istemcilerin biri onu gönderip diğeri
    # göndermeyince aynı film iki ayrı kayıt oluyordu ("gorge" ve "gorge|movie" →
    # Devam Et rafında iki poster). Tür bilgisi tabloda sütun olarak zaten duruyor.
    parts = [core]
    if year:
        parts.append(str(year))

    return "|".join(p for p in parts if p)


# Eski anahtarlardaki tür son eki ("...|movie", "...|serie") kırpılır: istemci hazır
# content_key gönderdiğinde de tek kayda denk gelsin.
_TYPE_SUFFIX = re.compile(r"\|(movie|serie|series|tv|live)$")


def canonical_key(content_key: str) -> str:
    return _TYPE_SUFFIX.sub("", str(content_key or "").strip())


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
    # Canlı yayında `video.duration` Infinity: oynatıcı bunu olduğu gibi yolluyordu,
    # SQLite `inf` kabul ediyor ve `continue_watching` JSON'a çevirirken 500 veriyordu
    # ("Out of range float values are not JSON compliant") — Devam Et rafı komple ölü.
    # Sonlu olmayan değer sıfıra düşer: canlı yayının kaldığı yer zaten anlamsız.
    if not math.isfinite(float(duration_seconds or 0.0)):
        duration_seconds = 0.0
    if not math.isfinite(float(position_seconds or 0.0)):
        position_seconds = 0.0
    ts = _now(now)
    with _LOCK:
        conn = _connect()
        conn.execute(
            """
            INSERT INTO watch_history
                (profile, content_key, plugin, title, poster, media_type, episode, content_url,
                 position_seconds, duration_seconds, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(profile, content_key) DO UPDATE SET
                plugin           = excluded.plugin,
                title            = excluded.title,
                poster           = excluded.poster,
                -- Dizi hiç filme düşmez: tek YouTube bölümü (bölüm listesi yok)
                -- "movie" yazıp Daha 17'yi Devam Et → Film rafına taşıyordu (10 Eki).
                media_type       = CASE WHEN watch_history.media_type = 'serie'
                                        THEN 'serie' ELSE excluded.media_type END,
                episode          = excluded.episode,
                content_url      = excluded.content_url,
                position_seconds = excluded.position_seconds,
                duration_seconds = excluded.duration_seconds,
                updated_at       = excluded.updated_at
            """,
            (_p(), content_key, plugin, title, poster, media_type, episode, content_url,
             float(position_seconds or 0.0), float(duration_seconds or 0.0), ts),
        )
        conn.commit()


def get_progress(content_key: str) -> dict | None:
    """Tek bir içeriğin ilerleme kaydını döndürür; yoksa None."""
    with _LOCK:
        conn = _connect()
        row = conn.execute(
            "SELECT * FROM watch_history WHERE profile = ? AND content_key = ?", (_p(), content_key)
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
            WHERE profile = ?
              AND (duration_seconds <= 0 OR position_seconds < duration_seconds * 0.92)
            ORDER BY updated_at DESC
            LIMIT ?
            """,
            (_p(), lim),
        ).fetchall()
    return [dict(r) for r in rows]


def list_watched(limit: int = 20) -> list[dict]:
    """İzlediklerim: continue_watching'in tersi — süresi bilinen ve %92'yi geçmiş kayıtlar."""
    lim = max(1, int(limit or 1))
    with _LOCK:
        conn = _connect()
        rows = conn.execute(
            """
            SELECT * FROM watch_history
            WHERE profile = ?
              AND duration_seconds > 0
              AND position_seconds >= duration_seconds * 0.92
            ORDER BY updated_at DESC
            LIMIT ?
            """,
            (_p(), lim),
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
            INSERT INTO favorites (profile, content_key, plugin, title, poster, media_type, content_url, added_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(profile, content_key) DO UPDATE SET
                plugin      = excluded.plugin,
                title       = excluded.title,
                poster      = excluded.poster,
                media_type  = excluded.media_type,
                -- Bos gelen URL kayitli olani EZMESIN (web url gondermiyor).
                content_url = CASE WHEN excluded.content_url <> '' THEN excluded.content_url
                                   ELSE favorites.content_url END
            """,
            (_p(), content_key, plugin, title, poster, media_type, content_url, ts),
        )
        conn.commit()


def remove_progress(content_keys: list[str]) -> int:
    """İzleme kayıtlarını siler (Devam Et temizliği). Silinen satır sayısını döner."""
    keys = [k for k in content_keys if k]
    if not keys:
        return 0
    with _LOCK:
        conn = _connect()
        cur = conn.executemany("DELETE FROM watch_history WHERE profile = ? AND content_key = ?",
                               [(_p(), k) for k in keys])
        conn.commit()
    return cur.rowcount


def remove_favorite(content_key: str) -> None:
    """Favoriyi kaldırır."""
    with _LOCK:
        conn = _connect()
        conn.execute("DELETE FROM favorites WHERE profile = ? AND content_key = ?", (_p(), content_key))
        conn.commit()


def is_favorite(content_key: str) -> bool:
    """İçerik favoride mi?"""
    with _LOCK:
        conn = _connect()
        row = conn.execute(
            "SELECT 1 FROM favorites WHERE profile = ? AND content_key = ? LIMIT 1", (_p(), content_key)
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
            "SELECT * FROM favorites WHERE profile = ? ORDER BY added_at DESC", (_p(),)
        ).fetchall()
    return [dict(r) for r in rows]


# ------------------------------------------------------------------------ lists
# kayit_takip: "devamı insin" dizileri — otomatik kayıt turu yalnız bu listeyi tarar.
ALLOWED_LISTS = ("izlenecek", "planlandi", "takip", "kayit_takip")


def toggle_user_list(
    content_key: str,
    list_name: str,
    *,
    plugin: str = "",
    title: str = "",
    poster: str = "",
    media_type: str = "",
    content_url: str = "",
    now: int | None = None,
) -> bool:
    """İçeriği kullanıcı listesine ekler/çıkarır; dönüş yeni durumdur."""
    if list_name not in ALLOWED_LISTS:
        raise ValueError("Geçersiz liste")
    ts = _now(now)
    with _LOCK:
        conn = _connect()
        exists = conn.execute(
            "SELECT 1 FROM user_lists WHERE profile = ? AND content_key = ? AND list_name = ?",
            (_p(), content_key, list_name),
        ).fetchone() is not None
        if exists:
            conn.execute(
                "DELETE FROM user_lists WHERE profile = ? AND content_key = ? AND list_name = ?",
                (_p(), content_key, list_name),
            )
        else:
            conn.execute(
                """
                INSERT INTO user_lists
                    (profile, content_key, list_name, plugin, title, poster, media_type, content_url, added_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (_p(), content_key, list_name, plugin, title, poster, media_type, content_url, ts),
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
            "SELECT * FROM user_lists WHERE profile = ? AND list_name = ? ORDER BY added_at DESC LIMIT ?",
            (_p(), list_name, lim),
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


# ------------------------------------------------------------------- profiles
MAX_PIN_FAILS = 5
_PIN_LOCK_SECONDS = 300
# shortcut: deneme sayacı bellekte, restart'ta sıfırlanır; çok sunuculu kurulumda DB'ye taşı.
_PIN_FAILS: dict[str, tuple[int, float]] = {}


def _hash_pin(pin: str, salt: str | None = None) -> str:
    salt = salt or secrets.token_hex(8)
    digest = hashlib.pbkdf2_hmac("sha256", pin.encode(), salt.encode(), 100_000).hex()
    return f"{salt}${digest}"


def _pin_ok(pin: str, stored: str) -> bool:
    salt, _, _ = stored.partition("$")
    return hmac.compare_digest(_hash_pin(pin, salt), stored)


def list_profiles() -> list[dict]:
    with _LOCK:
        rows = _connect().execute(
            "SELECT id, name, kid, pin_hash FROM profiles ORDER BY sort, id"
        ).fetchall()
    return [
        {"id": r["id"], "name": r["name"], "kid": bool(r["kid"]), "has_pin": bool(r["pin_hash"])}
        for r in rows
    ]


def set_profile(profile: str, name: str | None = None, pin: str | None = None) -> bool:
    """Ad ve/veya PIN günceller. pin="" PIN'i kaldırır. Profil yoksa False."""
    if pin and not (pin.isdigit() and 4 <= len(pin) <= 6):
        raise ValueError("PIN 4-6 haneli rakam olmalı")
    with _LOCK:
        conn = _connect()
        if not conn.execute("SELECT 1 FROM profiles WHERE id = ?", (profile,)).fetchone():
            return False
        if name is not None and name.strip():
            conn.execute("UPDATE profiles SET name = ? WHERE id = ?", (name.strip()[:40], profile))
        if pin is not None:
            conn.execute("UPDATE profiles SET pin_hash = ? WHERE id = ?",
                         (_hash_pin(pin) if pin else None, profile))
        conn.commit()
    return True


def device_profile(device_id: str, label: str = "") -> str | None:
    """Cihazın atanmış profili; ilk görüldüğünde kaydeder.

    Yeni cihaz, admin'de `model:<label>` kaydı varsa o profili alır (Mi Box güncellenince
    Dean'de kalsın); yoksa atanmamış başlar ve kendi geçmişini tutar.
    """
    device_id = device_id.strip()[:64]
    label = label.strip()[:60]
    if not device_id:
        return None
    with _LOCK:
        conn = _connect()
        if label and not conn.execute("SELECT 1 FROM devices WHERE device_id = ?", (device_id,)).fetchone():
            tpl = conn.execute("SELECT profile FROM devices WHERE device_id = ?", (f"model:{label}",)).fetchone()
            if tpl and tpl["profile"]:
                conn.execute(
                    "INSERT INTO devices (device_id, profile, label, last_seen) VALUES (?, ?, ?, ?)",
                    (device_id, tpl["profile"], label, _now()),
                )
        conn.execute(
            """
            INSERT INTO devices (device_id, profile, label, last_seen) VALUES (?, NULL, ?, ?)
            ON CONFLICT(device_id) DO UPDATE SET
                last_seen = excluded.last_seen,
                label = CASE WHEN excluded.label <> '' THEN excluded.label ELSE devices.label END
            """,
            (device_id, label, _now()),
        )
        conn.commit()
        row = conn.execute("SELECT profile FROM devices WHERE device_id = ?", (device_id,)).fetchone()
    return row["profile"] if row else None


def profile_for_device(device_id: str, label: str = "") -> str:
    """Atanmış profil; atanmamış cihaz kendi geçmişini tutar (sonra admin'den profile bağlanır)."""
    return device_profile(device_id, label) or f"cihaz:{device_id.strip()[:64]}"


def list_devices() -> list[dict]:
    with _LOCK:
        rows = _connect().execute("SELECT * FROM devices ORDER BY last_seen DESC").fetchall()
    return [dict(r) for r in rows]


def assign_device(device_id: str, profile: str | None) -> bool:
    """Cihazı profile atar; None atamayı kaldırır (cihaz kendi geçmişine döner)."""
    with _LOCK:
        conn = _connect()
        if profile is not None and not conn.execute(
            "SELECT 1 FROM profiles WHERE id = ?", (profile,)
        ).fetchone():
            return False
        cur = conn.execute("UPDATE devices SET profile = ? WHERE device_id = ?", (profile, device_id))
        if not cur.rowcount and profile is not None:
            conn.execute(
                "INSERT INTO devices (device_id, profile, label, last_seen) VALUES (?, ?, '', ?)",
                (device_id, profile, _now()),
            )
        conn.commit()
    return True


def claim_by_pin(device_id: str, pin: str) -> tuple[str, str | None]:
    """Yeni cihazın ilk açılış PIN'i: PIN'i tutan profili bulup cihazı ona atar.

    ("ok", profil) | ("bad_pin", None) | ("locked", None). Profil sorulmaz, PIN yeter
    (Dean: telefon/tablet ilk açılışta "beni bulsun").
    """
    # Sayaç tek anahtarda: hangi profili denediği bilinmeyen tahmin hepsine birden işler.
    fails, until = _PIN_FAILS.get("*", (0, 0.0))
    if until > time.time():
        return "locked", None
    pin = (pin or "").strip()
    with _LOCK:
        rows = _connect().execute(
            "SELECT id, pin_hash FROM profiles WHERE pin_hash IS NOT NULL ORDER BY sort, id"
        ).fetchall()
    profile = next((r["id"] for r in rows if pin and _pin_ok(pin, r["pin_hash"])), None)
    if not profile:
        fails += 1
        _PIN_FAILS["*"] = (0, time.time() + _PIN_LOCK_SECONDS) if fails >= MAX_PIN_FAILS else (fails, 0.0)
        return "bad_pin", None
    _PIN_FAILS.pop("*", None)
    assign_device(device_id, profile)
    return "ok", profile


def select_profile(device_id: str, profile: str, pin: str, fallback_pin: str = "") -> str:
    """Cihazdan profil seçimi: "ok" | "bad_pin" | "locked" | "unknown".

    Çocuk profili PIN'siz açılabilir. PIN'i ayarlanmamış yetişkin profil fallback_pin
    (site PIN'i) ister; o da yoksa açılmaz.
    """
    with _LOCK:
        row = _connect().execute(
            "SELECT kid, pin_hash FROM profiles WHERE id = ?", (profile,)
        ).fetchone()
    if not row:
        return "unknown"
    # Sayaç profile bağlı: cihaz kimliği başlıktan gelir, değiştirilerek kilit atlatılmasın.
    fails, until = _PIN_FAILS.get(profile, (0, 0.0))
    if until > time.time():
        return "locked"
    pin = (pin or "").strip()
    if row["pin_hash"]:
        ok = _pin_ok(pin, row["pin_hash"])
    elif row["kid"]:
        ok = True
    else:
        ok = bool(fallback_pin) and hmac.compare_digest(pin.encode(), fallback_pin.encode())
    if not ok:
        fails += 1
        _PIN_FAILS[profile] = (0, time.time() + _PIN_LOCK_SECONDS) if fails >= MAX_PIN_FAILS else (fails, 0.0)
        return "bad_pin"
    _PIN_FAILS.pop(profile, None)
    assign_device(device_id, profile)
    return "ok"
