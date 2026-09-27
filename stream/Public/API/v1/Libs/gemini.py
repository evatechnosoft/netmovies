# NetMovies — Gemini'ye tek kapı.
#
# Anahtar yalnız SUNUCUDA durur: önce yönetim paneli (admin.json), yoksa .env.
# Koda gömülü değildir, istemciye hiç gitmez. Sesli kumanda (voice.py) ve arama
# hakemi (resolve_sources) aynı kapıdan geçer — iki yerde ayrı istemci, ayrı
# hata yönetimi tutmanın anlamı yok.
#
# YZ_GECIT_URL doluysa (ev YZ geçidi, LiteLLM — bkz. infra/yz/) istek oraya
# OpenAI biçiminde `model: "gemini"` ile gider; anahtarı geçit tutar, kota/hata
# olunca yerel modele kendisi düşer. Boşsa Google doğrudan çağrılır.

from __future__ import annotations

import json
import os

import httpx

ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
VARSAYILAN_MODEL = "gemini-3.5-flash-lite"
GECIT_MODEL      = "gemini"


def gecit_url() -> str:
    return os.getenv("YZ_GECIT_URL", "").strip().rstrip("/")


def ayar(ad: str, env_ad: str, varsayilan: str = "") -> str:
    """Panel önce gelir ki anahtar değişince kap yeniden başlatılmasın."""
    try:
        from Public.Home.Libs import admin_config
        deger = str(admin_config.load_config().get(ad) or "").strip()
    except Exception:
        deger = ""
    return deger or os.getenv(env_ad, "").strip() or varsayilan


def anahtar_var() -> bool:
    """Geçit varsa anahtar geçittedir — stream'in kendi anahtarı gerekmez."""
    return bool(gecit_url()) or bool(ayar("gemini_api_key", "GEMINI_API_KEY"))


def json_ayikla(payload: dict) -> dict | None:
    """Gemini yanıtından JSON nesnesini çıkarır."""
    try:
        metin = payload["candidates"][0]["content"]["parts"][0]["text"]
    except (KeyError, IndexError, TypeError):
        return None
    return metinden_json(metin)


def metinden_json(metin: str | None) -> dict | None:
    """Model ```json çitiyle sarabiliyor — ilk { ... son } arası kesilir."""
    if not isinstance(metin, str):
        return None
    bas, son = metin.find("{"), metin.rfind("}")
    if bas < 0 or son <= bas:
        return None
    try:
        cikti = json.loads(metin[bas:son + 1])
    except json.JSONDecodeError:
        return None
    return cikti if isinstance(cikti, dict) else None


async def _gecitten_sor(
    url: str,
    sistem: str,
    metin: str,
    sema: dict | None,
    timeout_sn: float,
) -> tuple[dict | None, str]:
    """OpenAI uyumlu geçide (LiteLLM) sorar. Şema geçide yapısal olarak
    gönderilmez: Gemini şema lehçesi ile yerel yedek model aynı biçimi
    tanımıyor; şema sistem metnine eklenir, JSON metinden ayıklanır."""
    if sema:
        sistem = f"{sistem}\n\nYanıt bu JSON şemasına uysun:\n{json.dumps(sema, ensure_ascii=False)}"
    govde = {
        "model"          : GECIT_MODEL,
        "messages"       : [
            {"role": "system", "content": sistem},
            {"role": "user",   "content": metin},
        ],
        "temperature"    : 0,
        "response_format": {"type": "json_object"},
    }
    try:
        async with httpx.AsyncClient(
            timeout=httpx.Timeout(connect=5, read=timeout_sn, write=timeout_sn, pool=5)
        ) as client:
            yanit = await client.post(f"{url}/chat/completions", json=govde)
    except httpx.HTTPError as hata:
        return None, f"yz gecidi erisilemedi: {hata}"

    if yanit.status_code != 200:
        return None, f"yz gecidi {yanit.status_code}: {yanit.text[:200]}"

    try:
        icerik = yanit.json()["choices"][0]["message"]["content"]
    except (ValueError, KeyError, IndexError, TypeError):
        return None, "yz gecidi yaniti beklenen bicimde degil"

    cikti = metinden_json(icerik)
    return (cikti, "") if cikti is not None else (None, "yz gecidi yanitindan JSON cikarilamadi")


async def sor(
    sistem: str,
    parcalar: list[dict],
    sema: dict | None = None,
    timeout_sn: float = 20.0,
) -> tuple[dict | None, str]:
    """Gemini'ye sorar, JSON nesnesi ve hata metni döndürür.

    Şema verilirse model yapısal çıktı üretir (responseSchema) — metinden JSON
    kazımaya göre çok daha güvenilir.
    """
    url = gecit_url()
    # Geçit yalnız metin taşır; ses/görsel parçası olan istek doğrudan yoldan gider.
    if url and all(set(p) == {"text"} for p in parcalar):
        metin = "\n".join(str(p["text"]) for p in parcalar)
        return await _gecitten_sor(url, sistem, metin, sema, timeout_sn)

    api_key = ayar("gemini_api_key", "GEMINI_API_KEY")
    model   = ayar("gemini_model", "GEMINI_MODEL", VARSAYILAN_MODEL)
    if not api_key:
        return None, "gemini anahtari yok"

    uretim: dict = {"responseMimeType": "application/json", "temperature": 0}
    if sema:
        uretim["responseSchema"] = sema

    govde = {
        "systemInstruction": {"parts": [{"text": sistem}]},
        "contents"         : [{"role": "user", "parts": parcalar}],
        "generationConfig" : uretim,
    }

    try:
        async with httpx.AsyncClient(
            timeout=httpx.Timeout(connect=5, read=timeout_sn, write=timeout_sn, pool=5)
        ) as client:
            yanit = await client.post(
                ENDPOINT.format(model=model),
                headers={"x-goog-api-key": api_key},
                json=govde,
            )
    except httpx.HTTPError as hata:
        return None, f"gemini erisilemedi: {hata}"

    if yanit.status_code != 200:
        # Model adı yanlışsa Google 404 döner; teşhis için modeli de söyle.
        return None, f"gemini {yanit.status_code} (model={model}): {yanit.text[:200]}"

    cikti = json_ayikla(yanit.json())
    return (cikti, "") if cikti is not None else (None, "gemini yanitindan JSON cikarilamadi")
