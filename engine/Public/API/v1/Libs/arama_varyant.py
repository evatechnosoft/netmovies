# Arama sorgusu sadeleştirme + varyant üretimi.
#
# `resolve_sources` içindeydi; router modülü `Core`'u çekip dairesel import
# yarattığı için test edilemiyordu. Saf fonksiyonlar, dış bağımlılığı yok.

from __future__ import annotations

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
