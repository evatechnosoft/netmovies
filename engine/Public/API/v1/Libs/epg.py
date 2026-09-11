# NetMovies — Canlı yayın rehberi (EPG): "şu an ne oynuyor".
#
# Kaynak XMLTV (epgshare01, TR paketi): 178 kanal · ~15 bin program · 165 KB gz.
# Kanal listesi (iptv-org + kullanıcının kendi listesi) ile EPG ayrı dünyalar;
# eşleştirme kanal ADI üzerinden yapılır. Türkçe büyük/küçük harf, "HD/FHD/4K"
# ekleri ve noktalama listeden listeye değiştiği için ad sadeleştirilir —
# "SİNEMA TV HD" ile "Sinema TV" aynı kanaldır.
#
# Rehber 6 saatte bir tazelenir ve BELLEKTE tutulur: yalnız "şimdi/sonraki"
# sorulduğu için diske yazmaya değmez, kap yeniden başlarsa taze çekilir.

from __future__ import annotations

import asyncio
import gzip
import re
import time
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta, timezone

import httpx

from CLI import konsol

_KAYNAK = "https://epgshare01.online/epgshare01/epg_ripper_TR1.xml.gz"
_TTL    = 6 * 3600

# Ad sadeleştirme: Türkçe harfler + yalnız gürültü taşıyan ekler.
_HARFLER = str.maketrans("İIıŞşĞğÜüÖöÇç", "iiissgguuoocc")
_EKLER   = re.compile(r"\b(hd|fhd|uhd|4k|sd|tr|turkiye|türkiye|canli|canlı|yayin|yayın|tv)\b")

_rehber: dict[str, list[tuple[datetime, datetime, str, str]]] = {}
_damga  = 0.0
_kilit  = asyncio.Lock()


def sadelestir(ad: str) -> str:
    """Kanal adını eşleştirilebilir hale getirir."""
    metin = str(ad or "").translate(_HARFLER).lower()
    metin = re.sub(r"[^\w\s]", " ", metin)
    metin = _EKLER.sub(" ", metin)
    return " ".join(metin.split())


def _zaman(deger: str | None) -> datetime | None:
    """XMLTV damgası: `20260910060000 +0300`."""
    if not deger:
        return None
    parca = deger.strip().split()
    try:
        an = datetime.strptime(parca[0][:14], "%Y%m%d%H%M%S")
    except ValueError:
        return None

    if len(parca) > 1 and len(parca[1]) == 5:
        try:
            isaret = 1 if parca[1][0] == "+" else -1
            fark   = timedelta(hours=int(parca[1][1:3]), minutes=int(parca[1][3:5])) * isaret
            return an.replace(tzinfo=timezone(fark))
        except ValueError:
            pass
    return an.replace(tzinfo=timezone.utc)


def _ayristir(ham: bytes) -> dict[str, list[tuple[datetime, datetime, str, str]]]:
    kok = ET.fromstring(ham)

    # Kanal kimliği → sadeleştirilmiş ad(lar). Bir kanalın birden çok adı olabiliyor.
    adlar: dict[str, set[str]] = {}
    for kanal in kok.findall("channel"):
        kimlik = kanal.get("id") or ""
        for ad in kanal.findall("display-name"):
            if ad.text:
                adlar.setdefault(kimlik, set()).add(sadelestir(ad.text))

    rehber: dict[str, list[tuple[datetime, datetime, str, str]]] = {}
    for program in kok.findall("programme"):
        bas, son = _zaman(program.get("start")), _zaman(program.get("stop"))
        if not bas or not son:
            continue
        baslik = program.find("title")
        if baslik is None or not baslik.text:
            continue
        ozet = program.find("desc")
        satir = (bas, son, baslik.text.strip(), (ozet.text or "").strip()[:200] if ozet is not None else "")
        for sade in adlar.get(program.get("channel") or "", set()):
            rehber.setdefault(sade, []).append(satir)

    for liste in rehber.values():
        liste.sort(key=lambda s: s[0])
    return rehber


async def tazele(zorla: bool = False) -> int:
    """Rehberi indirir. Zaten tazeyse dokunmaz, kanal sayısını döner."""
    global _rehber, _damga

    async with _kilit:
        if not zorla and _rehber and time.monotonic() - _damga < _TTL:
            return len(_rehber)
        try:
            async with httpx.AsyncClient(timeout=60, follow_redirects=True) as istemci:
                yanit = await istemci.get(_KAYNAK)
            if yanit.status_code != 200:
                konsol.log(f"[yellow]! epg:[/] kaynak {yanit.status_code}")
                return len(_rehber)
            _rehber = _ayristir(gzip.decompress(yanit.content))
            _damga  = time.monotonic()
            konsol.log(f"[green]• epg:[/] {len(_rehber)} kanal · rehber tazelendi")
        except Exception as hata:
            konsol.log(f"[yellow]! epg:[/] tazelenemedi ({type(hata).__name__})")
        return len(_rehber)


def simdi(kanal_adi: str) -> dict[str, str] | None:
    """Kanalın şu anki ve sıradaki programı. Rehberde yoksa None."""
    liste = _rehber.get(sadelestir(kanal_adi))
    if not liste:
        return None

    an = datetime.now(timezone.utc)
    for sira, (bas, son, baslik, ozet) in enumerate(liste):
        if bas <= an < son:
            sonraki = liste[sira + 1][2] if sira + 1 < len(liste) else ""
            return {
                "program" : baslik,
                "ozet"    : ozet,
                "baslangic": bas.isoformat(timespec="minutes"),
                "bitis"   : son.isoformat(timespec="minutes"),
                "sonraki" : sonraki,
            }
    return None
