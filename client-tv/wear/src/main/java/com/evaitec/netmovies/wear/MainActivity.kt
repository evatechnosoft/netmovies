package com.evaitec.netmovies.wear

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import kotlin.math.roundToInt

// NetMovies Mini — saat kumandası.
//
// Kadran küçük: dört ayrı yön tuşuna isabet etmek zor. Bu yüzden ekranın tamamı
// dokunmatik yüzey — kaydırma yön, dokunma OK. Tuş ızgarası yok.
//
// Üstte Devam Et posterleri (yuvarlak, yatay): dokunmak doğrudan televizyonda
// başlatır; küçük ekranda "seç → onayla" ikinci adımı israf.
//
// Yükleme iki aşamalı: Devam Et sunucunun yerel kaydından milisaniyeler içinde
// gelir ve HEMEN çizilir; Yeni Çıkanlar arkadan eklenir. Tek beklemeyle ikisini
// birden istemek, soğuk agregasyonda (~40 sn) saati yarım dakika boş tutuyordu.

private val Zemin   = Color(0xFF0A0C10)
private val Kart    = Color(0xFF161A22)
private val Metin   = Color(0xFFE8EAF0)
private val Soluk   = Color(0xFF8B93A7)
private val Vurgu   = Color(0xFF8B5CF6)
private val Vurgu2  = Color(0xFF22D3EE)

/** Şerit yüksekliği SABİT: iki aşamalı yüklemede posterler sonradan gelince
 *  yerleşim kaymasın diye yer baştan ayrılır. */
private val SeritYuksekligi = 50.dp
private val YayYuksekligi   = 42.dp

