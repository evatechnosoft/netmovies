# NetMovies — Kayıtlar: elle seçilen film/bölümü sunucu diskine indirir.
#
# TV açık ya da kapalı, iş sunucuda sürer. Kaynak çözümü ve indirme mevcut
# yolu kullanır: resolve_sources zinciri + kendi video proxy'miz (WARP yedeği,
# imza başlıkları, Referer hepsi orada). Kayıt bir HLS kopyasıdır: playlist
# yerel dosya adlarıyla yeniden yazılır, segmentler diske iner. Hazır kayıt
# resolve_sources'ta ilk kaynak olarak döner — oynatma internetsiz sürer.
#
# Bant paylaşımı: biri izliyorsa (son 45 sn'de proxy ya da ilerleyen konum)
# indirme tek bağlantıya ve "izlerken" hızına iner; kimse izlemiyorsa PARALEL
# bağlantıyla "boşta" hızına çıkar. Hızlar TV Yönetim'den (prefs) gelir, Mbit/s:
# izlerken 0 = indirme durur, boşta 0 = sınırsız (varsayılan). Otomatik: açıksa Takip listesindeki dizilerin son bölümü kuyruğa.
#
# Veritabanı yok: her kaydın klasöründeki meta.json tek kaynaktır.

import asyncio
import hashlib
import json
import os
import re
import shutil
import time
from pathlib      import Path
from urllib.parse import urljoin, unquote_plus

import httpx

from CLI import konsol
from Public.Home.Libs.watch_store import normalize_key

KAYIT_DIR        = Path(os.getenv("KAYIT_DIR", "/kayitlar" if Path("/kayitlar").is_dir() else "kayitlar"))
IZLERKEN_MBIT    = float(os.getenv("KAYIT_IZLERKEN_MBIT", "40"))  # prefs yoksa: 100 Mbit hattın yarısı
BOSTA_MBIT       = float(os.getenv("KAYIT_BOSTA_MBIT", "0"))
OTOMATIK_ARALIK  = 3 * 60 * 60
MIN_BOS_GB       = float(os.getenv("KAYIT_MIN_BOS_GB", "20"))
YEREL            = os.getenv("KAYIT_YEREL", "http://127.0.0.1:3310")
PARALEL          = 4
IZLEME_PENCERESI = 45
MAX_DENEME       = 3
MAX_KAYNAK       = 4
ONDE             = 3    # takipte önden hazır tutulan bölüm sayısı
ISARET           = {"X-NM-Kayit": "1"}   # proxy bu isteği "izleme" saymaz, cache'e yazmaz

_ID_RE    = re.compile(r"^[0-9a-f]{16}$")
_DOSYA_RE = re.compile(r"^(?:[av]/)?[\w.-]+$")
_URI_RE   = re.compile(r'URI="([^"]+)"')

_son_izleme = 0.0
_gorev: asyncio.Task | None = None
_otomatik: asyncio.Task | None = None
_sonraki_an = 0.0                    # ortak hız sınırlayıcı: paralel bağlantılar toplamda sınırda
_client = httpx.AsyncClient(
    timeout         = httpx.Timeout(connect=10.0, read=60.0, write=10.0, pool=10.0),
    follow_redirects= True,
    trust_env       = False,
)


# ------------------------------------------------------------------- izleme
def izleme_oldu() -> None:
    global _son_izleme
    _son_izleme = time.monotonic()


def izleniyor() -> bool:
    return _son_izleme > 0 and time.monotonic() - _son_izleme < IZLEME_PENCERESI


def ayarlar() -> dict:
    """TV Ayarlar'ın yazdığı prefs anahtarları; yoksa ortam varsayılanı."""
    try:
        from ..Routers.prefs import _oku   # döngüsel import olmasın diye geç
        p = _oku()
    except Exception:
        p = {}

    def mbit(anahtar: str, varsayilan: float) -> float:
        try:
            return max(0.0, float(p.get(anahtar, varsayilan)))
        except (TypeError, ValueError):
            return varsayilan

    return {
        "izlerken_mbit": mbit("kayit_izlerken_mbit", IZLERKEN_MBIT),
        "bosta_mbit"   : mbit("kayit_bosta_mbit", BOSTA_MBIT),
        "otomatik"     : str(p.get("kayit_otomatik", "0")) == "1",
    }


