package com.evaitec.netmovies.wear

import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
// Altta tek düğme GERİ: bir basış duraklatır (en sık istenen), iki saniye içinde
// ikinci basış gerçek GERİ gönderir — `/mini` web sayfasındaki davranışın aynısı.

private val Zemin   = Color(0xFF0A0C10)
private val Kart    = Color(0xFF161A22)
private val Cizgi   = Color(0xFF252B36)
private val Metin   = Color(0xFFE8EAF0)
private val Soluk   = Color(0xFF8B93A7)
private val Vurgu   = Color(0xFF8B5CF6)

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
    var geriAn by remember { mutableStateOf(0L) }
    // Döner çerçeve (Galaxy Watch halkası) sarma için: yatay kaydırma zaten yön
    // tuşu, sarmaya ayrı bir hareket gerekiyordu (Dean: "geri sarmayı halkayla").
    var halkaBirikim by remember { mutableStateOf(0f) }
    // Bölüm seçimi açık mı: dolu ise ekran bölüm listesine döner.
    var seciliDizi by remember { mutableStateOf<KatalogOgesi?>(null) }
    var seciliBolumler by remember { mutableStateOf<List<BolumOgesi>>(emptyList()) }
    val halkaOdak = remember { FocusRequester() }

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
            withContext(Dispatchers.Main) { durum = if (ok) "" else "gönderilemedi" }
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

    LaunchedEffect(Unit) {
        if (ogeler.isNotEmpty()) return@LaunchedEffect   // bölüm ekranından dönüldü
        withContext(Dispatchers.IO) {
            // Devam Et önde (en olası niyet), arkasına Yeni Çıkanlar.
            val devam = Sunucu.get("/api/v1/continue_watching")
                ?.let { runCatching { Sunucu.json.decodeFromString<IzlemeYaniti>(it).result }.getOrNull() }
                ?.map { KatalogOgesi(it.plugin, it.contentUrl, it.title, it.poster) }
                .orEmpty()
            val yeni = Sunucu.get("/api/v1/aggregate_new?type=movie")
                ?.let { runCatching { Sunucu.json.decodeFromString<KatalogYaniti>(it).result.items }.getOrNull() }
                .orEmpty()

            val secilen = (devam + yeni).filter { it.url.isNotBlank() }.distinctBy { it.title.lowercase() }.take(20)
            withContext(Dispatchers.Main) {
                ogeler = secilen
                if (secilen.isEmpty()) durum = "sunucu bulunamadı"
            }
        }
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

    Box(
        Modifier
            .fillMaxSize()
            .background(Zemin)
            // Halka: her tam adımda 10 saniye. Küçük tıklar birikir, eşiği geçince
            // tek komut gider — her mikro harekette istek atmak sarmayı titretiyor.
            .onRotaryScrollEvent { olay ->
                halkaBirikim += olay.verticalScrollPixels
                val adim = 60f
                if (kotlin.math.abs(halkaBirikim) >= adim) {
                    val yon = if (halkaBirikim > 0) 10 else -10
                    halkaBirikim = 0f
                    komut("""{"type":"transport","action":"seek","value":$yon}""")
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
        Column(
            Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (ogeler.isEmpty()) {
                Text("yükleniyor…", color = Soluk, fontSize = 12.sp, modifier = Modifier.padding(top = 24.dp))
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
                text = durum.ifBlank { "kaydır: yön · dokun: OK" },
                color = Soluk,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            ) {
                // Ana menü: televizyonu ana ekrana döndürür — saatte gezinmek
                // yerine tek dokunuş (Dean: "ana menü").
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Kart)
                        .clickable { komut("""{"type":"nav","screen":"home"}""") },
                    contentAlignment = Alignment.Center,
                ) { Text("☰", color = Metin, fontSize = 15.sp) }

                Box(
                    Modifier
                        .size(52.dp)
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
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f).padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(bolumler.size) { i ->
                val ep = bolumler[i]
                val numara = ep.episode?.let { "S${ep.season}B$it" } ?: "${i + 1}. Bölüm"
                val ad = ep.title?.takeIf { it.isNotBlank() }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Kart)
                        .clickable { onSec(i) }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = if (ad != null) "$numara · $ad" else numara,
                        color = Metin,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