@Composable
private fun MiniEkran() {
    val kapsam = rememberCoroutineScope()
    val baglam = LocalContext.current
    var durum by remember { mutableStateOf("") }
    var ogeler by remember { mutableStateOf<List<KatalogOgesi>>(emptyList()) }
    var yukleniyor by remember { mutableStateOf(true) }
    // Yeniden deneme sayaci: sunucu bulunamadiginda ekrana dokunmak
    // adres aramasini sifirdan baslatir (LaunchedEffect anahtari).
    var tekrar by remember { mutableStateOf(0) }
    // Poster yayındaki kesirli seçim: 2.3 gibi bir değer 2. ve 3. öge arasında
    // yumuşak geçiş üretir, halka her adımda zıplatmaz.
    var posterSecim by remember { mutableStateOf(0f) }
    // Bölüm seçimi açık mı: dolu ise ekran bölüm listesine döner.
    var seciliDizi by remember { mutableStateOf<KatalogOgesi?>(null) }
    var seciliBolumler by remember { mutableStateOf<List<BolumOgesi>>(emptyList()) }
    var aramaAcik by remember { mutableStateOf(false) }
    val halkaOdak = remember { FocusRequester() }
    // Kendi kendini guncelleme: evaitecOTA bileklikte APK kuramiyordu.
    var guncelleme by remember { mutableStateOf<Guncelleme.Bilgi?>(null) }
    var guncelDurum by remember { mutableStateOf("") }

    fun titre() {
        runCatching {
            val v = baglam.getSystemService(Vibrator::class.java)
            v?.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    fun komut(govde: String) {
        titre()
        kapsam.launch(Dispatchers.IO) {
            val ok = Sunucu.post("/api/v1/remote/command", govde)
            // Adres olmus olabilir (tunel kopmasi, baska agla baglanma): hatirlanani
            // unut ki sonraki istek sunucuyu yeniden arasin.
            if (!ok) Sunucu.unut()
            withContext(Dispatchers.Main) { durum = if (ok) "" else "sunucuya ulaşılamadı" }
        }
    }

    fun gonder(oge: KatalogOgesi, bolum: Int) {
        titre()
        kapsam.launch(Dispatchers.IO) {
            fun kacis(d: String) = URLEncoder.encode(d, "UTF-8")
            val sorgu = "plugin=${kacis(oge.plugin)}&url=${kacis(oge.url)}" +
                "&title=${kacis(oge.title)}&poster=${kacis(oge.poster)}&episode=$bolum"
            val ok = Sunucu.post("/api/v1/remote/play?$sorgu")
            withContext(Dispatchers.Main) { durum = if (ok) "📺 ${oge.title}" else "gönderilemedi" }
        }
    }

    // Poster'a dokununca: dizi ise bölüm listesi açılır, film ise doğrudan gider.
    // Bölüm sırası TV'ye taşınır (`episode`), yoksa TV 1. bölümü açıyordu.
    fun oynat(oge: KatalogOgesi) {
        titre()
        aramaAcik = false
        kapsam.launch(Dispatchers.IO) {
            val yanit = Sunucu.get("/api/v1/load_item?plugin=" + URLEncoder.encode(oge.plugin, "UTF-8") +
                "&encoded_url=" + oge.url)
            val bolumler = yanit
                ?.let { runCatching { Sunucu.json.decodeFromString<BilgiYaniti>(it).result?.episodes }.getOrNull() }
                .orEmpty()
            withContext(Dispatchers.Main) {
                if (bolumler.isEmpty()) gonder(oge, -1) else { seciliDizi = oge; seciliBolumler = bolumler }
            }
        }
    }

    LaunchedEffect(Unit) { runCatching { halkaOdak.requestFocus() } }

    // Acilista bir kez sorulur: sunucu zaten adres aramasi yapiyor, bu istek
    // onun ardina takilir. Bulunmazsa satir hic cizilmez.
    LaunchedEffect(Unit) {
        val bilgi = withContext(Dispatchers.IO) { runCatching { Guncelleme.kontrol() }.getOrNull() }
        guncelleme = bilgi
    }

    fun guncelle() {
        val bilgi = guncelleme ?: return
        titre()
        if (!Guncelleme.kurabilirMi(baglam)) {
            // Izin yokken kurulum sessizce reddediliyor: APK iniyor, hicbir sey
            // olmuyor. Once izin ekrani, sonra tekrar dokunus.
            runCatching { Guncelleme.izinEkrani(baglam) }
            guncelDurum = "izin ver, tekrar dokun"
            return
        }
        Guncelleme.sonDurum = ""
        guncelDurum = "indiriliyor…"
        kapsam.launch(Dispatchers.IO) {
            val sonuc = runCatching { Guncelleme.kur(baglam, Guncelleme.indir(baglam, bilgi)) }
            withContext(Dispatchers.Main) {
                guncelDurum = sonuc.fold({ "kuruluyor…" }, { "olmadı: ${it.message ?: "bilinmeyen"}" })
            }
        }
    }

    LaunchedEffect(tekrar) {
        if (ogeler.isNotEmpty()) return@LaunchedEffect   // alt ekrandan dönüldü
        yukleniyor = true
        // Ev sunucusu LAN'da: Wi-Fi uyanmadan adres aramanın anlamı yok.
        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { devam ->
            WifiKoprusu.uyandir(baglam) { if (devam.isActive) devam.resumeWith(Result.success(Unit)) }
        }
        // 1. aşama: Devam Et — sunucunun yerel kaydı, milisaniyeler içinde gelir.
        val devam = withContext(Dispatchers.IO) {
            Sunucu.get("/api/v1/continue_watching")
                ?.let { runCatching { Sunucu.json.decodeFromString<IzlemeYaniti>(it).result }.getOrNull() }
                ?.map { KatalogOgesi(it.plugin, it.contentUrl, it.title, it.poster) }
                .orEmpty()
        }
        ogeler = devam.filter { it.url.isNotBlank() }
        if (ogeler.isNotEmpty()) yukleniyor = false

        // 2. aşama: Yeni Çıkanlar — sunucu cache'i sıcaksa anında gelir.
        val yeni = withContext(Dispatchers.IO) {
            Sunucu.get("/api/v1/aggregate_new?type=movie")
                ?.let { runCatching { Sunucu.json.decodeFromString<KatalogYaniti>(it).result.items }.getOrNull() }
                .orEmpty()
        }
        yukleniyor = false
        ogeler = (ogeler + yeni).filter { it.url.isNotBlank() }.distinctBy { it.title.lowercase() }.take(20)
        if (ogeler.isEmpty()) durum = "sunucu bulunamadı"
    }

    val dizi = seciliDizi
    if (dizi != null) {
        BolumListesi(
            baslik   = dizi.title,
            bolumler = seciliBolumler,
            onSec    = { sira -> seciliDizi = null; gonder(dizi, sira) },
            onKapat  = { seciliDizi = null },
        )
        return
    }

    if (aramaAcik) {
        AramaEkrani(
            onSec   = { oynat(it) },
            onKapat = { aramaAcik = false },
            onDurum = { durum = it },
        )
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Zemin)
            // Halkanın TEK işi kaldı: şeritte gezinmek. Üç konumlu kip düğmesi
            // (gezinme/sarma/ses) kalktı — sarma ve ses artık alttaki kendi
            // yayında, hangi kipte olunduğunu akılda tutmak gerekmiyor.
            .onRotaryScrollEvent { olay ->
                if (ogeler.isNotEmpty()) {
                    val adimPiksel = 26f // bir öge ilerlemek için gereken piksel
                    posterSecim = (posterSecim + olay.verticalScrollPixels / adimPiksel)
                        .coerceIn(0f, (ogeler.size - 1).toFloat())
                }
                true
            }
            .focusRequester(halkaOdak)
            .focusable()
            // Ekranın ORTASI dokunmatik yüzey: kaydır = yön, dokun = OK. Şerit ve
            // sarma yayı kendi sürüklemelerini yutar, buraya hiç düşmez.
            .pointerInput(Unit) {
                detectTapGestures {
                    if (ogeler.isNotEmpty()) {
                        oynat(ogeler[posterSecim.roundToInt().coerceIn(0, ogeler.size - 1)])
                    } else {
                        komut("""{"type":"key","key":"CENTER"}""")
                    }
                }
            }
            .pointerInput(Unit) {
                var dx = 0f
                var dy = 0f
                detectDragGestures(
                    onDragStart = { dx = 0f; dy = 0f },
                    onDragEnd = {
                        val yon = when {
                            kotlin.math.abs(dx) < 24f && kotlin.math.abs(dy) < 24f -> null
                            kotlin.math.abs(dx) > kotlin.math.abs(dy) -> if (dx > 0) "RIGHT" else "LEFT"
                            else -> if (dy > 0) "DOWN" else "UP"
                        }
                        if (yon != null) komut("""{"type":"key","key":"$yon"}""")
                    },
                ) { _, sur -> dx += sur.x; dy += sur.y }
            },
    ) {
        // Yükleme göstergesi kadranın KENARINDA: orta alan şeridin, kenar çerçevenin.
        if (yukleniyor) CerceveHalkasi()

        Column(
            Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            // ŞERİT EN ÜSTTE, yüksekliği SABİT. Eskiden liste boşken düğmeler
            // kadranın ortasında duruyor, posterler ikinci aşamada gelince her şey
            // aşağı kayıp üst üste biniyordu (Dean: "açarken ortada düğmeler, sonra
            // aşağı kayıyor, liste üstüne geliyor bozuyor"). Yer baştan ayrılırsa
            // yerleşim hiç oynamaz; şerit de inceldi (100dp -> 50dp).
            Box(
                Modifier.fillMaxWidth().height(SeritYuksekligi),
                contentAlignment = Alignment.Center,
            ) {
                if (ogeler.isEmpty()) {
                    // Sunucu bulunamayınca ekran sonsuza kadar "yükleniyor" kalıyordu:
                    // durum satırı altta yazıyordu ama buradaki metin değişmiyordu.
                    Text(
                        text = if (yukleniyor) "yükleniyor…" else "sunucu yok — dokun, dene",
                        color = Soluk,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .clickable(enabled = !yukleniyor) { Sunucu.unut(); tekrar++ },
                    )
                } else {
                    PosterYayi(ogeler, posterSecim) { i -> posterSecim = i.toFloat(); oynat(ogeler[i]) }
                }
            }

            Text(
                text = durum.ifBlank {
                    ogeler.getOrNull(posterSecim.roundToInt())?.title ?: "halka: gezin · dokun: aç"
                },
                color = Soluk,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            // Beş düğme kadranda kalabalıktı (Dean: "bu kadar fazla gerek yok,
            // uzun basma ekleriz"). İkiye indi, ikinci işler uzun basışta:
            //   oynat/duraklat  dokun = play_pause · UZUN BAS = GERİ
            //   menü            dokun = televizyon ana ekranı · UZUN BAS = sesli arama
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            ) {
                YuvarlakDugme(
                    yazi   = "⏯",
                    boyut  = 46.dp,
                    onUzun = { komut("""{"type":"key","key":"BACK"}""") },
                ) { komut("""{"type":"transport","action":"play_pause","value":0}""") }

                YuvarlakDugme(
                    yazi   = "☰",
                    boyut  = 46.dp,
                    renk   = Soluk,
                    onUzun = { titre(); aramaAcik = true },
                ) { komut("""{"type":"nav","screen":"home"}""") }
            }

            SarmaYayi(
                onSarma = { sn -> komut("""{"type":"transport","action":"seek","value":$sn}""") },
                onSes   = { yon -> komut("""{"type":"transport","action":"volume","value":$yon}""") },
            )

            // Güncelleme şeridi EN ALTTA: ağ cevabı geç geldiğinde beliren bu satır
            // en üstteyken altındaki her şeyi aşağı itiyordu.
            guncelleme?.let { bilgi ->
                Text(
                    // Kurulum alıcısından gelen sonuç yerel durumu EZER: "kuruluyor…"
                    // yazarken kurulum reddedilirse ekran yalan söylemesin.
                    text = Guncelleme.sonDurum.ifBlank { guncelDurum.ifBlank { "⬆ ${bilgi.surum} güncelle" } },
                    color = Vurgu,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Kart)
                        .clickable { guncelle() }
                        .padding(vertical = 3.dp),
                )
            }
        }
    }
}