async def _hiz_bekle(bayt: int, hiz: dict) -> None:
    global _sonraki_an
    # İzlerken payı 0: izleme bitene dek bekle (çubuk en solda).
    while izleniyor() and hiz["izlerken_mbit"] <= 0:
        await asyncio.sleep(5)
    mbit = hiz["izlerken_mbit"] if izleniyor() else hiz["bosta_mbit"]
    if mbit <= 0:
        return
    simdi = time.monotonic()
    _sonraki_an = max(simdi, _sonraki_an) + bayt * 8 / (mbit * 1_000_000)
    await asyncio.sleep(_sonraki_an - simdi)


# ------------------------------------------------------------------- kimlik/meta
def _int_or_none(v) -> int | None:
    try:
        return int(v) if v not in (None, "") else None
    except (TypeError, ValueError):
        return None


def kayit_id(title: str, season_no, episode_no, episode_index) -> str:
    """Site-agnostik: aynı bölüm hangi sağlayıcıdan açılırsa açılsın aynı kayıt."""
    s, e = _int_or_none(season_no), _int_or_none(episode_no)
    bolum = f"{s or 0}x{e}" if e is not None else f"i{_int_or_none(episode_index) or 0}"
    return hashlib.sha1(f"{normalize_key(title)}|{bolum}".encode("utf-8")).hexdigest()[:16]


def _klasor(kid: str) -> Path:
    return KAYIT_DIR / kid


def _oku(kid: str) -> dict | None:
    try:
        return json.loads((_klasor(kid) / "meta.json").read_text("utf-8"))
    except (OSError, ValueError):
        return None


def _yaz(meta: dict) -> None:
    klasor = _klasor(meta["id"])
    klasor.mkdir(parents=True, exist_ok=True)
    gecici = klasor / "meta.json.part"
    gecici.write_text(json.dumps(meta, ensure_ascii=False), "utf-8")
    gecici.replace(klasor / "meta.json")


def liste() -> list[dict]:
    if not KAYIT_DIR.is_dir():
        return []
    kayitlar = [m for m in (_oku(p.name) for p in KAYIT_DIR.iterdir() if _ID_RE.match(p.name)) if m]
    return sorted(kayitlar, key=lambda m: m.get("eklendi", 0), reverse=True)


def ekle(veri: dict) -> dict:
    title = str(veri.get("title") or "").strip()
    if not title or not veri.get("plugin") or not veri.get("content_url"):
        raise ValueError("title, plugin ve content_url gerekli")
    # load_item bölüm adresleri quote_plus KODLU gelir (TV sözleşmesi); motor ham
    # adres bekler. Kodlu giden adres "kaynak bulunamadı" oluyordu (A.B.İ. 9 bölüm).
    for anahtar in ("content_url", "item_url"):
        deger = str(veri.get(anahtar) or "")
        if deger.lower().startswith(("http%3a", "https%3a")):
            veri[anahtar] = unquote_plus(deger)
    kid  = kayit_id(title, veri.get("season_no"), veri.get("episode_no"), veri.get("episode"))
    eski = _oku(kid)
    if eski and eski.get("durum") in ("bekliyor", "iniyor", "hazir"):
        return eski
    meta = {
        "id"          : kid,
        "title"       : title,
        "poster"      : str(veri.get("poster") or ""),
        "plugin"      : str(veri["plugin"]),
        "content_url" : str(veri["content_url"]),           # çözülen adres (bölüm sayfası olabilir)
        "item_url"    : str(veri.get("item_url") or veri["content_url"]),   # listede açılan kart
        "media_type"  : str(veri.get("media_type") or ""),
        "episode"     : _int_or_none(veri.get("episode")) or 0,
        "episode_no"  : _int_or_none(veri.get("episode_no")),
        "season_no"   : _int_or_none(veri.get("season_no")),
        "episode_ref" : str(veri.get("episode_ref") or ""),
        "durum"       : "bekliyor",
        "ilerleme"    : 0.0,
        "bayt"        : 0,
        "deneme"      : 0,
        "hata"        : "",
        "eklendi"     : int(time.time()),
    }
    _yaz(meta)
    baslat()
    return meta


