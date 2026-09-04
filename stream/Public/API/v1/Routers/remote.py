# NetMovies — "TV'de oynat": telefondan seçip televizyonda başlatma.
#
# Yayınlama/yansıtma DEĞİL: telefon yalnız NE oynatılacağını söyler, akışı TV kendi
# çözer ve oynatır (kalite ve kaynak zinciri TV tarafında kalır, telefon pil harcamaz).
#
# Kuyruk bilerek BELLEKTE ve tek slotluk: tek hane, tek televizyon. Komut okununca
# silinir; sunucu yeniden başlarsa bekleyen komut kaybolur — kalıcı olması istenmez,
# eski bir komutun ertesi gün televizyonu açması istenmeyen davranış olur.

import time

from Core import Request
from .    import api_v1_router, api_v1_global_message

# Bekleyen komut bu süreden eskiyse yok sayılır (telefon gönderdi, TV kapalıydı).
_TTL_SECONDS = 120

_pending: dict | None = None


@api_v1_router.post("/remote/play")
async def remote_play(request: Request):
    """Telefon/web çağırır: bu içeriği televizyonda başlat."""
    global _pending
    veri   = request.state.veri or {}
    plugin = str(veri.get("plugin") or "").strip()
    url    = str(veri.get("url") or "").strip()
    if not plugin or not url:
        return {**api_v1_global_message, "result": {"ok": False, "error": "plugin ve url gerekli"}}

    _pending = {
        "plugin" : plugin,
        "url"    : url,
        "title"  : str(veri.get("title") or ""),
        "poster" : str(veri.get("poster") or ""),
        "sent_at": int(time.time()),
    }
    return {**api_v1_global_message, "result": {"ok": True}}


@api_v1_router.get("/remote/poll")
async def remote_poll(request: Request):
    """Televizyon çağırır: bekleyen komut varsa alır ve kuyruğu boşaltır."""
    global _pending
    cmd = _pending
    _pending = None
    if not cmd or int(time.time()) - cmd["sent_at"] > _TTL_SECONDS:
        return {**api_v1_global_message, "result": None}
    return {**api_v1_global_message, "result": cmd}
