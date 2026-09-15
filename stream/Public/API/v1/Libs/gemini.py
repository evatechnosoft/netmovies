# NetMovies — Gemini'ye tek kapı.
#
# Anahtar yalnız SUNUCUDA durur: önce yönetim paneli (admin.json), yoksa .env.
# Koda gömülü değildir, istemciye hiç gitmez. Sesli kumanda (voice.py) ve arama
# hakemi (resolve_sources) aynı kapıdan geçer — iki yerde ayrı istemci, ayrı
# hata yönetimi tutmanın anlamı yok.

from __future__ import annotations

import json
import os

import httpx

ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
VARSAYILAN_MODEL = "gemini-3.5-flash-lite"


def ayar(ad: str, env_ad: str, varsayilan: str = "") -> str:
    """Panel önce gelir ki anahtar değişince kap yeniden başlatılmasın."""
    try:
        from Public.Home.Libs import admin_config
        deger = str(admin_config.load_config().get(ad) or "").strip()
    except Exception:
        deger = ""
    return deger or os.getenv(env_ad, "").strip() or varsayilan


def anahtar_var() -> bool:
    return bool(ayar("gemini_api_key", "GEMINI_API_KEY"))


def json_ayikla(payload: dict) -> dict | None:
    """Yanıttan JSON nesnesini çıkarır. Model ```json çitiyle sarabiliyor —
    ilk { ... son } arası kesilir."""
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