def sil(kid: str) -> bool:
    if not _ID_RE.match(kid or "") or not _klasor(kid).is_dir():
        return False
    shutil.rmtree(_klasor(kid), ignore_errors=True)
    return True


def hazir_kayit(title: str, season_no, episode_no, episode_index) -> dict | None:
    if not title:
        return None
    meta = _oku(kayit_id(title, season_no, episode_no, episode_index))
    return meta if meta and meta.get("durum") == "hazir" else None


def dosya(kid: str, yol: str) -> Path | None:
    """Servis edilecek dosya; klasör dışına çıkan her şey reddedilir."""
    if not _ID_RE.match(kid or "") or not _DOSYA_RE.match(yol or "") or ".." in yol or yol.startswith("meta"):
        return None
    hedef = _klasor(kid) / yol
    return hedef if hedef.is_file() else None


def izlendi_isaretle(kid: str, yol: str) -> None:
    """Son segment servis edildiyse kayıt izlenmiş sayılır (disk dolarken silinir)."""
    meta = _oku(kid)
    if meta and not meta.get("izlendi") and meta.get("son_segment") == yol:
        meta["izlendi"] = int(time.time())
        _yaz(meta)


# ------------------------------------------------------------------- disk
def _yer_ac() -> bool:
    """Boş alan tavanın altındaysa izlenmiş kayıtları eskiden yeniye siler."""
    KAYIT_DIR.mkdir(parents=True, exist_ok=True)
    tavan = MIN_BOS_GB * 1024 ** 3
    izlenenler = sorted((m for m in liste() if m.get("izlendi")), key=lambda m: m["izlendi"])
    while shutil.disk_usage(KAYIT_DIR).free < tavan:
        if not izlenenler:
            return False
        eski = izlenenler.pop(0)
        konsol.log(f"[yellow]⏺ yer açıldı:[/] {eski['title']} {eski.get('episode_ref', '')}")
        sil(eski["id"])
    return True


# ------------------------------------------------------------------- indirme
async def _getir_metin(url: str) -> tuple[str, str]:
    yanit = await _client.get(url, headers=ISARET)
    yanit.raise_for_status()
    return yanit.text, str(yanit.url)


async def _indir_dosya(url: str, hedef: Path) -> int:
    """Tek dosya; izlenirken hız sınırlı. Var olan dosya atlanır (devam)."""
    if hedef.exists():
        return hedef.stat().st_size
    gecici = hedef.with_name(hedef.name + ".part")
    hiz = ayarlar()
    for deneme in range(3):
        try:
            boyut = 0
            async with _client.stream("GET", url, headers=ISARET) as yanit:
                yanit.raise_for_status()
                with gecici.open("wb") as f:
                    async for parca in yanit.aiter_bytes(65536):
                        f.write(parca)
                        boyut += len(parca)
                        await _hiz_bekle(len(parca), hiz)
            gecici.replace(hedef)
            return boyut
        except (httpx.HTTPError, OSError):
            if deneme == 2:
                raise
            await asyncio.sleep(2 * (deneme + 1))
    return 0