/**
 * Alt kenarda yarım daire şerit — sarmanın ve sesin KENDİ alanı.
 *
 * Eskiden sarma halkanın bir kipiydi ve ekranın tamamı sürüklemeyi yön tuşuna
 * çeviriyordu: listede gezinmek için parmağı gezdirince televizyon ileri geri
 * sarıyordu (Dean). Sarma buraya taşındı; bu kutu sürüklemeyi yutar, üstteki
 * yön-tuşu yüzeyine hiç düşmez.
 *
 * Yatay sürükleme = ±10 sn (her 30 piksel bir adım, parmak sürerken birikir),
 * dikey sürükleme = ses. Etiket sürüklerken ne gönderildiğini yazar.
 */
@Composable
private fun SarmaYayi(onSarma: (Int) -> Unit, onSes: (Int) -> Unit) {
    var etiket by remember { mutableStateOf("") }
    Box(
        Modifier
            .fillMaxWidth()
            .height(YayYuksekligi)
            .pointerInput(Unit) {
                var dx = 0f
                var dy = 0f
                var adim = 0
                detectDragGestures(
                    onDragStart = { dx = 0f; dy = 0f; adim = 0 },
                    onDragEnd = { etiket = "" },
                    onDragCancel = { etiket = "" },
                ) { degisim, sur ->
                    // Sürükleme burada TÜKETİLİR: altındaki yön-tuşu yüzeyi görmesin.
                    degisim.consume()
                    dx += sur.x
                    dy += sur.y
                    val yatay = kotlin.math.abs(dx) > kotlin.math.abs(dy)
                    val ham = if (yatay) dx / 30f else -dy / 30f
                    val yeni = ham.toInt()
                    if (yeni != adim) {
                        val fark = yeni - adim
                        adim = yeni
                        if (yatay) {
                            onSarma(fark * 10)
                            etiket = (if (adim >= 0) "+" else "") + "${adim * 10} sn"
                        } else {
                            onSes(if (fark > 0) 1 else -1)
                            etiket = if (adim >= 0) "ses +" else "ses -"
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Görsel: kadranın alt kavisini izleyen yarım daire — yuvarlak düğmelerle
        // aynı dil, ama tek parça bir "şerit" olduğu bakınca anlaşılıyor.
        Canvas(Modifier.fillMaxSize()) {
            val kalin = 3.dp.toPx()
            val yaricap = size.width / 2f
            val ustKose = Offset(0f, size.height - yaricap * 2f)
            val yayBoyu = Size(size.width, yaricap * 2f)
            drawArc(
                color      = Kart,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter  = false,
                topLeft    = ustKose,
                size       = yayBoyu,
                style      = Stroke(width = kalin * 3f),
            )
            drawArc(
                color      = Vurgu2,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter  = false,
                topLeft    = ustKose,
                size       = yayBoyu,
                style      = Stroke(width = kalin),
            )
        }
        Text(
            text = etiket.ifBlank { "sarma · ses" },
            color = if (etiket.isBlank()) Soluk else Metin,
            fontSize = 11.sp,
            fontWeight = if (etiket.isBlank()) FontWeight.Normal else FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * Yuvarlak kadrana oturan liste. Düz `LazyColumn` dikdörtgen çiziyordu: en üst ve
 * en alttaki satırlar kadranın kavisinde kesiliyor, okunmuyordu (Dean, 16 Eylül).
 * `ScalingLazyColumn` satırları kenarlara doğru küçültüp içeri çeker — liste
 * kadranın yayını takip eder, orta satır tam boy kalır.
 *
 * Halka (döner çerçeve) listeyi kaydırır: bu ekranlarda halkanın TV'ye komut
 * göndermesi anlamsız, parmakla kaydırmak da küçük ekranda satırı kaçırtıyor.
 * Odak burada istenir — `focusable()` olmadan çerçeve olayı hiç gelmez.
 */
@Composable
private fun HalkaListesi(
    modifier: Modifier = Modifier,
    icerik: androidx.wear.compose.foundation.lazy.ScalingLazyListScope.() -> Unit,
) {
    val durum: ScalingLazyListState = rememberScalingLazyListState()
    val odak = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { odak.requestFocus() } }

    ScalingLazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .onRotaryScrollEvent { olay ->
                durum.dispatchRawDelta(olay.verticalScrollPixels)
                true
            }
            .focusRequester(odak)
            .focusable(),
        state = durum,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = icerik,
    )
}

/** Liste satırı: tek dokunuşluk kart. İki listede de aynı görünsün diye tek yerde.
 *  `simge`: satırın sağında küçük bir işaret (ör. sesli arama sonuçlarında "📺" —
 *  dokununca TV'de açıldığı görünsün diye, Dean: "telefondaki gibi ekrana gönder").
 *  Bölüm listesinde kullanılmıyor, `null` kalır. */
@Composable
private fun ListeSatiri(yazi: String, simge: String? = null, onSec: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Kart)
            .clickable { onSec() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = yazi,
                color = Metin,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (simge != null) Text(simge, color = Vurgu2, fontSize = 11.sp)
        }
    }
}

@Composable
private fun YuvarlakDugme(
    yazi: String,
    boyut: Dp,
    renk: Color = Metin,
    /** İkinci iş: uzun basış. Kadranda düğme sayısını beşten ikiye indirdi. */
    onUzun: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(boyut)
            .clip(CircleShape)
            .background(Kart)
            .then(
                if (onUzun == null) Modifier.clickable(onClick = onClick)
                else Modifier.pointerInput(onUzun, onClick) {
                    detectTapGestures(onLongPress = { onUzun() }, onTap = { onClick() })
                }
            ),
        contentAlignment = Alignment.Center,
    ) { Text(yazi, color = renk, fontSize = 14.sp) }
}

/** Kadran kenarında iç içe iki yay, ters yönlerde döner — web'deki `.wp-spinner`
 *  deseninin saat hâli. Ortayı boş bırakır: içerik altında görünmeye devam eder. */
@Composable
private fun CerceveHalkasi() {
    val gecis = rememberInfiniteTransition(label = "halka")
    val dis by gecis.animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label         = "dis",
    )
    val ic by gecis.animateFloat(
        initialValue  = 360f,
        targetValue   = 0f,
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart),
        label         = "ic",
    )

    Canvas(Modifier.fillMaxSize()) {
        val kalin = size.minDimension * 0.012f

        fun yay(iceri: Float, baslangic: Float, renk: Color) {
            drawArc(
                color      = renk,
                startAngle = baslangic,
                sweepAngle = 90f,
                useCenter  = false,
                topLeft    = Offset(iceri, iceri),
                size       = Size(size.width - iceri * 2, size.height - iceri * 2),
                style      = Stroke(width = kalin),
            )
        }

        yay(kalin, dis, Vurgu)
        yay(kalin * 5f, ic, Vurgu2)
    }
}

