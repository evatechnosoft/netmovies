# Ajanda satırlarını gün başlıklarına bölen saf yardımcı.
#
# Router modülünde duruyordu; testten import edilince `Core` zinciri dairesel
# import veriyordu (engine'deki `arama_varyant` ile aynı ders). Dış bağımlılığı yok.

from __future__ import annotations


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
