# NetMovies — sesli kumanda: Türkçe konuşmayı NİYETE çevirir.
#
# Tarayıcının kendi konuşma tanıması (Web Speech API) yalnız METİN verir; "10 saniye
# geri al" ya da "dün bıraktığım korku filmini aç" gibi cümleler arama kutusuna
# harfiyen yazılınca hiçbir işe yaramıyor. Burada Gemini sesi doğrudan alıp EYLEM
# üretir: arama sorgusu mu, tuş mu, oynatma kontrolü mü.
#
# Anahtar yalnız SUNUCUDA durur — koda gömülü değildir, istemciye hiç gitmez ve
# panele maskeli döner. Yönetim panelinden girilir (Yönetim → Sesli Kumanda);
# .env'deki GEMINI_API_KEY yedek yoldur. Anahtar yoksa uç 503 döner ve kumanda
# sayfası tarayıcının kendi tanımasına düşer (yalnız arama yapar).

import base64
import json
import os

import httpx

from Core     import Request, JSONResponse
from .        import api_v1_router, api_v1_global_message
from .remote  import build_command, enqueue

# Anahtar KODDA DEĞİL: önce yönetim panelinden (admin.json), yoksa .env'den.
# Panel önce gelir ki anahtar değişince kap yeniden başlatılmasın.
def _ayar(ad: str, env_ad: str, varsayilan: str = "") -> str:
    try:
        from Public.Home.Libs import admin_config
        deger = str(admin_config.load_config().get(ad) or "").strip()
    except Exception:
        deger = ""
    return deger or os.getenv(env_ad, "").strip() or varsayilan


_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"

# Ses tavanı: kumandada tek cümle söylenir, 15 sn'lik opus ~120 KB. Tavan bunun
# katbekat üstünde ama sunucuyu megabaytlarca base64'e boğmaya izin vermez.
_MAX_AUDIO_BYTES = 1_500_000

_SISTEM = """Sen bir Türkçe TV kumandasının niyet çözücüsüsün. Kullanıcının sesli
komutunu tek bir JSON nesnesine çevirirsin. Açıklama yazma, yalnız JSON döndür.

Alanlar:
- spoken : duyduğun cümlenin düz metni
- action : "search" | "key" | "transport" | "nav" | "none"
- query  : action=search ise aranacak yapım adı (yalnız ad; "izle", "aç", "filmini"
           gibi kelimeleri at)
- key    : action=key ise UP, DOWN, LEFT, RIGHT, CENTER, BACK, HOME, MENU
- transport: action=transport ise play_pause, seek, volume, stop
- value  : seek için saniye (ileri +, geri -), volume için -1..1 arası değişim
- screen : action=nav ise home, browse, following, channels
- reply  : kullanıcıya gösterilecek çok kısa Türkçe geri bildirim

Örnekler:
"inception aç" -> {"action":"search","query":"Inception","reply":"Inception aranıyor"}
"durdur" -> {"action":"transport","transport":"play_pause","reply":"Duraklatıldı"}
"on saniye geri al" -> {"action":"transport","transport":"seek","value":-10,"reply":"10 sn geri"}
"sesi aç" -> {"action":"transport","transport":"volume","value":0.2,"reply":"Ses artırıldı"}
"geri dön" -> {"action":"key","key":"BACK","reply":"Geri"}
"takip listem" -> {"action":"nav","screen":"following","reply":"Takip listesi"}
Anlamadığın cümlede action="none" ver."""


def _hata(mesaj: str, kod: int = 400) -> JSONResponse:
    return JSONResponse(status_code=kod, content={**api_v1_global_message, "result": {"ok": False, "error": mesaj}})


def _cikti_ayikla(payload: dict) -> dict | None:
    """Gemini yanıtından JSON nesnesini çıkarır. Model yine de ```json çitiyle
    sarabiliyor — o yüzden ilk { ... son } arası kesilir."""
    try:
        metin = payload["candidates"][0]["content"]["parts"][0]["text"]
    except (KeyError, IndexError, TypeError):
        return None

    bas, son = metin.find("{"), metin.rfind("}")
    if bas < 0 or son <= bas:
        return None

    try:
        return json.loads(metin[bas:son + 1])
    except json.JSONDecodeError:
        return None


