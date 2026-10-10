# Ses/görüntü süre kayması düzeltmesi (proxy tarafı).
#
# Kimi kaynakta ses ayrı rendition'dır ve görüntü zaman çizelgesi sesinkinden
# sabit oranda kısadır: Daha 17 (DiziPal, 10 Eki) ses 8640,07 sn, görüntü
# 8628,17 sn → ses doğrusal olarak geride kaldı (1:49'da ~9 sn). Engine oranı
# ölçer (`av_oran` = ses / görüntü); proxy görüntü varyantının #EXTINF'lerini ve
# TS segmentlerindeki PES PTS/DTS'yi bu oranla esnetir. Ses dokunulmaz — kare
# zamanlamasındaki %0,1'lik değişim görünmez, seste duyulurdu.

import re

AV_ORAN_ALT, AV_ORAN_UST = 0.98, 1.02   # bunun dışı kayma değil, yanlış ölçüm
_TS_PAKET  = 188
_PTS_MASKE = (1 << 33) - 1


def oran_oku(deger: str | None) -> float | None:
    """Sorgu parametresinden oran; geçersiz/aralık dışıysa None."""
    try:
        oran = float(deger or "")
    except ValueError:
        return None
    return oran if AV_ORAN_ALT <= oran <= AV_ORAN_UST and oran != 1.0 else None


def extinf_olcekle(satir: str, oran: float) -> str:
    """`#EXTINF:10.416667,` → süre × oran (başlık kısmı korunur)."""
    return re.sub(r"^#EXTINF:([\d.]+)", lambda m: f"#EXTINF:{float(m.group(1)) * oran:.6f}", satir)


def _pts_oku(b: bytearray, i: int) -> int:
    return (((b[i] >> 1) & 7) << 30) | (b[i + 1] << 22) | ((b[i + 2] >> 1) << 15) | (b[i + 3] << 7) | (b[i + 4] >> 1)


def _pts_yaz(b: bytearray, i: int, v: int) -> None:
    b[i]     = (b[i] & 0xF0) | ((v >> 29) & 0x0E) | 1
    b[i + 1] = (v >> 22) & 0xFF
    b[i + 2] = ((v >> 14) & 0xFE) | 1
    b[i + 3] = (v >> 7) & 0xFF
    b[i + 4] = ((v << 1) & 0xFE) | 1


def ts_olcekle(veri: bytes, oran: float) -> bytes:
    """MPEG-TS içindeki görüntü PES'lerinin PTS/DTS'sini oranla çarpar; TS değilse aynen döner."""
    if not veri or veri[0] != 0x47:
        return veri
    # ponytail: PCR olduğu gibi kalır — HLS'te ExoPlayer zamanı PES PTS'den alır.
    b = bytearray(veri)
    for p in range(0, len(b) - _TS_PAKET + 1, _TS_PAKET):
        if b[p] != 0x47 or not b[p + 1] & 0x40:   # senkron yok / PES başlangıcı değil
            continue
        afc = (b[p + 3] >> 4) & 3
        o = p + 4
        if afc & 2:
            o += 1 + b[o]
        if not afc & 1 or o + 19 > p + _TS_PAKET:
            continue
        if b[o:o + 3] != b"\x00\x00\x01" or not 0xE0 <= b[o + 3] <= 0xEF:
            continue
        bayrak = b[o + 7] >> 6
        if bayrak & 2:
            _pts_yaz(b, o + 9, round(_pts_oku(b, o + 9) * oran) & _PTS_MASKE)
        if bayrak == 3:
            _pts_yaz(b, o + 14, round(_pts_oku(b, o + 14) * oran) & _PTS_MASKE)
    return bytes(b)
