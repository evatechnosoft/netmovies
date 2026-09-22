# Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

from CLI import konsol
import asyncio, subprocess, json, time

# Çözüm önbelleği: aynı videoyu arka arkaya açmak (kaldığın yerden devam, geri-ileri)
# her seferinde yeni bir yt-dlp süreci başlatıyordu. YouTube adresleri ~6 saat
# geçerli; yarısından kısa bir ömür güvenli tarafta kalır.
_CACHE     : dict = {}
_CACHE_TTL = 1800

# YouTube çözümü bir gün bozulduğunda ilk şüpheli yt-dlp'nin yaşıdır: kaynak
# imzasını değiştirdiğinde eski sürüm çözemez. Hata anında günde en fazla bir kez
# güncellenir — zamanlanmış görev yok, maliyeti yalnız zaten başarısız olmuş çağrıda.
_SON_GUNCELLEME = 0.0


async def _guncelle() -> bool:
    global _SON_GUNCELLEME
    if time.time() - _SON_GUNCELLEME < 86400:
        return False
    _SON_GUNCELLEME = time.time()
    try:
        process = await asyncio.create_subprocess_exec(
            "python3", "-m", "pip", "install", "--no-cache-dir", "-U", "yt-dlp",
            stdout = subprocess.PIPE,
            stderr = subprocess.PIPE,
        )
        await asyncio.wait_for(process.communicate(), timeout=120.0)
        basarili = process.returncode == 0
        konsol.log(f"[yellow]↻ yt-dlp güncelleme:[/] {'başarılı' if basarili else 'başarısız'}")
        return basarili
    except Exception as hata:
        konsol.log(f"[red]yt-dlp güncelleme hatası:[/] {hata}")
        return False

async def ytdlp_extract_video_info(url: str):
    """
    yt-dlp ile video bilgisi çıkar.

    Desteklenen site kontrolünü yt-dlp'nin kendisi yapar: tanımadığı adreste
    hata döner ve burada None'a çevrilir.

    Args:
        url: Video URL'si

    Returns:
        {
            "title": str,
            "stream_url": str,
            "duration": float,
            "thumbnail": str,
            "format": str  # "hls" | "mp4" | "webm"
        }
    """
    if not url.startswith(("http://", "https://")):
        return None

    kayit = _CACHE.get(url)
    if kayit and time.time() - kayit[0] < _CACHE_TTL:
        return kayit[1]

    # URL uygunsa tam bilgiyi çıkar
    bilgi = await _extract_with_ytdlp(url)
    if bilgi:
        _CACHE[url] = (time.time(), bilgi)
    return bilgi

async def _extract_with_ytdlp(url: str, yeniden: bool = False):
    """yt-dlp ile video bilgisi çıkar (internal)"""
    try:
        cmd = [
            "yt-dlp",
            "--no-warnings",
            "--no-playlist",
            "-j",  # JSON output
            url
        ]

        process = await asyncio.create_subprocess_exec(
            *cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE
        )

        stdout, stderr = await asyncio.wait_for(
            process.communicate(),
            timeout=30.0  # 30 saniye timeout
        )

        if process.returncode != 0:
            error_msg = stderr.decode() if stderr else "Unknown error"
            konsol.log(f"[red]yt-dlp error:[/] {error_msg}")
            if not yeniden and await _guncelle():
                return await _extract_with_ytdlp(url, yeniden=True)
            return None

        # JSON parse
        info = json.loads(stdout.decode().splitlines()[0])

        # Tek dosyalık (progressive) akış YouTube'da 360p ile sınırlı. Aynı videonun
        # HLS master playlist'i ses+video BİRLEŞİK rendition taşıyor — 1080p avc1'den
        # 2160p vp9'a kadar — ve kaliteyi istemci seçiyor. Sunucuda birleştirme yok.
        def _birlesik(f):
            return f.get("vcodec") not in (None, "none") and f.get("acodec") not in (None, "none")

        master = next(
            (
                f["manifest_url"]
                for f in info.get("formats") or []
                if f.get("protocol") == "m3u8_native" and f.get("manifest_url")
            ),
            None,
        )

        # Format belirleme
        ext = info.get("ext", "mp4").lower()
        url_lower = info.get("url", "").lower()

        if master:
            video_format = "hls"
        elif "m3u8" in url_lower or info.get("protocol") == "m3u8_native":
            video_format = "hls"
        elif ext in ["mp4", "webm", "mkv", "avi", "mov", "flv", "wmv"]:
            video_format = ext
        else:
            video_format = "mp4"

        return {
            "title"        : info.get("title", "Video"),
            # Sıra: HLS master → yt-dlp'nin seçtiği tek dosya → listedeki en iyi
            # birleşik akış. Son ikisi YouTube'da 360p, HLS olmayan sitelerde tam kalite.
            "stream_url"   : master or info.get("url") or next(
                (f.get("url") for f in reversed(info.get("formats") or []) if _birlesik(f) and f.get("url")),
                None,
            ),
            "duration"     : info.get("duration", 0),
            "thumbnail"    : info.get("thumbnail"),
            "format"       : video_format,
            "uploader"     : info.get("uploader", ""),
            "description"  : info.get("description", "")[:200] if info.get("description") else "",
            "http_headers" : {k.lower(): v for k, v in info.get("http_headers", {}).items()}
        }

    except asyncio.TimeoutError:
        konsol.log(f"[red]yt-dlp timeout:[/] {url}")
        return None
    except json.JSONDecodeError as e:
        konsol.log(f"[red]yt-dlp JSON parse error:[/] {e}")
        return None
    except FileNotFoundError:
        konsol.log("[red]yt-dlp not found![/] Please install: pip install yt-dlp")
        return None
    except Exception as e:
        konsol.log(f"[red]yt-dlp exception:[/] {e}")
        return None


async def ytdlp_search(query: str, limit: int = 20):
    """YouTube'da arar ve kart listesi döndürür.

    `--flat-playlist` ile tek istek: her sonuç için ayrı çözümleme yapılmaz,
    adres oynatma anında `ytdlp_extract_video_info` ile çözülür.
    """
    try:
        process = await asyncio.create_subprocess_exec(
            "yt-dlp", "--no-warnings", "--flat-playlist", "-J", f"ytsearch{int(limit)}:{query}",
            stdout = subprocess.PIPE,
            stderr = subprocess.PIPE,
        )
        stdout, stderr = await asyncio.wait_for(process.communicate(), timeout=30.0)
        if process.returncode != 0:
            konsol.log(f"[red]yt-dlp search error:[/] {stderr.decode()[:200]}")
            return []

        entries = json.loads(stdout.decode().splitlines()[0]).get("entries") or []
        sonuclar = []
        for e in entries:
            kimlik = e.get("id")
            if not kimlik:
                continue
            sonuclar.append({
                "id"       : kimlik,
                "title"    : e.get("title") or "Video",
                "url"      : f"https://www.youtube.com/watch?v={kimlik}",
                "poster"   : f"https://i.ytimg.com/vi/{kimlik}/hqdefault.jpg",
                "duration" : e.get("duration") or 0,
                "channel"  : e.get("channel") or e.get("uploader") or "",
            })
        return sonuclar
    except asyncio.TimeoutError:
        konsol.log(f"[red]yt-dlp search timeout:[/] {query}")
        return []
    except Exception as hata:
        konsol.log(f"[red]yt-dlp search exception:[/] {hata}")
        return []