def _ayikla_master(metin: str, taban: str) -> tuple[str, str, str | None, str | None]:
    """Master'dan ≤1080p en iyi varyantı ve onun ses grubunu seçer.

    Dönüş: (STREAM-INF satırı, varyant adresi, MEDIA satırı, ses adresi).
    """
    satirlar  = [s.strip() for s in metin.splitlines() if s.strip()]
    adaylar: list[tuple[int, int, str, str]] = []
    for i, satir in enumerate(satirlar):
        if satir.startswith("#EXT-X-STREAM-INF") and i + 1 < len(satirlar):
            yuk = re.search(r"RESOLUTION=\d+x(\d+)", satir)
            bw  = re.search(r"BANDWIDTH=(\d+)", satir)
            yukseklik = int(yuk.group(1)) if yuk else 0
            # 1080 üstü sona: 4K Mi Box'ta çözülmüyor (hafıza ses-var-goruntu-yok-codec).
            puan = yukseklik if yukseklik <= 1080 else -yukseklik
            adaylar.append((puan, int(bw.group(1)) if bw else 0, satir, urljoin(taban, satirlar[i + 1])))
    if not adaylar:
        raise ValueError("master boş")
    _, _, inf, varyant = max(adaylar)
    grup = re.search(r'AUDIO="([^"]+)"', inf)
    if not grup:
        return inf, varyant, None, None
    sesler = [s for s in satirlar if s.startswith("#EXT-X-MEDIA") and "TYPE=AUDIO" in s
              and f'GROUP-ID="{grup.group(1)}"' in s and "URI=" in s]
    if not sesler:
        return inf, varyant, None, None
    ses = next((s for s in sesler if "DEFAULT=YES" in s), sesler[0])
    return inf, varyant, ses, urljoin(taban, _URI_RE.search(ses).group(1))


async def _medya_indir(meta: dict, metin: str, taban: str, klasor: Path, pay: tuple[int, int]) -> str:
    """Media playlist'i indirir, yerel adlarla klasor/index.m3u8 yazar. Son segment adını döner."""
    if "#EXT-X-ENDLIST" not in metin:
        raise ValueError("canlı yayın kaydedilmez")
    klasor.mkdir(parents=True, exist_ok=True)
    uzanti = ".m4s" if "#EXT-X-MAP" in metin else ".ts"
    isler: list[tuple[str, Path]] = []
    cikti: list[str] = []
    ek = 0
    for satir in (s.strip() for s in metin.splitlines()):
        if not satir:
            continue
        if satir.startswith("#"):
            m = _URI_RE.search(satir)
            if m and satir.startswith(("#EXT-X-KEY", "#EXT-X-MAP")):
                ad = f"ek{ek}.bin" if satir.startswith("#EXT-X-KEY") else f"init{ek}.mp4"
                ek += 1
                isler.append((urljoin(taban, m.group(1)), klasor / ad))
                satir = satir.replace(m.group(0), f'URI="{ad}"')
            cikti.append(satir)
            continue
        ad = f"{len(isler):05d}{uzanti}"
        isler.append((urljoin(taban, satir), klasor / ad))
        cikti.append(ad)

    sira, toplam_pay = pay
    toplam = len(isler)
    bitti  = 0
    while bitti < toplam:
        kume  = 1 if izleniyor() else PARALEL
        grup  = isler[bitti:bitti + kume]
        boyut = await asyncio.gather(*(_indir_dosya(u, h) for u, h in grup))
        bitti += len(grup)
        meta["bayt"] += sum(boyut)
        meta["ilerleme"] = round((sira + bitti / toplam) / toplam_pay, 3)
        if bitti % 20 == 0 or bitti == toplam:
            _yaz(meta)

    (klasor / "index.m3u8").write_text("\n".join(cikti) + "\n", "utf-8")
    son = next((s for s in reversed(cikti) if not s.startswith("#")), "")
    return f"{klasor.name}/{son}"


async def _manifest_mi(adres: str) -> tuple[str, str] | None:
    """İlk 64 KB'ye bakar: manifestse (metin, son adres), değilse None (mp4 gibi)."""
    async with _client.stream("GET", adres, headers=ISARET) as yanit:
        yanit.raise_for_status()
        govde = b""
        async for parca in yanit.aiter_bytes(65536):
            govde += parca
            bas = govde.lstrip()[:7]
            if len(bas) == 7 and bas != b"#EXTM3U":
                return None
            if len(govde) > 2_000_000:
                raise ValueError("manifest çok büyük")
        if govde.lstrip()[:7] != b"#EXTM3U":
            return None
        return govde.decode("utf-8", "ignore"), str(yanit.url)


