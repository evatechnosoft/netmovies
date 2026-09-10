# NetMovies — telefon kumandası: telefondan seçip televizyonda başlatma ve TV'yi sürme.
#
# Yayınlama/yansıtma DEĞİL: telefon yalnız NE yapılacağını söyler, akışı TV kendi
# çözer ve oynatır (kalite ve kaynak zinciri TV tarafında kalır, telefon pil harcamaz).
#
# Kuyruk bilerek BELLEKTE: tek hane, tek televizyon. Komut okununca silinir; sunucu
# yeniden başlarsa bekleyen komut kaybolur — kalıcı olması istenmez, eski bir komutun
# ertesi gün televizyonu açması istenmeyen davranış olur.
#
# Tek slot yerine kısa kuyruk: D-pad'de art arda basılan tuşlar birbirini ezmemeli.
# Uzun-yoklama (`wait`) ile tuş gecikmesi saniyelerden milisaniyelere iner; `wait`
# vermeyen eski istemciler eski davranışı (anında dön) aynen görür.

import asyncio
import time

from Core import Request
from .    import api_v1_router, api_v1_global_message

# Bekleyen komut bu süreden eskiyse yok sayılır (telefon gönderdi, TV kapalıydı).
_TTL_SECONDS   = 120
_QUEUE_MAX     = 32
# Uzun-yoklamada TV'nin bağlantıyı ne kadar açık tutabileceği tavan (sn).
# İstek middleware'i 30sn'de 504 veriyor (`_istek.py: istek_timeout`) — altında kalmalı.
_MAX_WAIT      = 25
# TV bu süre içinde yokladıysa "bağlı" sayılır (kumandadaki gösterge).
_ONLINE_WINDOW = 15

# Kumandanın gönderebileceği eylemler. Şema burada KAPALI tutulur: telefon TV'ye
# rastgele alan geçiremez, yalnız bilinen bir eylemi tetikler.
_KEYS      = {"UP", "DOWN", "LEFT", "RIGHT", "CENTER", "BACK", "HOME", "MENU"}
_TRANSPORT = {"play_pause", "seek", "volume", "stop"}
_SCREENS   = {"home", "browse", "following", "channels", "admin"}

_queue: asyncio.Queue = asyncio.Queue(maxsize=_QUEUE_MAX)
# TV'nin son yoklama zamanı — kumanda "televizyon açık mı" diye buna bakar.
_last_poll: float = 0.0
# TV'nin "şu an oynayan" bildirimi (birkaç saniyede bir). Bu kadar süredir
# gelmediyse oynatıcı kapanmış sayılır; kumandadaki şerit kaybolur.
_STATE_TTL = 20
_now: dict = {}


def _err(mesaj: str) -> dict:
    return {**api_v1_global_message, "result": {"ok": False, "error": mesaj}}


def build_command(veri: dict) -> dict | str:
    """Ham isteği doğrulanmış komuta çevirir. Hata varsa metin döner."""
    tur = str(veri.get("type") or "play").strip().lower()

    if tur == "play":
        plugin = str(veri.get("plugin") or "").strip()
        url    = str(veri.get("url") or "").strip()
        if not plugin or not url:
            return "plugin ve url gerekli"
        return {
            "type"   : "play",
            "plugin" : plugin,
            "url"    : url,
            "title"  : str(veri.get("title") or ""),
            "poster" : str(veri.get("poster") or ""),
        }

    if tur == "key":
        key = str(veri.get("key") or "").strip().upper()
        if key not in _KEYS:
            return f"gecersiz key: {key or '(bos)'}"
        return {"type": "key", "key": key}

    if tur == "transport":
        action = str(veri.get("action") or "").strip().lower()
        if action not in _TRANSPORT:
            return f"gecersiz action: {action or '(bos)'}"
        try:
            deger = float(veri.get("value") or 0)
        except (TypeError, ValueError):
            return "value sayi olmali"
        return {"type": "transport", "action": action, "value": deger}

    if tur == "text":
        # Alan adı bilerek `value` DEĞİL: transport'un `value`'su sayısaldır ve
        # istemci tarafında (Kotlin) aynı ada iki tip sığmaz.
        return {
            "type"   : "text",
            "text"   : str(veri.get("text") or "")[:200],
            "submit" : str(veri.get("submit") or "").lower() in ("1", "true", "evet"),
        }

    if tur == "nav":
        screen = str(veri.get("screen") or "").strip().lower()
        if screen not in _SCREENS:
            return f"gecersiz screen: {screen or '(bos)'}"
        return {"type": "nav", "screen": screen}

    return f"gecersiz type: {tur}"


