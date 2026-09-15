# NetMovies — zincir boşa düştüğünde başlık kurtarma.
#
# NEDEN: sağlayıcı aramaları harfi harfine çalışıyor. "the odyssey" sıfır sonuç
# veriyor, "odyssey" iki sonuç veriyordu (hafıza: arama varyantları). Varyant
# üreticisi kalıp tabanlı — artikel atar, noktalama siler — ama filmin Türkçe
# adını ya da yılını bilemez. Zincirin TAMAMI boş döndüğünde, yani kaybedecek
# bir şey kalmadığında, başlığın başka söyleniş biçimleri sorulur.
#
# Yalnız boş sonuçta çağrılır: normal akışta hiç maliyeti yoktur.

from __future__ import annotations

from CLI import konsol

from . import gemini

_SISTEM = """Sen bir film/dizi başlığı eşleştirme yardımcısısın.
Verilen başlığın Türkiye'deki korsan dizi/film sitelerinde ARAMA KUTUSUNA
yazıldığında sonuç verecek alternatif yazılışlarını üretirsin.

Kurallar:
- En çok 4 alternatif. En olası önce.
- Türkçe yayın adı varsa MUTLAKA ekle (ör. "The Odyssey" → "Odesa Destanı").
- Orijinal ad, kısaltılmış ad (artikelsiz), yılsız ad, yaygın alternatif çeviri.
- Verilen başlığın AYNISINI tekrar etme.
- Uydurma isim yazma: emin değilsen o alternatifi hiç üretme.
- Yalnız JSON döndür."""

_SEMA = {
    "type": "object",
    "properties": {
        "basliklar": {
            "type": "array",
            "items": {"type": "string"},
            "maxItems": 4,
        }
    },
    "required": ["basliklar"],
}

# Kaç alternatif gerçekten denenir. Her deneme tam bir zincir taraması (onlarca
# saniye) — kullanıcı zaten bekliyor, sınırsız deneme onu ekranda unutur.
MAX_DENEME = 2


async def alternatif_basliklar(baslik: str) -> list[str]:
    """Başlığın aranmaya değer başka yazılışları. Anahtar yoksa boş liste."""
    baslik = (baslik or "").strip()
    if not baslik or not gemini.anahtar_var():
        return []

    cikti, hata = await gemini.sor(
        sistem   = _SISTEM,
        parcalar = [{"text": baslik}],
        sema     = _SEMA,
        timeout_sn = 12.0,
    )
    if cikti is None:
        konsol.log(f"[yellow]⚠ başlık kurtarma:[/] {hata}")
        return []

    ham = cikti.get("basliklar")
    if not isinstance(ham, list):
        return []

    sade  = baslik.casefold()
    cikti_liste: list[str] = []
    for aday in ham:
        if not isinstance(aday, str):
            continue
        aday = aday.strip()
        if aday and aday.casefold() != sade and aday not in cikti_liste:
            cikti_liste.append(aday)
    return cikti_liste[:MAX_DENEME]