/**
 * Posterleri kadranın üst yayında dizer — Samsung'un renk seçicisindeki döner
 * halka hissi: her öge kadranın çevresinde bir açıya oturur, `secim` (halkayla
 * sürülen kesirli indeks) kayınca öğeler yay boyunca kayar; merkeze (en tepeye)
 * gelen büyür ve öne çıkar (Dean: "dönerken büyüyüp seçilen daha öne çıkacak").
 *
 * Merkez noktası görünen kutunun ALTINDA, yarıçap kutu genişliğinden büyük
 * tutulur: 100dp'lik pencere, dev bir çemberin sadece tepe dilimini gösterir,
 * bu da uçlara doğru hafif aşağı kavisi (yay) verir.
 *
 * ponytail: yarıçap/açı sabitleri gerçek cihazda kalibre edilmedi, gözle
 * ayarlandı — Galaxy Watch'ta kavis abartılı/yetersiz görünürse `yaricapKat`
 * ve `aciAdimi`yi buradan ayarla.
 */
@Composable
private fun PosterYayi(ogeler: List<KatalogOgesi>, secim: Float, onClick: (Int) -> Unit) {
    val yogunluk = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val genislikPx = with(yogunluk) { maxWidth.toPx() }
        val yukseklikPx = with(yogunluk) { maxHeight.toPx() }
        val yaricapKat = 2.2f   // ince şeritte yay daha yayvan olmalı
        val yaricap = genislikPx * yaricapKat
        val merkezX = genislikPx / 2f
        val merkezY = yukseklikPx + yaricap - yukseklikPx * 0.15f
        val aciAdimi = 5f  // derece — komşu poster arası açı
        val yaricapAcikGorunen = 3f // bu değerden uzak ögeler çizilmez

        ogeler.forEachIndexed { i, oge ->
            val uzaklik = i - secim
            if (kotlin.math.abs(uzaklik) > yaricapAcikGorunen) return@forEachIndexed

            val aci = ((-90f + uzaklik * aciAdimi) * (kotlin.math.PI / 180f)).toFloat()
            val x = merkezX + yaricap * kotlin.math.cos(aci)
            val y = merkezY + yaricap * kotlin.math.sin(aci)
            // Merkeze yakınlık: 1 = tam seçili, 0 = kenarda kaybolan.
            val yakinlik = (1f - kotlin.math.abs(uzaklik) / yaricapAcikGorunen).coerceIn(0f, 1f)
            val olcek = 0.55f + 0.5f * yakinlik

            Box(
                Modifier
                    .offset(with(yogunluk) { (x - 18f).toDp() }, with(yogunluk) { (y - 18f).toDp() })
                    .graphicsLayer { scaleX = olcek; scaleY = olcek; alpha = 0.3f + 0.7f * yakinlik }
                    // Büyüyen (yakın) poster diğerlerinin üstünde çizilsin, kesişmesin.
                    .zIndex(yakinlik),
            ) {
                PosterDairesi(oge) { onClick(i) }
            }
        }
    }
}

