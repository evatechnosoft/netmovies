# NetMovies — "oynatıcı kanıtı" (X-Sp) üreteci.
#
# Bazı kaynak oynatıcıları (fastplay/setplay ailesi) manifest ve segment
# isteklerinde tek kullanımlık bir imza başlığı istiyor: aynı imza ikinci kez
# gönderilirse 404 dönüyor. Yani imzayı istemciye bir kez verip geçmek işe
# yaramaz — HER giden istekte yeniden üretilmeli.
#
# Bu yüzden eklenti imzanın kendisini değil MALZEMESİNİ taşır
# (`X-Sp-Secret` + `X-Sp-Time`), proxy de her istekte imzayı burada üretir.
# Böylece TV, telefon ve web hiçbir şey bilmek zorunda kalmaz.

import random

SECRET_HEADER = "X-Sp-Secret"
TIME_HEADER   = "X-Sp-Time"
PROOF_HEADER  = "X-Sp"


def _base36(value: int) -> str:
    digits = "0123456789abcdefghijklmnopqrstuvwxyz"
    if value == 0:
        return "0"
    out = ""
    while value:
        value, rem = divmod(value, 36)
        out = digits[rem] + out
    return out


def _fnv1a(text: str) -> str:
    h = 2166136261
    for ch in text:
        h ^= ord(ch)
        h = (h * 16777619) & 0xFFFFFFFF
    return format(h, "x")


def build_proof(secret: str, issued_at: int) -> str:
    """Oynatıcının JS'iyle aynı biçim: <zaman>.<rastgele36>.<fnv1a(sp|zaman|rastgele)>."""
    rand = _base36(int(2176782336 * random.random()))
    return f"{issued_at}.{rand}.{_fnv1a(f'{secret}|{issued_at}|{rand}')}"


def apply_player_proof(headers: dict[str, str]) -> dict[str, str]:
    """Malzeme header'larını taze imzaya çevirir; malzemeyi upstream'e sızdırmaz.

    Malzeme yoksa header'lar olduğu gibi döner — diğer kaynaklar etkilenmez.
    """
    secret = headers.pop(SECRET_HEADER, None) or headers.pop(SECRET_HEADER.lower(), None)
    issued = headers.pop(TIME_HEADER, None) or headers.pop(TIME_HEADER.lower(), None)
    if not secret or not issued:
        return headers

    try:
        headers[PROOF_HEADER] = build_proof(str(secret), int(issued))
    except (TypeError, ValueError):
        pass

    return headers
