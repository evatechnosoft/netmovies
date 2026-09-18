# NetMovies — tüm kaynaklarda tek seferde arama.
#
# `/search` tek eklentiye sorar; kumanda ve TV "Gözat" ekranı bunu 11 kez paralel
# çağırıp elde birleştiriyordu. Aynı iş iki istemcide iki kez yazılmasın diye
# birleştirme sunucuya alındı: telefon tek istek atar, tünel üzerinden 11 tur
# atmaz. Yönetim panelinde gizlenen kaynaklar burada da düşer (HANDOFF dersi:
# yeni istemci ucu eklerken süzmeyi de bağla).

import asyncio

from urllib.parse import unquote_plus

from Core   import Request
from .      import api_v1_router, api_v1_global_message
from ..Libs import fuck_dmca, get_client_headers, lang_memo, source_score

from Public.Home.Libs import admin_config, watch_store
from Public.Home.Routers.tmdb import rating_for, year_for

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


# Kaç sonuç için ek bilgi (bölüm sayısı) çekilir. `load_item` sonucu 1 saat
# cache'li; yine de her sonuç için çağırmak aramayı gereksiz uzatır — listenin
# görünen başı zenginleşir, altı kaydırdıkça zaten yeniden aranır.
_ZENGIN_TAVANI = 14


def _kullanici_agirliklari() -> dict[str, int]:
    """Başlık anahtarı → sıralama ağırlığı.

    Aranan şey çoğu zaman kullanıcının zaten işaretlediği şeydir: favorisi,
    listesine aldığı ya da izlemeye başladığı içerik. Sıralamayı sunucu yapar —
    her istemci aynı kuralı yeniden yazmasın.
    """
    agirlik: dict[str, int] = {}

    def ekle(satirlar, puan: int) -> None:
        for satir in satirlar or []:
            key = lang_memo.anahtar((satir or {}).get("title") or "")
            if key:
                agirlik[key] = max(agirlik.get(key, 0), puan)

    kaynaklar = (
        (lambda: watch_store.list_favorites(), 400),
        (lambda: watch_store.list_user_list("izlenecek", 200), 320),
        (lambda: watch_store.list_user_list("takip", 200), 300),
        (lambda: watch_store.list_user_list("planlandi", 200), 260),
        (lambda: watch_store.list_continue_watching(50), 220),
    )
    for oku, puan in kaynaklar:
        try:
            ekle(oku(), puan)
        except Exception:
            # Liste okunamazsa sıralama alaka + sağlayıcı puanına düşer; arama ölmez.
            continue
    return agirlik


def _grupla(ogeler: list[dict]) -> list[dict]:
    """Aynı başlığı tek satırda toplar; sağlayıcılar `providers` altına iner.

    Sıra korunur: grubun yeri, o gruba ait EN İYİ sıradaki öğenin yeridir — üst
    sıradaki sağlayıcı zaten puanla seçilmiş oluyor, temsilci de odur.
    """
    gruplar: dict[str, dict] = {}
    for oge in ogeler:
        key = lang_memo.anahtar(oge.get("title") or "") or (oge.get("url") or "")
        grup = gruplar.get(key)
        saglayici = {"plugin": oge.get("plugin") or "", "url": oge.get("url") or ""}
        if grup is None:
            gruplar[key] = {**oge, "providers": [saglayici]}
        elif not any(s["plugin"] == saglayici["plugin"] for s in grup["providers"]):
            grup["providers"].append(saglayici)
            # Poster ilk gelenlerde boş olabiliyor; grupta dolu olan kazanır.
            if not grup.get("poster") and oge.get("poster"):
                grup["poster"] = oge["poster"]
    return list(gruplar.values())


async def _zenginlestir(ogeler: list[dict], client_headers: dict) -> None:
    """Satırda gösterilecek bilgiyi doldurur: dil rozeti, TMDB puanı/yılı, bölüm sayısı.

    Kalite ve süre BİLEREK burada yok: ikisi de oynatma zinciri (ya da TMDB detay
    isteği) gerektirir, 60 sonuç için o kadar tur atılmaz. İstemci bir satıra
    basınca zaten `load_item` + `resolve_sources` çağırıp kartı doldurur.
    """
    rozetler = lang_memo.rozetler()
    for sira, oge in enumerate(ogeler):
        baslik = oge.get("title") or ""
        rozet  = rozetler.get(lang_memo.anahtar(baslik))
        if rozet:
            oge["lang"] = rozet
        # Ana sayfadan farklı olarak arama TMDB'ye gidebilir: sonuç sayısı onlarla
        # ölçülü ve kullanıcı arama sonucunu beklemeye razı. Yıl aynı aramadan gelir.
        puan = await rating_for(baslik, fetch=sira < _ZENGIN_TAVANI)
        if puan is not None:
            oge["rating"] = puan
        yil = year_for(baslik)
        if yil:
            oge["year"] = yil

    async def bolumler(oge: dict) -> None:
        # Adres HAM gider: `oge["url"]` quote_plus KODLU gelir ve httpx parametreyi
        # bir kez daha kodlar — motor `%253A` görüp 500 döndürüyordu. İstemci kodlu
        # gönderir çünkü kodlama onun tarafında bir kez olur; sunucu içinden çağrıda
        # kodlamayı httpx üstlenir.
        try:
            detay = await asyncio.wait_for(
                fuck_dmca(
                    "/load_item",
                    params         = {
                        "plugin"     : oge.get("plugin"),
                        "encoded_url": unquote_plus(str(oge.get("url") or "")),
                    },
                    client_headers = client_headers,
                ),
                timeout = _KAYNAK_TIMEOUT,
            )
        except Exception:
            return
        liste = (detay or {}).get("episodes") or []
        if liste:
            oge["episode_count"] = len(liste)
            sezonlar = {e.get("season") for e in liste if isinstance(e, dict) and e.get("season")}
            if sezonlar:
                oge["season_count"] = len(sezonlar)

    await asyncio.gather(*(bolumler(o) for o in ogeler[:_ZENGIN_TAVANI]), return_exceptions=True)


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

    # Sıra: kullanıcının kendi listeleri en önde, sonra kanıtlanmış sağlayıcı.
    # Stabil sıralama — eşit ağırlıkta kaynakların kendi sırası korunur.
    agirlik  = _kullanici_agirliklari()
    puanlar  = source_score.puanlar()
    sade_sorgu = _sade(sorgu)

    def sira(oge: dict) -> float:
        baslik = oge.get("title") or ""
        skor   = float(agirlik.get(lang_memo.anahtar(baslik), 0))
        # Tam eşleşme, aynı adı taşıyan uzun varyantların önüne geçsin
        # ("Reacher" araması "Reacher: Ekstra" ile başlamasın).
        if _sade(baslik) == sade_sorgu:
            skor += 150.0
        return -(skor + puanlar.get(oge.get("plugin") or "", 0.0) / 10.0)

    ogeler = sorted(ogeler, key=sira)[:_SONUC_TAVANI]

    # `group=1`: aynı içerik her sağlayıcıda bir satır açıyordu — "reacher" araması
    # beş kez "Reacher" gösteriyordu. Yeni istemciler tek satır ister, sağlayıcı
    # seçimi bilgi kartına iner. Eski istemciler (TV Gözat, web kumanda) düz liste
    # beklediği için varsayılan DEĞİŞMEZ.
    if str(veri.get("group") or "") in ("1", "true"):
        ogeler = _grupla(ogeler)

    await _zenginlestir(ogeler, basliklar)

    return {**api_v1_global_message, "result": ogeler}