@Composable
private fun PosterDairesi(oge: KatalogOgesi, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Kart)
                .clickable(onClick = onClick),
        ) {
            AsyncImage(
                model = Sunucu.taban() + "/proxy/image?url=" +
                    URLEncoder.encode(oge.poster, "UTF-8") + "&title=" + URLEncoder.encode(oge.title, "UTF-8"),
                contentDescription = oge.title,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        }
        Text(
            text = oge.title,
            color = Soluk,
            fontSize = 8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.size(width = 44.dp, height = 11.dp),
            textAlign = TextAlign.Center,
        )
    }
}

/** JSON gövdesine gömülecek metin. Konuşma tanıması tırnak da üretebiliyor;
 *  kaçırılmazsa gövde bozulur ve uç 400 döner. */
private fun jsonKacis(metin: String): String =
    metin.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")

// Sesli kumanda — saatte klavye işkence. Tanıma SAATİN kendi motoruyla yapılır
// (`RecognizerIntent`), çıkan metin sunucudaki `/voice` ucuna gider: Gemini
// cümleyi niyete çevirir. "inception aç" arama olur, "sesi kıs" / "10 saniye
// geri al" / "takip listem" doğrudan televizyona gider (uç kendi kuyruğa yazar).
//
// Gemini yoksa (anahtar girilmemiş, 503) ya da ulaşılamıyorsa düz aramaya
// düşülür: sesli komut çalışmasa bile arama çalışmaya devam etsin.
@Composable
private fun AramaEkrani(onSec: (KatalogOgesi) -> Unit, onKapat: () -> Unit, onDurum: (String) -> Unit) {
    val kapsam = rememberCoroutineScope()
    var sorgu by remember { mutableStateOf("") }
    var sonuclar by remember { mutableStateOf<List<KatalogOgesi>>(emptyList()) }
    var araniyor by remember { mutableStateOf(false) }

    fun soyle(metin: String) {
        sorgu    = metin
        araniyor = true
        sonuclar = emptyList()
        kapsam.launch(Dispatchers.IO) {
            val niyet = Sunucu.postAl("/api/v1/voice", """{"text":"${jsonKacis(metin)}"}""")
                ?.let { runCatching { Sunucu.json.decodeFromString<SesYaniti>(it).result }.getOrNull() }

            // Komut niyetini uç zaten TV'ye yolladı; saatin yapacağı bir şey yok.
            if (niyet != null && niyet.sent) {
                withContext(Dispatchers.Main) {
                    araniyor = false
                    onDurum(niyet.reply?.takeIf { it.isNotBlank() } ?: "📺 gönderildi")
                    onKapat()
                }
                return@launch
            }

            val aranan  = niyet?.query?.takeIf { it.isNotBlank() } ?: metin
            val bulunan = Sunucu.get("/api/v1/search_all?query=" + URLEncoder.encode(aranan, "UTF-8"))
                ?.let { runCatching { Sunucu.json.decodeFromString<AramaYaniti>(it).result }.getOrNull() }
                .orEmpty()
            withContext(Dispatchers.Main) {
                sorgu    = aranan
                sonuclar = bulunan
                araniyor = false
            }
        }
    }

    val mikrofon = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { sonuc ->
        if (sonuc.resultCode == Activity.RESULT_OK) {
            val metin = sonuc.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (metin.isNotBlank()) soyle(metin)
        }
    }

    fun dinle() {
        val niyet = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Ne izlemek istiyorsun?")
        }
        runCatching { mikrofon.launch(niyet) }
    }

    // Ekran açılır açılmaz mikrofon: aramaya girmenin tek sebebi konuşmak.
    LaunchedEffect(Unit) { dinle() }

    Box(Modifier.fillMaxSize().background(Zemin)) {
        if (araniyor) CerceveHalkasi()

        Column(
            Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = sorgu.ifBlank { "🎙 söyle: ara, sar, ses, ekran" },
                color = if (sorgu.isBlank()) Soluk else Metin,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            HalkaListesi(Modifier.weight(1f)) {
                items(sonuclar.size) { i ->
                    val oge = sonuclar[i]
                    // "📺": satıra dokununca TV'de açılacağı önceden görünsün.
                    ListeSatiri(oge.title, simge = "📺") { onSec(oge) }
                }
                if (sonuclar.isEmpty() && !araniyor && sorgu.isNotBlank()) {
                    items(1) { Text("sonuç yok", color = Soluk, fontSize = 11.sp) }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                YuvarlakDugme("🎙", 44.dp, Vurgu) { dinle() }
                YuvarlakDugme("✕", 40.dp, Soluk) { onKapat() }
            }
        }
    }
}

