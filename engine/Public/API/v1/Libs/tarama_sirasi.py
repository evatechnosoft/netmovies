# NetMovies — alternatif sağlayıcı tarama sırası.
#
# Zincir ilk çalışan kaynakta durduğu için sıra DOĞRUDAN bekleme süresidir:
# o hafta bozulan sağlayıcı listenin başındaysa her çözümleme onun bütçesini
# (ALTERNATIVE_TIMEOUT) harcayıp geçiyordu.
#
# Elle yazılmış liste yalnız SOĞUK BAŞLANGIÇ: stream kanıta dayalı sıra
# (`order`) gönderdiğinde o geçerli. Puan/kanıt stream tarafında tutulur
# (Public/API/v1/Libs/source_score.py) — engine mekanizmayı tutar, hafızayı değil.

# Dublaj ağırlıklı kaynaklar önde; sonra harf sırası.
_ONCELIKLI = ["DiziPal", "DiziMom", "HDFilmCehennemi"]
_DIGERLERI = sorted(["KultFilmler", "FilmMakinesi", "DiziBox", "DiziYou", "Dizilla", "SezonlukDizi"])
ALTERNATIVE_ORDER = _ONCELIKLI + _DIGERLERI

# Tarama DIŞI: yetişkin eklentileri film/dizi çözümlemesinde asla işe yaramaz,
# her zincire bir arama turu eklerler. Bunun dışında kalan HER yüklü eklenti
# taranır (bkz. `tum_saglayicilar`).
TARAMA_DISI = frozenset({
    "HQPorner", "SpankBang", "FullPorner", "PornHub",
    "xHamster", "OxAx", "UncutMaza",
})


def tum_saglayicilar(ham_sira: str | None, mevcut: list[str] | set[str]) -> list[str]:
    """Taranacak sağlayıcılar: sıralama bilinenden, KAPSAM yüklü eklentilerden.

    `ALTERNATIVE_ORDER` elle yazılmış 9 adlık bir listeydi ve kapsamı da o
    belirliyordu: motorda yüklü olan DDizi, FullHDFilmizlesene, JetFilmizle
    zincire hiç girmiyordu — arama onları buluyor, çözümleme denemiyordu
    (Dean: "orası her ortamı taramalı"). Sıra hâlâ kanıta/öncelik listesine
    göre; adı listede geçmeyen eklenti sona eklenir.
    """
    sira = [ad for ad in tarama_sirasi(ham_sira) if ad not in TARAMA_DISI]
    sira += sorted(ad for ad in mevcut if ad not in sira and ad not in TARAMA_DISI)
    return sira

# Bir alternatif sağlayıcıya ayrılan üst süre. Ölü site (DNS/connect timeout) eskiden
# httpx'in kendi süresine kadar zinciri bekletiyordu; bütçe aşılırsa o sağlayıcı atlanır.
ALTERNATIVE_TIMEOUT = 25


def tarama_sirasi(ham_sira: str | None) -> list[str]:
    """Tarama sırası. Sırada olmayan sağlayıcı listenin SONUNA eklenir:
    puanı henüz olmayan yeni eklenti sessizce kaybolmamalı."""
    ham_sira = (ham_sira or "").strip()
    if not ham_sira:
        return ALTERNATIVE_ORDER
    istenen = [p.strip() for p in ham_sira.split(",") if p.strip()]
    if not istenen:
        return ALTERNATIVE_ORDER
    return istenen + [n for n in ALTERNATIVE_ORDER if n not in istenen]
