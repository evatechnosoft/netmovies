package com.evaitec.netmovies.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.evaitec.netmovies.tv.data.AgendaDay
import com.evaitec.netmovies.tv.data.AgendaItem
import com.evaitec.netmovies.tv.data.Network
import com.evaitec.netmovies.tv.input.NmBackHandler
import com.evaitec.netmovies.tv.ui.theme.NmColor
import com.evaitec.netmovies.tv.ui.theme.NmDim
import com.evaitec.netmovies.tv.ui.theme.NmType
import android.view.KeyEvent
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// Ajanda: yayınlanacak bölümler ve vizyona girecek filmler, gün gün.
//
// Bilerek WebView DEĞİL: yönetim paneli WebView'de D-pad ile gezilemiyordu ve
// bileşen her Android TV'de aynı davranmıyor (bkz. AdminScreen). Veri zaten
// `/api/v1/agenda`'da hazır — gruplama ve sıralama sunucuda, burada yalnız çizim.
//
// Satırlar tıklanabilir değil: kayıtlar TMDB'den geliyor, katalogda karşılığı
// olmayabilir. "Ne zaman" sorusunu cevaplar, oynatma yolu Gözat'tan geçer.

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AgendaScreen(onBack: () -> Unit) {
    var gunler by remember { mutableStateOf<List<AgendaDay>>(emptyList()) }
    var toplam by remember { mutableStateOf(0) }
    var aylik by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    NmBackHandler { onBack() }

    // Aralık için ayrı bir odak hedefi açmak yerine yatay tuşlar kullanılır:
    // liste dikey kayıyor, SAĞ/SOL boşta duruyordu.
    val kok = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { kok.requestFocus() } }

    LaunchedEffect(aylik) {
        loading = true
        error = null
        runCatching { Network.api.agenda(if (aylik) "month" else "week").result }
            .onSuccess { gunler = it.gunler; toplam = it.toplam }
            .onFailure { error = it.message ?: "Ajanda alınamadı" }
        loading = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = NmDim.SafeH)
            .focusRequester(kok)
            .focusable()
            .onKeyEvent { ke: ComposeKeyEvent ->
                val kod = ke.nativeKeyEvent.keyCode
                val yatay = kod == KeyEvent.KEYCODE_DPAD_LEFT || kod == KeyEvent.KEYCODE_DPAD_RIGHT
                if (yatay && ke.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                    aylik = !aylik
                    true
                } else {
                    yatay
                }
            },
    ) {
        Text(
            text = if (aylik) "🗓  Ajanda — Bu Ay ($toplam)" else "🗓  Ajanda — Bu Hafta ($toplam)",
            fontWeight = FontWeight.Bold,
            fontSize = NmType.ScreenTitle,
            color = NmColor.OnSurface,
            modifier = Modifier.padding(top = NmDim.SafeV, bottom = 4.dp),
        )
        Text(
            // Aralık değiştirmek için ayrı bir odak hedefi açmak yerine, zaten elde
            // olan SAĞ/SOL tuşu kullanılır: liste dikey kayıyor, yatay boşta.
            text = "SAĞ/SOL: hafta ↔ ay",
            fontSize = NmType.Caption,
            color = NmColor.OnSurfaceMuted,
            modifier = Modifier.padding(bottom = 10.dp),
        )

        when {
            loading -> AjandaBos("Yükleniyor…")
            error != null -> AjandaBos(error!!)
            gunler.isEmpty() -> AjandaBos("Bu aralıkta yayın yok.")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().focusGroup(),
                contentPadding = PaddingValues(bottom = NmDim.SafeV),
                verticalArrangement = Arrangement.spacedBy(NmDim.ItemGap),
            ) {
                gunler.forEach { gun ->
                    item { GunBasligi(gun.tarih, gun.ogeler.size) }
                    items(gun.ogeler.size) { i -> AjandaSatiri(gun.ogeler[i]) }
                }
            }
        }
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
        else -> "${gun.dayOfMonth} ${gun.month.getDisplayName(TextStyle.FULL, Locale("tr"))} · " +
            gun.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("tr"))
    }

    Text(
        text = "$etiket  ($adet)",
        fontWeight = FontWeight.Bold,
        fontSize = NmType.RowTitle,
        color = if (gun == bugun) NmColor.Primary else NmColor.OnSurface,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AjandaSatiri(oge: AgendaItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NmDim.CardRadius))
            .background(NmColor.Surface)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(46.dp).height(68.dp).clip(RoundedCornerShape(6.dp))) {
            PosterImage(poster = oge.poster, title = oge.baslik, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = oge.baslik,
                fontWeight = FontWeight.SemiBold,
                fontSize = NmType.Label,
                color = NmColor.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(if (oge.tur == "film") "Film" else "Dizi")
                    append(" · ").append(oge.bolum)
                    if (oge.puan > 0) append(" · ★ ").append(oge.puan)
                },
                fontSize = NmType.Caption,
                color = NmColor.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (oge.ozet.isNotBlank()) {
                Text(
                    text = oge.ozet,
                    fontSize = NmType.Caption,
                    color = NmColor.OnSurfaceMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
