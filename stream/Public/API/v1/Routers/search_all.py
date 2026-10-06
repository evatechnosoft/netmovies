# NetMovies — tüm kaynaklarda tek seferde arama.
#
# `/search` tek eklentiye sorar; kumanda ve TV "Gözat" ekranı bunu 11 kez paralel
# çağırıp elde birleştiriyordu. Aynı iş iki istemcide iki kez yazılmasın diye
# birleştirme sunucuya alındı: telefon tek istek atar, tünel üzerinden 11 tur
# atmaz. Yönetim panelinde gizlenen kaynaklar burada da düşer (HANDOFF dersi:
# yeni istemci ucu eklerken süzmeyi de bağla).

import asyncio

from dataclasses import asdict
from urllib.parse import quote_plus, unquote_plus

from Core   import Request
from .      import api_v1_router, api_v1_global_message
from ..Libs import fuck_dmca, get_client_headers, lang_memo, source_score
from ..Libs.arama_niyet import ayristir, sirala, tekillestir, yapim_yili

from Public.Home.Libs import admin_config, watch_store
from Public.Home.Routers.tmdb import rating_for, type_for, year_for

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


def _bitisik(metin: str) -> str:
    return "".join(c for c in metin if c.isalnum())


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
        # Noktalamasız da bakılır: site "Abi", resmi kanal "A.B.İ." yazıyor.
        bitisik = _bitisik(baslik)
        return any(k in baslik or (_bitisik(k) and _bitisik(k) in bitisik) for k in kelimeler)

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


