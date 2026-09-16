"""Son iyi manifest önbelleği — tek kullanımlık kaynak adresleri için.

Bazı sağlayıcılar oynatma adresini TEK KULLANIMLIK veriyor
(`.../l.php?v=<jeton>` gibi): ilk istek 200 döner, oynatma başlar; oynatıcı
manifesti yeniden istediğinde aynı adres 403 verir. Kanıt (16 Eylül, Dizilla):
aynı `four.pichive.online/l.php?v=…` adresi arka arkaya 403, WARP çıkışıyla da
403 — IP engeli olsa WARP çözerdi, jeton tükenmişse her çıkıştan 403 gelir.
Sonuç: film birkaç dakika oynayıp "çalışan kaynak bulunamadı"ya düşüyordu.

Çözüm, başarıyla indirilen manifest gövdesini saklamak ve YALNIZ upstream hata
verdiğinde onu dönmek. Normal akışta hiçbir şey değişmez (manifest her zaman
tazeden gelir), bu yüzden canlı yayın da etkilenmez: canlı akışta upstream
sağlıklıysa önbelleğe hiç bakılmaz.

Segment adresleri burada TUTULMAZ — onların kendi önbelleği var ve megabaytlarca
yer tutarlar. Buradaki kayıtlar birkaç yüz KB'lık metin.
"""

from __future__ import annotations

import time
from collections import OrderedDict

# Manifest metni birkaç yüz KB; 64 kayıt en kötü ihtimalle onlarca MB değil,
# birkaç MB tutar. Aynı anda bu kadar farklı içerik izlenmiyor zaten.
_TAVAN = 64

# Bir film ~2 saat. Jeton tükendikten sonra da oynatmanın sonuna kadar yetmeli;
# daha uzun tutmanın anlamı yok, ertesi gün o adres zaten geçersiz.
_OMUR_SANIYE = 3 * 60 * 60

# url -> (govde, content_type, yazilma_zamani)
_kayitlar: OrderedDict[str, tuple[bytes, str, float]] = OrderedDict()


def yaz(url: str, govde: bytes, content_type: str) -> None:
    """Başarıyla indirilmiş manifesti sakla. Boş gövde kaydedilmez."""
    if not govde:
        return
    _kayitlar[url] = (govde, content_type, time.time())
    _kayitlar.move_to_end(url)
    while len(_kayitlar) > _TAVAN:
        _kayitlar.popitem(last=False)


def oku(url: str) -> tuple[bytes, str] | None:
    """Saklanan manifest; yoksa ya da ömrü dolduysa None."""
    kayit = _kayitlar.get(url)
    if kayit is None:
        return None
    govde, content_type, yazilma = kayit
    if time.time() - yazilma > _OMUR_SANIYE:
        _kayitlar.pop(url, None)
        return None
    _kayitlar.move_to_end(url)
    return govde, content_type


def temizle() -> None:
    """Testler için."""
    _kayitlar.clear()


def sayi() -> int:
    return len(_kayitlar)
