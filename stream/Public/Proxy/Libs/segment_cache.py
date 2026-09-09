# Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

from time import time
import asyncio
import hashlib
import os
from pathlib import Path

class SegmentCache:
    """
    LRU cache - HLS video segmentleri için
    - 32MB boyut limiti
    - En az kullanılan (LRU) segment'ler silinir
    - 5 dakika hard TTL
    """

    def __init__(self, max_size_mb: int = 32, hard_ttl_seconds: int = 300, max_item_mb: int = 5, disk_size_mb: int = 2048, disk_dir: str = "/data/cache/segments"):
        self.max_size_bytes   = max_size_mb * 1024 * 1024
        self.max_item_bytes   = max_item_mb * 1024 * 1024  # tekil segment limiti (4K için büyük)
        self.hard_ttl_seconds = hard_ttl_seconds
        self.disk_size_bytes  = disk_size_mb * 1024 * 1024
        self.disk_dir         = Path(disk_dir)
        try:
            self.disk_dir.mkdir(parents=True, exist_ok=True)
        except OSError:
            self.disk_dir = None

        # Cache storage: {url: (content, created_at, last_access, size)}
        self._cache      : dict[str, tuple[bytes, float, float, int]] = {}
        self._total_size                                              = 0
        self._lock                                                    = asyncio.Lock()
        # Disk yazımları arka planda koşar; referans tutulmazsa GC görevi iptal eder.
        self._disk_tasks : set[asyncio.Task] = set()
        # Budama sayacı: dizin taraması pahalı (9p üzerinde binlerce stat), her
        # yazımda değil arada bir yapılır.
        self._disk_writes = 0

    async def get(self, url: str) -> bytes | None:
        """Cache'den segment al ve access time'ı güncelle"""
        async with self._lock:
            if url not in self._cache:
                disk_content = await asyncio.to_thread(self._read_disk, url)
                if disk_content is None:
                    return None
                await self._set_memory(url, disk_content)
                return disk_content

            content, created_at, _, size = self._cache[url]

            # Hard TTL kontrolü
            if time() - created_at > self.hard_ttl_seconds:
                del self._cache[url]
                self._total_size -= size
                return None

            # Last access time'ı güncelle (LRU için)
            self._cache[url] = (content, created_at, time(), size)
            return content

    async def set(self, url: str, content: bytes):
        """Segment'i cache'e ekle"""
        content_size = len(content)

        # Max item size ve max cache size kontrolü
        if content_size > self.max_item_bytes or content_size > self.max_size_bytes:
            return

        async with self._lock:
            # Eğer bu URL zaten cache'deyse, önce eski boyutunu çıkar
            if url in self._cache:
                _, _, _, old_size = self._cache[url]
                self._total_size -= old_size

            # Yeni içeriği ekle (content, created_at, last_access, size)
            current_time     = time()
            self._cache[url] = (content, current_time, current_time, content_size)
            self._total_size += content_size

            # LRU eviction - boyut limiti aşıldıysa en az kullanılanları sil
            await self._evict_if_needed()

        # Disk yazımı YANIT YOLUNDA BEKLENMEZ: /data Windows'a 9p ile bağlı ve
        # yavaş; segment başına ~17 sn eklenip oynatma "3 sn oynar 5 sn donar"
        # hâline geliyordu. Segment zaten bellekte, disk yalnız yeniden izleme için.
        gorev = asyncio.create_task(asyncio.to_thread(self._write_disk, url, content))
        self._disk_tasks.add(gorev)
        gorev.add_done_callback(self._disk_tasks.discard)

    _PRUNE_EVERY = 200   # kaç yazımda bir disk budaması yapılır

    def _path_for(self, url: str) -> Path:
        digest = hashlib.sha256(url.encode("utf-8")).hexdigest()
        return self.disk_dir / digest

    def _read_disk(self, url: str) -> bytes | None:
        if self.disk_dir is None or not self.disk_dir.exists():
            return None
        path = self._path_for(url)
        try:
            if time() - path.stat().st_mtime > self.hard_ttl_seconds:
                path.unlink(missing_ok=True)
                return None
            return path.read_bytes()
        except OSError:
            return None

    def _write_disk(self, url: str, content: bytes):
        if self.disk_dir is None or len(content) > self.max_item_bytes:
            return
        path = self._path_for(url)
        temp = path.with_suffix(".tmp")
        try:
            temp.write_bytes(content)
            temp.replace(path)
        except OSError:
            temp.unlink(missing_ok=True)
            return

        # Budama her yazımda yapılırsa dizindeki her dosya için stat gerekir; 2600
        # dosyalık cache'te bu tek başına ~17 saniye sürüyordu. Arada bir yeterli:
        # limit aşımı en fazla _PRUNE_EVERY segment kadar sarkar.
        self._disk_writes += 1
        if self._disk_writes % self._PRUNE_EVERY:
            return
        try:
            # scandir: mtime ve size tek dizin girişinden okunur (iterdir + stat değil).
            files = sorted(
                ((entry.path, entry.stat()) for entry in os.scandir(self.disk_dir) if entry.is_file()),
                key=lambda item: item[1].st_mtime,
            )
            total = sum(stat.st_size for _, stat in files)
            for dosya, stat in files:
                if total <= self.disk_size_bytes:
                    break
                Path(dosya).unlink(missing_ok=True)
                total -= stat.st_size
        except OSError:
            pass

    async def _set_memory(self, url: str, content: bytes):
        if len(content) > self.max_item_bytes:
            return
        current_time = time()
        self._cache[url] = (content, current_time, current_time, len(content))
        self._total_size += len(content)
        await self._evict_if_needed()

    async def _evict_if_needed(self):
        """Gerekirse en az kullanılan ve süresi dolmuş itemları sil"""
        current_time = time()

        # Hard TTL dolmuş itemları temizle
        expired_urls = [
            url for url, (_, created_at, _, _) in self._cache.items()
            if current_time - created_at > self.hard_ttl_seconds
        ]
        for url in expired_urls:
            _, _, _, size = self._cache[url]
            del self._cache[url]
            self._total_size -= size

        # Hala limit aşılmışsa, en az kullanılan (LRU) itemları sil
        while self._total_size > self.max_size_bytes:
            if not self._cache:
                break

            lru_url = min(self._cache.items(), key=lambda x: x[1][2])[0]
            _, _, _, size = self._cache[lru_url]
            del self._cache[lru_url]
            self._total_size -= size

    def get_stats(self) -> dict:
        """Cache istatistikleri"""
        return {
            "total_items"      : len(self._cache),
            "total_size_mb"    : round(self._total_size / (1024 * 1024), 2),
            "max_size_mb"      : round(self.max_size_bytes / (1024 * 1024), 2),
            "hard_ttl_minutes" : self.hard_ttl_seconds // 60,
        }

# Global cache instance — 4K/yüksek bitrate için env ile büyütülebilir.
#   SEGMENT_CACHE_MB : toplam cache boyutu (varsayılan 256MB)
#   SEGMENT_ITEM_MB  : tekil segment limiti (varsayılan 20MB — 4K segmentleri sığar)
#   SEGMENT_TTL_SEC  : cache ömrü (varsayılan 600sn)
import os as _os

segment_cache = SegmentCache(
    max_size_mb      = int(_os.getenv("SEGMENT_CACHE_MB", "256")),
    hard_ttl_seconds = int(_os.getenv("SEGMENT_TTL_SEC", "600")),
    max_item_mb      = int(_os.getenv("SEGMENT_ITEM_MB", "20")),
    disk_size_mb     = int(_os.getenv("SEGMENT_DISK_CACHE_MB", "2048")),
    disk_dir         = _os.getenv("SEGMENT_DISK_CACHE_DIR", "/data/cache/segments"),
)
