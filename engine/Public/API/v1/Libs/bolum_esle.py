# Bölüm seçimi: sıra değil NUMARA.
#
# Sağlayıcılar aynı diziyi farklı kapsamda veriyor (DDizi sayfalı listesi 3.
# bölümden başlıyor, DiziMom 1'den). Sıra numarasıyla seçim başka sağlayıcıda
# BAŞKA bölümü açar. Önce (sezon, bölüm) aranır, sezon bilinmiyorsa yalnız
# bölüm; bulunamazsa sıraya düşülür.

import re


def bolum_sirasi(bolumler: list, sira: int, bolum_no: int | None = None, sezon_no: int | None = None) -> int | None:
    """Seçilecek bölümün listedeki yerini döner; hiçbiri uymazsa None.

    `bolumler` eklentinin Episode nesneleri ya da dict'leri olabilir.
    """
    def alan(ep, ad):
        return ep.get(ad) if isinstance(ep, dict) else getattr(ep, ad, None)

    if bolum_no is not None:
        for i, ep in enumerate(bolumler):
            if alan(ep, "episode") != bolum_no:
                continue
            if sezon_no is None or alan(ep, "season") in (None, sezon_no):
                return i
    if 0 <= sira < len(bolumler):
        return sira
    return None


def int_or_none(deger) -> int | None:
    """Sorgu parametresi → int; boş/sayı değilse None (sıfır geçerli değer)."""
    metin = str(deger).strip() if deger is not None else ""
    return int(metin) if metin.isdigit() else None


# "Haysiyet 3.Bölüm", "X 2. Sezon 5. Bölüm": bölüm sayfası kartlarının başlığı.
_BASLIK_BOLUM = re.compile(
    r"\s*(?:(\d+)\s*\.?\s*sezon\w*\s*)?(\d+)\s*\.?\s*b[oö]l[uü]m\w*\s*$",
    re.IGNORECASE,
)


def basliktan_bolum(baslik: str) -> tuple[str, int | None, int | None]:
    """(dizi adı, sezon, bölüm). Başlıkta bölüm eki yoksa (baslik, None, None).

    Bölüm eki aramayı öldürüyor ("haysiyet 3.bölüm" hiçbir sitede sonuç vermez)
    ve seçilen bölümü taşıyan tek ipucu o: atılırsa alternatif 1. bölümü açar.
    """
    m = _BASLIK_BOLUM.search(baslik or "")
    if not m or m.start() == 0:
        return baslik, None, None
    sezon = int(m.group(1)) if m.group(1) else None
    return baslik[: m.start()].strip(), sezon, int(m.group(2))
