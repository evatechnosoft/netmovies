# Arama sorgusu sadeleştirme + varyant üretimi.
#
# `resolve_sources` içindeydi; router modülü `Core`'u çekip dairesel import
# yarattığı için test edilemiyordu. Saf fonksiyonlar, dış bağımlılığı yok.

from __future__ import annotations

import re

# Türkçe'ye duyarlı sadeleştirme: "İNCEPTION".lower() -> "i̇nception" (birleşik nokta).
_HARFLER = str.maketrans("İIıŞşĞğÜüÖöÇç", "iiissgguuoocc")

# Arama başlığındaki site gürültüsü.
_NOISE = (
    "izle", "full hd", "hd", "4k", "1080p", "1080", "720p", "720",
    "türkçe", "turkce", "dublaj", "altyazılı", "altyazili", "altyazı", "altyazi",
    "dizisi", "filmi",
)

# Başlık başındaki artikel kaynak sitelerin arama motorunu boşa düşürüyor.
_ARTIKEL = {"the", "a", "an"}
# Alt başlık ayraçları: "Örümcek Adam: Yepyeni Bir Gün" hiçbir sitede tam eşleşmiyor.
_AYRACLAR = (":", " - ", " – ", " — ", "|")


def clean_title(title: str | None) -> str:
    text = (title or "").lower()
    for noise in _NOISE:
        text = text.replace(noise, " ")
    return " ".join(text.split()).strip(" -·:")


def query_variants(title: str | None) -> list[str]:
    """Giderek kısalan arama varyantları — ilk sonuç veren kazanır.

    Kaynak sitelerin arama motorları tam ifade eşleştiriyor: "the odyssey" 0
    sonuç verirken "odyssey" iki kaynakta buluyor, "Örümcek Adam: Yepyeni Bir
    Gün" hiçbir yerde bulunmuyor ama "Örümcek Adam" bulunuyor. Tek varyantla
    arayan zincir bu yüzden "hiçbir sağlayıcı kaynak vermedi" diyordu.
    """
    temel   = clean_title(title)
    varyant = [temel]

    for ayrac in _AYRACLAR:
        if ayrac in temel:
            varyant.append(temel.split(ayrac)[0].strip())

    kelimeler = temel.split()
    if len(kelimeler) > 1 and kelimeler[0] in _ARTIKEL:
        varyant.append(" ".join(kelimeler[1:]))
    if len(kelimeler) > 2:
        varyant.append(" ".join(kelimeler[:2]))

    return list(dict.fromkeys(v for v in varyant if len(v) >= 3))


def _anlamli_kelimeler(metin: str) -> set[str]:
    """Anlamlı kelimeler. Sayılar UZUNLUKTAN muaf: "Daha 17"un ayırt edici
    parçası "17"dir ve iki harf sınırına takılıp eleniyordu — geriye {daha}
    kalınca "Hızlı ve Öfkeli 2 Daha Hızlı Daha Öfkeli" eşleşme sayılıyor ve
    zincire yanlış film giriyordu."""
    sade = str(metin or "").translate(_HARFLER).lower()
    return {
        k for k in re.sub(r"[^\w\s]", " ", sade).split()
        if len(k) > 2 or k.isdigit()
    }


# Sağlayıcı başlığına eklenen sıra/bölüm sözcükleri yapımı değiştirmez.
_EKLER = {"sezon", "sezonu", "bolum", "bolumu", "son", "kisim", "part", "season", "episode"}


def _aday_parcalari(aday_baslik: str | None) -> list[str]:
    """Sağlayıcılar Türkçe ve orijinal adı TEK satırda veriyor:
    "Başlangıç - Inception", "Jack Reacher: Asla Geri Dönme - Jack Reacher:
    Never Go Back". Parçalar ayrı ayrı denenir; ":" bölünmez, alt başlık
    yapımın kendi parçasıdır."""
    ham      = clean_title(aday_baslik)
    parcalar = [ham]
    for ayrac in (" - ", " – ", " — ", "|"):
        if ayrac in ham:
            parcalar.extend(p.strip() for p in ham.split(ayrac))
    return [p for p in parcalar if p]


def baslik_uyusuyor(aranan: str, aday_baslik: str | None) -> bool:
    """Kısaltılmış varyantla bulunan sonucu ASIL başlığa karşı doğrular.

    Varyantlar kasten geniş: "Örümcek Adam: Yepyeni Bir Gün" için "örümcek adam"
    da aranıyor. Sonucu doğrulamadan kabul etmek yanlış film açıyordu (Dean:
    "örümcek adam açıyorum çizgi film çıkıyor"). İki yönlü kural:

    1. Asıl başlığın anlamlı kelimelerinin TAMAMI adayda geçmeli.
    2. Adayda KALAN kelimeler yapımı değiştirmemeli — "Reacher" (dizi) arayıp
       "Jack Reacher: Asla Geri Dönme" (film) bulmak tam olarak buydu: hedef
       tek kelime olduğu için kapsama sınavını geçiyor, ama başka yapım.
       Sezon/bölüm eki ve yıl/sıra sayıları muaf.

    Noktalama ve site eki ("izle") farkı tolere edilir.
    """
    hedef = _anlamli_kelimeler(aranan)
    if not hedef:
        return False

    for parca in _aday_parcalari(aday_baslik):
        aday = _anlamli_kelimeler(parca)
        if not hedef <= aday:
            continue
        fazla = {k for k in aday - hedef if not k.isdigit() and k not in _EKLER}
        if not fazla:
            return True

    return False
