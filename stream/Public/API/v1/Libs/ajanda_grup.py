# Ajanda satırlarını gün başlıklarına bölen saf yardımcı.
#
# Router modülünde duruyordu; testten import edilince `Core` zinciri dairesel
# import veriyordu (engine'deki `arama_varyant` ile aynı ders). Dış bağımlılığı yok.

from __future__ import annotations


def aralikla(satirlar: list[dict], son_gun: str) -> list[dict]:
    """Aylık listeden haftalık görünümü süzer.

    Hafta ve ay ayrı TMDB turuyla çekildiğinde `discover` iki aralık için farklı
    "ilk N popüler" listesi döndürüyordu: aynı gün haftada 6, ayda 5 satır
    görünüyor, ay haftanın alt kümesi olmuyordu. Tek liste çekilip burada
    süzülür — iki görünüm arasında tutarsızlık kalmaz.
    """
    return [s for s in satirlar if s["tarih"] <= son_gun]


def takibi_birlestir(populer: list[dict], takip: list[dict]) -> list[dict]:
    """Takipteki dizilerin bölümlerini popüler listeye katar.

    Aynı dizi popülerde de varsa (`tmdb_id`) popüler satırı atılır; takip satırı
    kopyalanıp `takip=True` işaretlenir (popüler liste paylaşılan önbellekte,
    yerinde değiştirilmez). Aynı günde takipteki diziler önce gelir.
    """
    if not takip:
        return populer
    takip_idler = {s.get("tmdb_id") for s in takip if s.get("tmdb_id")}
    satirlar = [s for s in populer if s.get("tmdb_id") not in takip_idler]
    satirlar += [{**s, "takip": True} for s in takip]
    # Kararlı sıralama: gün içindeki popülerlik sırası korunur.
    return sorted(satirlar, key=lambda s: (s["tarih"], 0 if s.get("takip") else 1))


def gunlere_bol(satirlar: list[dict]) -> list[dict]:
    """Tarihe göre sıralı satırları gün başlıklarına böler.

    Arayüz takvimi satır satır değil gün başlıklarıyla çiziyor; gruplama burada
    yapılır ki hem web sayfası hem TV aynı yapıyı görsün.
    """
    gunler: list[dict] = []
    for satir in satirlar:
        if not gunler or gunler[-1]["tarih"] != satir["tarih"]:
            gunler.append({"tarih": satir["tarih"], "ogeler": []})
        gunler[-1]["ogeler"].append(satir)
    return gunler