async def _kaynagi_indir(meta: dict, adres: str) -> None:
    klasor = _klasor(meta["id"])
    bulunan = await _manifest_mi(adres)
    if bulunan is None:
        # Progresif dosya (mp4): tek parça indir.
        meta["bayt"] = await _indir_dosya(adres, klasor / "video.mp4")
        meta.update(giris="video.mp4", son_segment="video.mp4")
        return
    metin, taban = bulunan
    if "#EXT-X-STREAM-INF" not in metin:
        meta["son_segment"] = await _medya_indir(meta, metin, taban, klasor / "v", (0, 1))
        meta["giris"] = "v/index.m3u8"
        return
    inf, varyant, ses_satiri, ses = _ayikla_master(metin, taban)
    pay = 2 if ses else 1
    # Alt playlist adresleri tek kullanımlık/kısa ömürlü (pichive l.php?v=): ses
    # listesini video inerken beklettiğimizde 403 veriyordu ve 1,3 GB çöpe gidiyordu.
    # İkisini de hemen al, segmentleri sonra indir.
    video_pl = await _getir_metin(varyant)
    ses_pl   = await _getir_metin(ses) if ses else None
    meta["son_segment"] = await _medya_indir(meta, *video_pl, klasor / "v", (0, pay))
    master = ["#EXTM3U"]
    if ses_pl:
        await _medya_indir(meta, *ses_pl, klasor / "a", (1, pay))
        master.append(_URI_RE.sub('URI="a/index.m3u8"', ses_satiri))
    master += [inf, "v/index.m3u8"]
    (klasor / "index.m3u8").write_text("\n".join(master) + "\n", "utf-8")
    meta["giris"] = "index.m3u8"


async def _kaynaklar(meta: dict) -> list[dict]:
    # Döngüsel import olmasın: Libs paketi bu modülü yükler.
    from . import fuck_dmca
    from .language     import order_by_language
    from .source_proxy import route_through_proxy

    params = {
        "plugin"      : meta["plugin"],
        "encoded_url" : meta["content_url"],
        "title"       : meta["title"],
        "episode"     : meta["episode"],
        "mode"        : "full",
    }
    if meta.get("episode_no") is not None:
        params["episode_no"] = meta["episode_no"]
    if meta.get("season_no") is not None:
        params["season_no"] = meta["season_no"]
    sonuc = await fuck_dmca("/resolve_sources", params=params, timeout=90.0)
    kaynaklar = (sonuc.get("sources") or []) if isinstance(sonuc, dict) else []
    return route_through_proxy(order_by_language(kaynaklar), YEREL, hepsi=True)


async def _kaydet(meta: dict) -> None:
    meta.update(durum="iniyor", deneme=meta.get("deneme", 0) + 1, hata="", ilerleme=0.0, bayt=0)
    _yaz(meta)
    # Yeniden başlatmada kaynak değişebilir; başka kaynağın segmentleri karışmasın.
    _temizle(meta["id"])
    kaynaklar = await _kaynaklar(meta)
    if not kaynaklar:
        raise ValueError("kaynak bulunamadı")
    son_hata: Exception | None = None
    for kaynak in kaynaklar[:MAX_KAYNAK]:
        try:
            await _kaynagi_indir(meta, kaynak["url"])
            meta.update(durum="hazir", ilerleme=1.0, bitti=int(time.time()), kaynak=kaynak.get("name", ""))
            _yaz(meta)
            konsol.log(f"[green]⏺ kayıt hazır:[/] {meta['title']} {meta['episode_ref']} · {meta['bayt'] // 2**20} MB")
            return
        except (httpx.HTTPError, OSError, ValueError) as hata:
            son_hata = hata
            konsol.log(f"[yellow]⏺ kaynak düştü:[/] {kaynak.get('name', '?')} · {type(hata).__name__}: {hata}")
            _temizle(meta["id"])
            meta["bayt"] = 0
    raise son_hata or ValueError("indirilemedi")


def _temizle(kid: str) -> None:
    for alt in ("v", "a", "index.m3u8", "video.mp4", "video.mp4.part"):
        hedef = _klasor(kid) / alt
        shutil.rmtree(hedef, ignore_errors=True) if hedef.is_dir() else hedef.unlink(missing_ok=True)