async def _yil_ve_rozet(ogeler: list[dict]) -> None:
    """Sıralamanın ihtiyaç duyduğu bilgi: dil rozeti, TMDB puanı, yapım yılı.

    Tekilleştirmeden ÖNCE çalışır: yıl birleştirme anahtarının parçası.
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
        yil = yapim_yili(oge, year_for(baslik), type_for(baslik))
        if yil:
            oge["year"] = yil


async def _bolum_sayilari(ogeler: list[dict], client_headers: dict) -> None:
    """Satırdaki bölüm/sezon sayısı — görünen baş için `load_item`.

    Kalite ve süre BİLEREK burada yok: ikisi de oynatma zinciri (ya da TMDB detay
    isteği) gerektirir, 60 sonuç için o kadar tur atılmaz. İstemci bir satıra
    basınca zaten `load_item` + `resolve_sources` çağırıp kartı doldurur.
    """
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


# YouTube araması (Dean, 5 Ekim: "ne yazarsam bulsun, öne al"). Sağlayıcı
# aramasından AYRI: YouTube eklentisinin kendi `search`ü yalnız resmi dizi
# listesi döner, çünkü kaynak zinciri onu başlık eşleştirmede kullanıyor; her
# videoyu oraya katmak "Abi" filmine rastgele bir video eşlerdi.
# yt: "0" (varsayılan, kapalı) | "video" | "liste" (oynatma listesi).
# Varsayılan kapalı (Dean, 6 Ekim): YouTube önde olunca asıl diziler kısa videoların
# altında kalıyordu; YouTube araması Gözat'ta YouTube seçiliyken ya da elle açılır.
# sadece=youtube: sağlayıcılar sorulmaz, yalnız YouTube (Gözat → YouTube → ara).
_YT_ADET = 12


def youtube_kartlari(videolar: list) -> list[dict]:
    """Motorun /youtube-search satırlarını katalog kartına çevirir (adres kodlu)."""
    kartlar = []
    for v in videolar or []:
        if not isinstance(v, dict) or not v.get("url"):
            continue
        kanal = v.get("channel") or ""
        kartlar.append({
            "plugin"  : "YouTube",
            "title"   : v.get("title") or "YouTube",
            "url"     : quote_plus(v["url"]),
            "poster"  : v.get("poster") or "",
            "category": f"YouTube · {kanal}" if kanal else "YouTube",
        })
    return kartlar


def youtube_one(youtube: list[dict], ogeler: list[dict]) -> list[dict]:
    """Sıra: resmi YouTube dizi kartı → YouTube araması → diğer sağlayıcılar.

    Resmi kart (eklenti aramasından, bölüm listeli) videolardan önce: "abi" yazınca
    dizinin kendisi tek tek bölüm videolarının arkasında kalmasın.
    """
    resmi    = [o for o in ogeler if o.get("plugin") == "YouTube"]
    adresler = {o.get("url") for o in resmi}
    youtube  = [k for k in youtube if k["url"] not in adresler]
    adresler |= {k["url"] for k in youtube}
    return resmi + youtube + [o for o in ogeler if o.get("url") not in adresler]


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
    if veri.get("sadece") == "youtube":
        adlar = [ad for ad in adlar if ad == "YouTube"]

    # "resident evil dublaj 2026" → başlık "resident evil"; işaret sözcükleri
    # sağlayıcıya gitmez (HDFilmCehennemi "resident evil 2026" için 0 döndürüyor).
    niyet  = ayristir(sorgu)
    aranan = niyet.baslik or sorgu
    varyantlar = _varyantlar(aranan)

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

    yt_tur = str(veri.get("yt") or "0")

    async def youtube() -> list[dict]:
        if yt_tur not in ("video", "liste"):
            return []
        try:
            sonuc = await asyncio.wait_for(
                fuck_dmca("/youtube-search", params={"query": sorgu, "limit": _YT_ADET, "tur": yt_tur}, client_headers=basliklar),
                timeout = _KAYNAK_TIMEOUT,
            )
        except Exception:
            return []   # YouTube düşerse sağlayıcı sonuçları yine gelir
        return youtube_kartlari(sonuc if isinstance(sonuc, list) else [])

    yt_gorev = asyncio.create_task(youtube())
    gruplar  = await asyncio.gather(*(tek(ad) for ad in adlar), return_exceptions=True)

    ogeler: list[dict] = []
    for grup in gruplar:
        if isinstance(grup, list):
            ogeler.extend(grup)

    # Gizli kategori ve puan eşiği de burada uygulanır.
    ogeler = admin_config.filter_aggregate_items(ogeler, cfg)
    ogeler = _alakali(ogeler, aranan)

    # Sıra: kullanıcının kendi listeleri en önde, sonra kanıtlanmış sağlayıcı.
    # Stabil sıralama — eşit ağırlıkta kaynakların kendi sırası korunur.
    agirlik  = _kullanici_agirliklari()
    puanlar  = source_score.puanlar()
    sade_sorgu = _sade(aranan)

    def sira(oge: dict) -> float:
        baslik = oge.get("title") or ""
        skor   = float(agirlik.get(lang_memo.anahtar(baslik), 0))
        # Tam eşleşme, aynı adı taşıyan uzun varyantların önüne geçsin
        # ("Reacher" araması "Reacher: Ekstra" ile başlamasın).
        if _sade(baslik) == sade_sorgu:
            skor += 150.0
        return -(skor + puanlar.get(oge.get("plugin") or "", 0.0) / 10.0)

    ogeler = sorted(ogeler, key=sira)[:_SONUC_TAVANI]
    await _yil_ve_rozet(ogeler)

    # Aynı içerik her sağlayıcıda bir kart açıyordu (Dean: "postere bastığı için
    # mükerrerler"). Tek kart, sağlayıcılar `providers` altında; kart puanı en
    # yüksek sağlayıcıyı açar (girdi yukarıda puanla sıralı). Kart şekli eski düz
    # satırın üst kümesi — TV Gözat ve saat ayrı sürüm istemeden aynı alanları okur.
    ogeler = sirala(tekillestir(ogeler), niyet)
    # Kullanıcının kendi listesindeki içerik niyet sırasının da önünde kalır.
    ogeler = sorted(ogeler, key=lambda o: -agirlik.get(lang_memo.anahtar(o.get("title") or ""), 0))

    await _bolum_sayilari(ogeler, basliklar)
    # Bölüm sayımı YouTube kartlarına yapılmaz: her video için yt-dlp koşardı.
    if yt_tur in ("video", "liste"):
        ogeler = youtube_one(await yt_gorev, ogeler)

    return {**api_v1_global_message, "result": ogeler, "niyet": asdict(niyet)}