def _komuta_cevir(niyet: dict) -> dict | None:
    """Niyeti kumanda komutuna çevirir; arama komut değildir (sayfa kendi arar)."""
    action = str(niyet.get("action") or "").lower()

    if action == "key":
        return {"type": "key", "key": niyet.get("key")}
    if action == "transport":
        return {"type": "transport", "action": niyet.get("transport"), "value": niyet.get("value") or 0}
    if action == "nav":
        return {"type": "nav", "screen": niyet.get("screen")}
    return None


@api_v1_router.post("/voice")
async def voice(request: Request):
    """Kumanda çağırır: ses (base64) ya da metin gönderir, niyet geri döner.

    Tuş/oynatma/ekran niyetleri DOĞRUDAN kuyruğa yazılır — kullanıcı "durdur"
    dedikten sonra ayrıca bir düğmeye basmak zorunda kalmasın. Arama niyeti
    yazılmaz: sonucu kullanıcı seçer, TV'de ne açılacağına Gemini karar vermez."""
    api_key = _ayar("gemini_api_key", "GEMINI_API_KEY")
    model   = _ayar("gemini_model", "GEMINI_MODEL", "gemini-3.5-flash-lite")
    if not api_key:
        return _hata("Gemini anahtari yok (Yonetim -> Sesli Kumanda)", 503)

    veri  = request.state.veri or {}
    # Panelde "Dene": anahtarı ve modeli doğrular ama TV'ye komut GÖNDERMEZ.
    deneme = str(veri.get("dry") or "").lower() in ("1", "true", "evet")
    metin = str(veri.get("text") or "").strip()
    ses   = str(veri.get("audio") or "").strip()
    mime  = str(veri.get("mime") or "audio/webm").strip()

    if not metin and not ses:
        return _hata("text veya audio gerekli")

    parts: list[dict] = []
    if ses:
        # data: URL olarak gelmiş olabilir — base64 gövdesini ayıkla.
        if "," in ses[:64] and ses.startswith("data:"):
            basli, ses = ses.split(",", 1)
            mime = basli[5:].split(";")[0] or mime
        try:
            ham = base64.b64decode(ses, validate=True)
        except Exception:
            return _hata("audio gecerli base64 degil")
        if len(ham) > _MAX_AUDIO_BYTES:
            return _hata(f"ses cok buyuk ({len(ham)} bayt, tavan {_MAX_AUDIO_BYTES})")
        parts.append({"inline_data": {"mime_type": mime, "data": ses}})
    if metin:
        parts.append({"text": metin})

    govde = {
        "systemInstruction" : {"parts": [{"text": _SISTEM}]},
        "contents"          : [{"role": "user", "parts": parts}],
        "generationConfig"  : {"responseMimeType": "application/json", "temperature": 0},
    }

    try:
        async with httpx.AsyncClient(timeout=httpx.Timeout(connect=5, read=20, write=20, pool=5)) as client:
            yanit = await client.post(
                _ENDPOINT.format(model=model),
                headers = {"x-goog-api-key": api_key},
                json    = govde,
            )
    except httpx.HTTPError as hata:
        return _hata(f"gemini erisilemedi: {hata}", 502)

    if yanit.status_code != 200:
        # Model adı yanlışsa Google 404 döner; teşhis için modeli de söyle.
        return _hata(f"gemini {yanit.status_code} (model={model}): {yanit.text[:200]}", 502)

    niyet = _cikti_ayikla(yanit.json())
    if niyet is None:
        return _hata("gemini yanitindan JSON cikarilamadi", 502)

    gonderildi = False
    ham_komut  = None if deneme else _komuta_cevir(niyet)
    if ham_komut:
        komut = build_command(ham_komut)
        if isinstance(komut, str):
            niyet["reply"] = f"Anlaşılmadı: {komut}"
        else:
            enqueue(komut)
            gonderildi = True

    return {**api_v1_global_message, "result": {"ok": True, "sent": gonderildi, "model": model, **niyet}}
