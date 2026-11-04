# DDizi "Full Tek Parça" bölümleri aslında 1..N parça: parça N sayfası `<bölüm adresi>/N`,
# her biri ayrı twimg HLS master'ı. Saf fonksiyonlar: parça adresi ayrıştırma ve
# parçaları #EXT-X-DISCONTINUITY ile tek medya playlist'ine ekleme.

from __future__ import annotations

import re

from urllib.parse import urljoin, urlparse

_PARCA = re.compile(r'href="([^"]*/izle/\d+/[^"]+\.htm)/(\d+)"')
_NITELIK = re.compile(r'([A-Z0-9-]+)=("[^"]*"|[^,]*)')


def parca_adresleri(html: str, url: str) -> list[str]:
    """Bölümün parça sayfaları sırayla (1. parça = bölüm adresi). Parçasızsa boş liste."""
    yol = urlparse(url).path
    numaralar = sorted({int(n) for href, n in _PARCA.findall(html) if urlparse(href).path == yol})
    if not numaralar or numaralar[0] != 2:
        return []
    return [url] + [f"{url}/{n}" for n in numaralar]


def _nitelikler(satir: str) -> dict[str, str]:
    return {k: v.strip('"') for k, v in _NITELIK.findall(satir.split(":", 1)[1])}


def en_iyi_varyant(master: str, master_url: str) -> tuple[str, str | None, dict[str, str]] | None:
    """Master'dan en yüksek çözünürlüklü varyant: (görüntü adresi, ses adresi | None, STREAM-INF nitelikleri)."""
    satirlar = [s.strip() for s in master.splitlines() if s.strip()]
    varyantlar = []
    for i, s in enumerate(satirlar):
        if s.startswith("#EXT-X-STREAM-INF") and i + 1 < len(satirlar):
            n = _nitelikler(s)
            boyut = n.get("RESOLUTION", "0x0").split("x")
            varyantlar.append((int(boyut[-1] or 0), int(n.get("BANDWIDTH", 0)), n, satirlar[i + 1]))
    if not varyantlar:
        return None
    _, _, n, adres = max(varyantlar, key=lambda v: v[:2])
    ses = next(
        (m["URI"] for m in (_nitelikler(s) for s in satirlar if s.startswith("#EXT-X-MEDIA"))
         if m.get("TYPE") == "AUDIO" and m.get("GROUP-ID") == n.get("AUDIO") and m.get("URI")),
        None,
    )
    return urljoin(master_url, adres), (urljoin(master_url, ses) if ses else None), n


def birlestir(parcalar: list[tuple[str, str]]) -> str:
    """(medya playlist metni, adresi) listesini tek VOD playlist'e ekler; tüm adresler mutlak."""
    hedef = max(int(float(m.group(1)) + 0.999) for metin, _ in parcalar for m in re.finditer(r"#EXT-X-TARGETDURATION:([\d.]+)", metin))
    cikti = ["#EXTM3U", "#EXT-X-VERSION:6", f"#EXT-X-TARGETDURATION:{hedef}", "#EXT-X-MEDIA-SEQUENCE:0", "#EXT-X-PLAYLIST-TYPE:VOD"]
    for sira, (metin, adres) in enumerate(parcalar):
        if sira:
            cikti.append("#EXT-X-DISCONTINUITY")
        for satir in (s.strip() for s in metin.splitlines()):
            if satir.startswith("#EXT-X-MAP"):
                cikti.append(re.sub(r'URI="([^"]+)"', lambda m: f'URI="{urljoin(adres, m.group(1))}"', satir))
            elif satir.startswith("#EXTINF"):
                cikti.append(satir)
            elif satir and not satir.startswith("#"):
                cikti.append(urljoin(adres, satir))
    cikti.append("#EXT-X-ENDLIST")
    return "\n".join(cikti) + "\n"


def master_olustur(video_url: str, ses_url: str | None, nitelik: dict[str, str]) -> str:
    """Tek varyantlı (+ ayrı ses) birleşik master."""
    sayisal = ("BANDWIDTH", "AVERAGE-BANDWIDTH", "RESOLUTION")
    inf = ",".join(f"{k}={v}" if k in sayisal else f'{k}="{v}"' for k, v in nitelik.items() if k != "AUDIO")
    satirlar = ["#EXTM3U", "#EXT-X-VERSION:6"]
    if ses_url:
        satirlar.append(f'#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="a",NAME="Audio",DEFAULT=YES,AUTOSELECT=YES,URI="{ses_url}"')
        inf += ',AUDIO="a"'
    return "\n".join(satirlar + [f"#EXT-X-STREAM-INF:{inf}", video_url]) + "\n"
