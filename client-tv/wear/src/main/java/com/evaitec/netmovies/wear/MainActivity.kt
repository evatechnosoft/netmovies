package com.evaitec.netmovies.wear

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

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

/** Döner çerçevenin ne sürdüğü. Tek düğme ikisi arasında geçer — saatte ayrı
 *  ses ve sarma kontrolüne yer yok (Dean: "onu switch olur"). */
private enum class HalkaKipi { SARMA, SES }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MiniEkran() }
    }
}

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
    var geriAn by remember { mutableStateOf(0L) }
    var halkaBirikim by remember { mutableStateOf(0f) }
    var halkaKipi by remember { mutableStateOf(HalkaKipi.SARMA) }
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
            // Halka: her tam adımda tek komut. Küçük tıklar birikir, eşiği geçince
            // gider — her mikro harekette istek atmak sarmayı titretiyor.
            // Ne gönderdiği kipe bağlı: sarma (±10 sn) ya da ses (±1 kademe).
            .onRotaryScrollEvent { olay ->
                halkaBirikim += olay.verticalScrollPixels
                val adim = 60f
                if (kotlin.math.abs(halkaBirikim) >= adim) {
                    val ileri = halkaBirikim > 0
                    halkaBirikim = 0f
                    when (halkaKipi) {
                        HalkaKipi.SARMA -> komut(
                            """{"type":"transport","action":"seek","value":${if (ileri) 10 else -10}}"""
                        )
                        // TV tarafı yalnız işarete bakıyor (ADJUST_RAISE/LOWER).
                        HalkaKipi.SES -> komut(
                            """{"type":"transport","action":"volume","value":${if (ileri) 1 else -1}}"""
                        )
                    }
                }
                true
            }
            .focusRequester(halkaOdak)
            .focusable()
            // Ekranın tamamı dokunmatik yüzey: kaydır = yön, dokun = OK.
            .pointerInput(Unit) {
                detectTapGestures { komut("""{"type":"key","key":"CENTER"}""") }
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
        // Yükleme göstergesi kadranın KENARINDA: 47 mm Classic'te orta alan
        // posterlerin, kenar çerçevenin yeri. Web'deki iç içe iki halkanın
        // (`.wp-spinner`) saat karşılığı.
        if (yukleniyor) CerceveHalkasi()

        Column(
            Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Guncelleme seridi: yalniz daha yeni surum varken cizilir.
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

            if (ogeler.isEmpty()) {
                // Sunucu bulunamayinca ekran sonsuza kadar "yukleniyor" kaliyordu:
                // durum satiri altta yaziyordu ama buradaki metin degismiyordu.
                Text(
                    text = if (yukleniyor) "yükleniyor…" else "sunucuya ulaşılamadı — dokun, yeniden dene",
                    color = Soluk,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 24.dp, start = 16.dp, end = 16.dp)
                        .clickable(enabled = !yukleniyor) { Sunucu.unut(); tekrar++ },
                )
            } else {
                LazyRow(
                    Modifier.fillMaxWidth().height(72.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp),
                ) {
                    items(ogeler.size) { i -> PosterDairesi(ogeler[i]) { oynat(ogeler[i]) } }
                }
            }

            Text(
                text = durum.ifBlank {
                    if (halkaKipi == HalkaKipi.SARMA) "halka: sarma · dokun: OK" else "halka: ses · dokun: OK"
                },
                color = Soluk,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                // Ana menü: televizyonu ana ekrana döndürür — saatte gezinmek
                // yerine tek dokunuş (Dean: "ana menü").
                YuvarlakDugme("☰", 38.dp) { komut("""{"type":"nav","screen":"home"}""") }

                YuvarlakDugme("🎙", 38.dp) { titre(); aramaAcik = true }

                // Halka kipi anahtarı: sarma ⟷ ses.
                YuvarlakDugme(
                    yazi  = if (halkaKipi == HalkaKipi.SARMA) "⏩" else "🔊",
                    boyut = 38.dp,
                    renk  = Vurgu,
                ) {
                    titre()
                    halkaKipi = if (halkaKipi == HalkaKipi.SARMA) HalkaKipi.SES else HalkaKipi.SARMA
                    halkaBirikim = 0f
                }

                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Kart)
                        .clickable {
                            val simdi = System.currentTimeMillis()
                            if (simdi - geriAn < 2000) {
                                geriAn = 0
                                komut("""{"type":"key","key":"BACK"}""")
                            } else {
                                geriAn = simdi
                                komut("""{"type":"transport","action":"play_pause","value":0}""")
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (System.currentTimeMillis() - geriAn < 2000) "GERİ?" else "⏯",
                        color = if (System.currentTimeMillis() - geriAn < 2000) Vurgu else Metin,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
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

/** Liste satırı: tek dokunuşluk kart. İki listede de aynı görünsün diye tek yerde. */
@Composable
private fun ListeSatiri(yazi: String, onSec: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Kart)
            .clickable { onSec() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            text = yazi,
            color = Metin,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun YuvarlakDugme(yazi: String, boyut: Dp, renk: Color = Metin, onClick: () -> Unit) {
    Box(
        Modifier.size(boyut).clip(CircleShape).background(Kart).clickable(onClick = onClick),
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

@Composable
private fun PosterDairesi(oge: KatalogOgesi, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(52.dp)
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
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.size(width = 56.dp, height = 12.dp),
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
                    ListeSatiri(oge.title) { onSec(oge) }
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