// Bölüm listesi — poster'a dokununca dizi ise açılır. Ana ekranın "her yer
// dokunmatik yüzey" davranışı burada YOK: bu ekranda dokunuş bölüm seçer.
@Composable
private fun BolumListesi(
    baslik: String,
    bolumler: List<BolumOgesi>,
    onSec: (Int) -> Unit,
    onKapat: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Zemin).padding(horizontal = 10.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = baslik,
            color = Metin,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        HalkaListesi(Modifier.weight(1f)) {
            items(bolumler.size) { i ->
                val ep = bolumler[i]
                val numara = ep.episode?.let { "S${ep.season}B$it" } ?: "${i + 1}. Bölüm"
                val ad = ep.title?.takeIf { it.isNotBlank() }
                ListeSatiri(if (ad != null) "$numara · $ad" else numara) { onSec(i) }
            }
        }
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Kart)
                .clickable { onKapat() },
            contentAlignment = Alignment.Center,
        ) { Text("✕", color = Soluk, fontSize = 13.sp) }
    }
}

/**
 * Saatin Wi-Fi'sini uyandırır ve süreci o ağa bağlar.
 *
 * Wear OS pil için Wi-Fi radyosunu telefona Bluetooth ile bağlıyken KAPALI
 * tutuyor; ayar ekranına girildiğinde radyo uyanıyor ve o yüzden "girince
 * bağlanıyor" gibi görünüyor (Dean, 16 Eylül: "saat hep kapatıyor Wi-Fi'yi,
 * eğer girersem Wi-Fi içine bağlanıyor"). Bu sistemin tasarımı, kapatılamaz —
 * ama uygulama Wi-Fi TAŞIYICISINI açıkça isteyebilir: istek süresince sistem
 * radyoyu açık tutar. Ev sunucusu LAN'da olduğu için Bluetooth vekili işe
 * yaramaz, gereken tam da Wi-Fi'dir.
 *
 * İstek geri çağrısı BIRAKILMAZ: serbest bırakılırsa sistem radyoyu yeniden
 * uyutur ve sunucu bir sonraki istekte yine kaybolur. Uygulama kapanınca
 * süreçle birlikte düşer.
 */
