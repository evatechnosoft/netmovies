package com.evaitec.netmovies.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.evaitec.netmovies.tv.data.AgendaDay
import com.evaitec.netmovies.tv.data.AgendaItem
import com.evaitec.netmovies.tv.data.Network
import com.evaitec.netmovies.tv.input.NmBackHandler
import com.evaitec.netmovies.tv.ui.theme.NmColor
import com.evaitec.netmovies.tv.ui.theme.NmDim
import com.evaitec.netmovies.tv.ui.theme.NmType
import com.evaitec.netmovies.tv.ui.theme.nmBottomScrim
import com.evaitec.netmovies.tv.ui.theme.nmFocusRingOnly
import com.evaitec.netmovies.tv.ui.theme.nmFocusScale
import com.evaitec.netmovies.tv.ui.theme.nmScale
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// Ajanda: yayınlanacak bölümler ve vizyona girecek filmler, gün gün.
//
// Bilerek WebView DEĞİL: yönetim paneli WebView'de D-pad ile gezilemiyordu ve
// bileşen her Android TV'de aynı davranmıyor (bkz. AdminScreen). Veri zaten
// `/api/v1/agenda`'da hazır — gruplama ve sıralama sunucuda, burada yalnız çizim.
//
// Yerleşim Gözat ile AYNI: gün başlığı tam satır, altında poster ızgarası.
// Liste biçimindeyken uygulamanın geri kalanından ayrı duruyordu (Dean:
// "gününde gösterim farklılığı, liste ve grid").

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AgendaScreen(onBack: () -> Unit, onAra: (String) -> Unit) {
    var gunler by remember { mutableStateOf<List<AgendaDay>>(emptyList()) }
    var toplam by remember { mutableStateOf(0) }
    // Üç adım: Bu Hafta · Bu Ay · Geçmiş. Geçmiş günler ana listede duruyordu ve
    // "bu hafta ne var" sorusunu kirletiyordu (Dean, 16 Eylül: "geçmiş adımına
    // alalım"). Veri tek turdan gelir, adım yalnız süzer — ek istek yok.
    var adim by remember { mutableStateOf(AjandaAdimi.HAFTA) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    NmBackHandler { onBack() }

    // Odak doğrudan İLK KARTA gider. Önceki sürümde dış Column `focusable()`
    // olduğu için odak orada takılı kalıyor, D-pad ızgaraya inemiyordu (Dean:
    // "listeye basamıyoruz, sadece en üsttekini seçiyor").
    //
    // İlk odak YALNIZ BİR KEZ istenir: her veri tazelemesinde istenirse ızgarada
    // aşağı inerken liste başa sıçrıyordu (Dean: "sistem kaydırdığı için
    // yeniliyor ve tekrar başa geçiyor").
    val ilkKart = remember { FocusRequester() }
    var odakVerildi by remember { mutableStateOf(false) }
    LaunchedEffect(gunler) {
        if (gunler.isNotEmpty() && !odakVerildi) {
            odakVerildi = true
            runCatching { ilkKart.requestFocus() }
        }
    }

    // Geçmiş adımı aylık turdan süzülür: haftalık yanıt yalnız bugün+7'ye kadar
    // geliyor, geçmiş günler ikisinde de aynı (bugün-7).
    LaunchedEffect(adim) {
        loading = true
        error = null
        val gorunum = if (adim == AjandaAdimi.HAFTA) "week" else "month"
        runCatching {
            if (adim == AjandaAdimi.IZLENMEMIS) Network.api.unwatched().result
            else Network.api.agenda(gorunum).result
        }
            .onSuccess { yanit ->
                val bugun = LocalDate.now().toString()
                // İzlemediklerim TEK grup gelir ve tarihe göre süzülmez: iki hafta
                // önce çıkmış ama izlenmemiş bölüm de listede kalmalı.
                val suzulmus = if (adim == AjandaAdimi.IZLENMEMIS) yanit.gunler else
                    yanit.gunler.filter {
                        if (adim == AjandaAdimi.GECMIS) it.tarih < bugun else it.tarih >= bugun
                    }
                gunler = suzulmus
                toplam = suzulmus.sumOf { it.ogeler.size }
            }
            .onFailure { error = it.message ?: "Ajanda alınamadı" }
        loading = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = NmDim.SafeH),
    ) {
        Text(
            text = "🗓  Ajanda — ${adim.baslik} ($toplam)",
            fontWeight = FontWeight.Bold,
            fontSize = NmType.ScreenTitle,
            color = NmColor.OnSurface,
            modifier = Modifier.padding(top = NmDim.SafeV, bottom = 4.dp),
        )
        // Aralık SAĞ/SOL tuşuyla değişiyordu: ızgarada satır sonuna gelince yatay
        // tuş aralığı değiştirip listeyi baştan yüklüyordu — kart seçmeye
        // çalışırken ekran altından kayıyordu. Artık iki düğme, ızgaradan ayrı.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 10.dp),
        ) {
            AjandaAdimi.entries.forEach { secenek ->
                AralikDugmesi(secenek.baslik, secili = adim == secenek) { adim = secenek }
            }
        }

        when {
            loading -> AjandaBos("Yükleniyor…")
            error != null -> AjandaBos(error!!)
            gunler.isEmpty() -> AjandaBos(
                if (adim == AjandaAdimi.GECMIS) "Son bir haftada yayınlanan yok."
                else "Bu aralıkta yayın yok."
            )
            else -> LazyVerticalGrid(
                modifier = Modifier.fillMaxSize().focusGroup(),
                // Poster ana sayfa rafından da küçük (110dp): ajanda bir takvim,
                // kart değil satır okunur — küçük poster satıra daha çok gün
                // sığdırıyor (Dean, 16 Eylül: "daha küçük, ana sayfa gibi").
                columns = GridCells.Adaptive(minSize = NmDim.AgendaPoster),
                contentPadding = PaddingValues(bottom = NmDim.SafeV),
                horizontalArrangement = Arrangement.spacedBy(NmDim.CardGap),
                verticalArrangement = Arrangement.spacedBy(NmDim.CardGap),
            ) {
                // Liste adıma göre zaten süzülü: ilk kart doğru kart.
                val odakGunu = gunler.first().tarih
                var odakVerilecek = true
                gunler.forEach { gun ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        GunBasligi(gun.tarih, gun.ogeler.size)
                    }
                    gun.ogeler.forEach { oge ->
                        val ilk = odakVerilecek && gun.tarih == odakGunu
                        if (ilk) odakVerilecek = false
                        item {
                            AjandaKarti(
                                oge = oge,
                                modifier = if (ilk) Modifier.focusRequester(ilkKart) else Modifier,
                                onAc = { onAra(oge.baslik) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Ajandanın üç adımı. Sıra ekrandaki düğme sırasıdır. */
private enum class AjandaAdimi(val baslik: String) {
    // Ajandanin kaynagi TMDB'nin POPULER takvimi; izlemediklerin ise kullanicinin
    // KENDI takip/favori listesinden turer ve gun gun bolunmez (Dean, 18 Eylul:
    // "gun belirtmeden tek listede bu ay diye belirtip izlemedigim bolumleri").
    IZLENMEMIS("İzlemediklerim"),
    HAFTA("Bu Hafta"),
    AY("Bu Ay"),
    GECMIS("Geçmiş"),
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AralikDugmesi(etiket: String, secili: Boolean, onSec: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(NmDim.CardRadius)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (secili) NmColor.Primary else NmColor.Surface)
            .nmFocusRingOnly(focused, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onSec() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = etiket,
            fontSize = NmType.Caption,
            fontWeight = if (secili) FontWeight.Bold else FontWeight.Normal,
            color = if (secili) NmColor.OnPrimary else NmColor.OnSurfaceMuted,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GunBasligi(tarih: String, adet: Int) {
    val bugun = LocalDate.now()
    val gun = runCatching { LocalDate.parse(tarih) }.getOrNull()
    val etiket = when {
        gun == null -> tarih
        gun == bugun -> "Bugün"
        gun == bugun.plusDays(1) -> "Yarın"
        gun == bugun.minusDays(1) -> "Dün"
        else -> "${gun.dayOfMonth} ${gun.month.getDisplayName(TextStyle.FULL, Locale("tr"))} · " +
            gun.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("tr"))
    }
    // Günü geçmiş satırlar listede KALIR (bölüm sağlayıcıya günler sonra
    // düşebiliyor), ama başlık soluk: göz "bugün"ü bir bakışta bulur.
    val gecmis = gun != null && gun < bugun

    Text(
        text = if (gecmis) "$etiket  ($adet)  · yayınlandı" else "$etiket  ($adet)",
        fontWeight = FontWeight.Bold,
        fontSize = NmType.RowTitle,
        color = when {
            gun == bugun -> NmColor.Primary
            gecmis       -> NmColor.OnSurfaceMuted
            else         -> NmColor.OnSurface
        },
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AjandaKarti(oge: AgendaItem, modifier: Modifier = Modifier, onAc: () -> Unit) {
    // Kart Gözat'taki posterle aynı ölçü ve odak davranışında. Ajanda TMDB
    // takviminden geliyor, öğede oynatma adresi YOK: OK başlığı Gözat'ın
    // aramasına düşürür, tek eşleşme varsa dizi doğrudan açılır.
    var focused by remember { mutableStateOf(false) }
    val scale = nmFocusScale(focused, NmDim.FocusScaleCard, label = "ajandaScale")
    val shape = RoundedCornerShape(NmDim.CardRadius)
    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .nmScale(scale)
            .zIndex(if (focused) 1f else 0f)
            .clip(shape)
            .background(NmColor.SurfaceHigh)
            .nmFocusRingOnly(focused, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onAc() },
    ) {
        PosterImage(poster = oge.poster, title = oge.baslik, modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(62.dp)
                .background(nmBottomScrim),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Text(
                text = oge.baslik,
                fontWeight = FontWeight.SemiBold,
                fontSize = NmType.Label,
                color = NmColor.OnSurface,
                maxLines = if (focused) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(oge.bolum)
                    if (oge.puan > 0) append(" · ★ ").append(oge.puan)
                },
                fontSize = NmType.Caption,
                color = NmColor.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AjandaBos(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, fontSize = NmType.Body, color = NmColor.OnSurfaceMuted)
    }
}