def _siradaki() -> dict | None:
    adaylar = [m for m in liste() if m.get("durum") in ("bekliyor", "iniyor")]
    return min(adaylar, key=lambda m: m.get("eklendi", 0)) if adaylar else None


async def _calis() -> None:
    while (meta := _siradaki()) is not None:
        if not _yer_ac():
            meta.update(durum="hata", hata="disk dolu")
            _yaz(meta)
            continue
        try:
            await _kaydet(meta)
        except Exception as hata:   # tek kayıt düşer, kuyruk sürer
            tekrar = meta.get("deneme", 0) < MAX_DENEME
            meta.update(durum="bekliyor" if tekrar else "hata", hata=f"{type(hata).__name__}: {hata}"[:200])
            _yaz(meta)
            konsol.log(f"[red]⏺ kayıt hatası:[/] {meta['title']} · {meta['hata']}")
            if tekrar:
                await asyncio.sleep(60)


def _bolum_sira(ref: str) -> tuple[int, int] | None:
    m = re.fullmatch(r"S(\d+)B(\d+)", ref or "")
    return (int(m.group(1)), int(m.group(2))) if m else None


async def _otomatik_tur() -> int:
    """`kayit_takip` listesindeki her dizi için izlenen/kayıtlı son bölümden SONRAKİ
    bölümleri (en çok ONDE) kuyruğa koyar; hiç iz yoksa en yeni bölümü. 3 saatte bir."""
    from . import fuck_dmca
    from Public.Home.Libs import watch_store

    kayitli = {(normalize_key(m["title"]), m.get("episode_ref")) for m in liste()}
    eklenen = 0
    for satir in watch_store.list_user_list("kayit_takip", 200):
        adres = satir.get("content_url") or ""
        if not adres or not satir.get("title"):
            continue
        try:
            detay = await fuck_dmca("/load_item", params={"plugin": satir["plugin"], "encoded_url": adres, "title": satir["title"]}, timeout=60.0)
        except Exception:
            continue
        bolumler = (detay.get("episodes") or []) if isinstance(detay, dict) else []
        sirali = sorted(
            ((int(b.get("season") or 1), int(b["episode"]), i, b) for i, b in enumerate(bolumler)
             if isinstance(b, dict) and b.get("episode") is not None),
        )
        if not sirali:
            continue
        anahtar = normalize_key(satir["title"])
        izler = [_bolum_sira(ref) for t, ref in kayitli if t == anahtar]
        gecmis = watch_store.get_progress(satir.get("content_key") or "") or {}
        izler.append(_bolum_sira(gecmis.get("episode") or ""))
        son = max((x for x in izler if x), default=None)
        adaylar = [x for x in sirali if son is None or (x[0], x[1]) > son]
        if son is None:
            adaylar = sirali[-1:]
        for sezon, no, i, b in adaylar[:ONDE]:
            ekle({
                "plugin": satir["plugin"], "title": satir["title"], "poster": satir.get("poster"),
                "content_url": b.get("url") or adres, "item_url": adres, "media_type": "serie",
                "episode": i, "episode_no": no, "season_no": sezon, "episode_ref": f"S{sezon}B{no}",
            })
            eklenen += 1
    return eklenen


async def _otomatik_dongu() -> None:
    while True:
        if ayarlar()["otomatik"]:
            try:
                n = await _otomatik_tur()
                if n:
                    konsol.log(f"[green]⏺ otomatik:[/] takipten {n} yeni bölüm kuyrukta")
            except Exception as hata:
                konsol.log(f"[red]⏺ otomatik tur düştü:[/] {type(hata).__name__}: {hata}")
        await asyncio.sleep(OTOMATIK_ARALIK)


def baslat() -> None:
    """Kuyruk işçisini (tek) ve otomatik takip döngüsünü başlatır; açılışta ve her eklemede çağrılır."""
    global _gorev, _otomatik
    dongu = asyncio.get_running_loop()
    if _gorev is None or _gorev.done():
        _gorev = dongu.create_task(_calis())
    if _otomatik is None or _otomatik.done():
        _otomatik = dongu.create_task(_otomatik_dongu())