object WifiKoprusu {

    @Volatile private var baglandi = false

    fun uyandir(context: Context, zamanAsimiMs: Int = 8_000, sonra: () -> Unit) {
        if (baglandi) { sonra(); return }
        val cm = context.getSystemService(ConnectivityManager::class.java)
        if (cm == null) { sonra(); return }

        // Zaten Wi-Fi üzerindeysek bekletme: her açılışta 8 sn beklemek pahalı.
        val mevcut = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        if (mevcut?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            baglandi = true
            sonra()
            return
        }

        val istek = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        var cevapVerildi = false
        val geriCagri = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                baglandi = true
                // Süreci bu ağa bağla: yoksa istekler varsayılan taşıyıcıdan
                // (Bluetooth vekili) çıkar ve ev sunucusuna hiç ulaşmaz.
                runCatching { cm.bindProcessToNetwork(network) }
                if (!cevapVerildi) { cevapVerildi = true; sonra() }
            }

            override fun onUnavailable() {
                if (!cevapVerildi) { cevapVerildi = true; sonra() }
            }

            override fun onLost(network: Network) {
                baglandi = false
                runCatching { cm.bindProcessToNetwork(null) }
            }
        }
        runCatching { cm.requestNetwork(istek, geriCagri, zamanAsimiMs) }
            .onFailure { if (!cevapVerildi) { cevapVerildi = true; sonra() } }
    }
}