def enqueue(command: dict) -> None:
    """Komutu kuyruğa koyar. Kuyruk dolduysa EN ESKİSİ atılır — kumandada
    son basılan tuş, birikmiş eski tuşlardan daha değerlidir."""
    command["sent_at"] = int(time.time())
    while True:
        try:
            _queue.put_nowait(command)
            return
        except asyncio.QueueFull:
            try:
                _queue.get_nowait()
            except asyncio.QueueEmpty:
                return


def _next_fresh() -> dict | None:
    """Kuyruktan TTL'i geçmemiş ilk komutu verir, bayatları atar."""
    simdi = int(time.time())
    while True:
        try:
            cmd = _queue.get_nowait()
        except asyncio.QueueEmpty:
            return None
        if simdi - cmd.get("sent_at", 0) <= _TTL_SECONDS:
            return cmd


@api_v1_router.post("/remote/play")
async def remote_play(request: Request):
    """Telefon/web çağırır: bu içeriği televizyonda başlat.

    Eski istemciler (v0.1.56 ve öncesi APK, web içerik sayfası) bu ucu kullanır;
    `type` göndermezler ve `play` varsayılır."""
    veri = request.state.veri or {}
    cmd  = build_command({**veri, "type": "play"})
    if isinstance(cmd, str):
        return _err(cmd)

    enqueue(cmd)
    return {**api_v1_global_message, "result": {"ok": True}}


@api_v1_router.post("/remote/command")
async def remote_command(request: Request):
    """Kumanda çağırır: tuş, oynatma kontrolü, metin veya ekran değişimi."""
    veri = request.state.veri or {}
    cmd  = build_command(veri)
    if isinstance(cmd, str):
        return _err(cmd)

    enqueue(cmd)
    return {**api_v1_global_message, "result": {"ok": True}}


@api_v1_router.get("/remote/poll")
async def remote_poll(request: Request):
    """Televizyon çağırır: bekleyen komutu alır ve kuyruktan düşürür.

    `wait` verilirse komut gelene kadar (en çok o kadar saniye) bağlantı açık
    tutulur — D-pad'in kumanda gibi hissettirmesi buna bağlı."""
    global _last_poll
    _last_poll = time.time()

    try:
        wait = min(max(float((request.state.veri or {}).get("wait") or 0), 0), _MAX_WAIT)
    except (TypeError, ValueError):
        wait = 0

    cmd = _next_fresh()
    if cmd is None and wait:
        try:
            cmd = await asyncio.wait_for(_queue.get(), timeout=wait)
        except asyncio.TimeoutError:
            cmd = None
        else:
            # Bekleme sırasında bayatlamış olabilir (TV bağlantısı asılı kaldıysa).
            if int(time.time()) - cmd.get("sent_at", 0) > _TTL_SECONDS:
                cmd = None

    return {**api_v1_global_message, "result": cmd}


@api_v1_router.post("/remote/state")
async def remote_state(request: Request):
    """Televizyon çağırır: şu an ne oynuyor, neresinde. Kumanda şeridi buradan beslenir."""
    global _now
    veri = request.state.veri or {}
    try:
        position = max(float(veri.get("position") or 0), 0.0)
        duration = max(float(veri.get("duration") or 0), 0.0)
    except (TypeError, ValueError):
        return _err("position/duration sayi olmali")
    _now = {
        "title"    : str(veri.get("title") or "")[:200],
        "poster"   : str(veri.get("poster") or ""),
        "plugin"   : str(veri.get("plugin") or ""),
        "url"      : str(veri.get("url") or ""),
        "position" : position,
        "duration" : duration,
        "playing"  : str(veri.get("playing") or "").lower() in ("1", "true", "evet"),
        "at"       : time.time(),
    }
    return {**api_v1_global_message, "result": {"ok": True}}


def now_playing() -> dict | None:
    """Taze bildirim varsa oynayan içerik, yoksa None."""
    if not _now or time.time() - _now["at"] > _STATE_TTL:
        return None
    return {k: v for k, v in _now.items() if k != "at"}


@api_v1_router.get("/remote/status")
async def remote_status(request: Request):
    """Kumanda çağırır: televizyon yokluyor mu, kuyrukta bekleyen var mı, ne oynuyor."""
    return {**api_v1_global_message, "result": {
        "tv_online"   : bool(_last_poll) and (time.time() - _last_poll) <= _ONLINE_WINDOW,
        "last_poll"   : int(_last_poll) or None,
        "pending"     : _queue.qsize(),
        "now_playing" : now_playing(),
    }}
