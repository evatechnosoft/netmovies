# NetMovies — tüm kaynaklarda tek seferde arama.
#
# `/search` tek eklentiye sorar; kumanda ve TV "Gözat" ekranı bunu 11 kez paralel
# çağırıp elde birleştiriyordu. Aynı iş iki istemcide iki kez yazılmasın diye
# birleştirme sunucuya alındı: telefon tek istek atar, tünel üzerinden 11 tur
# atmaz. Yönetim panelinde gizlenen kaynaklar burada da düşer (HANDOFF dersi:
# yeni istemci ucu eklerken süzmeyi de bağla).

import asyncio

from Core   import Request
from .      import api_v1_router, api_v1_global_message
from ..Libs import fuck_dmca, get_client_headers

from Public.Home.Libs import admin_config

# Kaynak başına tavan: bir eklenti asılırsa bütün arama onu beklemesin.
# Middleware isteği 30sn'de kesiyor (`_istek.py`), altında kalmalı.
_KAYNAK_TIMEOUT = 12.0
_SONUC_TAVANI   = 60
# Kaynak başına tavan: sorguyu yok sayıp 30 öğelik popüler listesini döndüren bir
# kaynak, tek başına bütün sonuç listesini yutmasın.
_KAYNAK_BASINA  = 8

# Türkçe'ye duyarlı sadeleştirme: "İNCEPTION".lower() -> "i̇nception" (birleşik nokta)
# olduğu için düz lower() eşleşmeyi kaçırıyor.
_HARFLER = str.maketrans("İIıŞşĞğÜüÖöÇç", "iiissgguuoocc")


def _sade(metin: str) -> str:
    return str(metin or "").translate(_HARFLER).lower()


_ARTIKEL  = {"the", "a", "an"}
_AYRACLAR = (":", " - ", " – ", " — ", "|")


def _varyantlar(sorgu: str) -> list[str]:
    """Giderek kısalan arama varyantları — ilki boş dönerse sıradaki denenir.

    Kaynak sitelerin arama motorları tam ifade eşleştiriyor: "the odyssey" hiçbir
    kaynakta sonuç vermezken "odyssey" iki kaynakta buluyor; "Örümcek Adam:
    Yepyeni Bir Gün" bulunmuyor ama "Örümcek Adam" bulunuyor.
    """
    varyant = [sorgu]

    for ayrac in _AYRACLAR:
        if ayrac in sorgu:
            varyant.append(sorgu.split(ayrac)[0].strip())

    kelimeler = sorgu.split()
    if len(kelimeler) > 1 and _sade(kelimeler[0]) in _ARTIKEL:
        varyant.append(" ".join(kelimeler[1:]))
    if len(kelimeler) > 2:
        varyant.append(" ".join(kelimeler[:2]))

    return list(dict.fromkeys(v for v in varyant if len(v) >= 3))


def _alakali(ogeler: list, sorgu: str) -> list:
    """Sorguyu yok sayan kaynakları eler.

    Bazı eklentiler arama sorgusunu hiç kullanmayıp ana sayfa listesini döndürüyor
    ("inception" araması 30 alakasız sonuç getiriyordu). Kural kaynak bazında:
    bir kaynağın HİÇBİR sonucunda sorgu kelimelerinden biri geçmiyorsa o kaynak
    aramamış demektir, tamamı düşer. Eşleşme varsa kaynak kalır ve eşleşenler öne
    alınır — kaynak başlığı Türkçeleştirmiş olabilir ("Başlangıç - Inception")."""
    kelimeler = [k for k in _sade(sorgu).split() if len(k) > 2] or [_sade(sorgu)]

    def eslesiyor(oge: dict) -> bool:
        baslik = _sade(oge.get("title"))
        return any(k in baslik for k in kelimeler)

    kaynaklar: dict[str, list] = {}
    for oge in ogeler:
        kaynaklar.setdefault(oge.get("plugin") or "", []).append(oge)

    isabetli = []
    for grup in kaynaklar.values():
        isabetli.extend([o for o in grup if eslesiyor(o)][:_KAYNAK_BASINA])

    return isabetli


@api_v1_router.get("/search_all")
async def search_all(request: Request):
    veri  = request.state.veri or {}
    sorgu = str(veri.get("query") or "").strip()
    if not sorgu:
        return {**api_v1_global_message, "result": []}

    basliklar = get_client_headers(request)
    cfg       = admin_config.load_config()
    # Özel Koleksiyon kaynakları burada HER ZAMAN dışarıda: panelde görünür
    # yapılmış olsalar bile ("Özel Koleksiyon" ekranı onları listeleyebilsin diye)
    # genel aramaya karışmamalılar — "inception" araması 47 alakasız sonuç
    # getiriyordu. O koleksiyon kendi ekranından elle aranır.
    gizli     = set(cfg["hidden_providers"]) | set(cfg["adult_providers"])

    adlar = await fuck_dmca("/get_plugin_names", client_headers=basliklar)
    adlar = [ad for ad in (adlar or []) if ad not in gizli]

    varyantlar = _varyantlar(sorgu)

    async def tek(ad: str) -> list:
        for varyant in varyantlar:
            try:
                sonuc = await asyncio.wait_for(
                    fuck_dmca("/search", params={"plugin": ad, "query": varyant}, client_headers=basliklar),
                    timeout = _KAYNAK_TIMEOUT,
                )
            except asyncio.TimeoutError:
                return []
            except Exception:
                # Tek kaynağın çökmesi bütün aramayı düşürmemeli — kaynaklar sık sık
                # ölü domain / WAF 403 döndürüyor, diğerleri yine sonuç verir.
                return []
            # Kaynak adı sonuçta taşınmalı: kumanda "TV'de oynat" derken plugin gerekir.
            ogeler = [{**item, "plugin": ad} for item in (sonuc or []) if isinstance(item, dict)]
            if ogeler:
                return ogeler
        return []

    gruplar = await asyncio.gather(*(tek(ad) for ad in adlar), return_exceptions=True)

    ogeler: list[dict] = []
    for grup in gruplar:
        if isinstance(grup, list):
            ogeler.extend(grup)

    # Gizli kategori ve puan eşiği de burada uygulanır.
    ogeler = admin_config.filter_aggregate_items(ogeler, cfg)
    ogeler = _alakali(ogeler, sorgu)

    return {**api_v1_global_message, "result": ogeler[:_SONUC_TAVANI]}
