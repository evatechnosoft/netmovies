package com.evaitec.netmovies.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.evaitec.netmovies.tv.data.FollowedShow
import com.evaitec.netmovies.tv.data.MediaItem
import com.evaitec.netmovies.tv.data.Network
import com.evaitec.netmovies.tv.data.encodedUrl
import com.evaitec.netmovies.tv.ui.theme.NmColor
import com.evaitec.netmovies.tv.ui.theme.NmDim
import com.evaitec.netmovies.tv.ui.theme.NmType
import com.evaitec.netmovies.tv.ui.theme.nmFocusRing
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// Listem: takip edilen diziler + sonraki bölüm günü. Poster rafı DEĞİL liste —
// buradaki bilgi görsel değil takvim: "hangi gün, hangi bölüm".
// Türkçe ve yabancı ayrımı sunucuda TMDB `origin_country`'den gelir (kaynak sitenin
// kategorisi güvenilmez: aynı dizi farklı sitede farklı kategoride duruyor).

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FollowingScreen(onSelect: (MediaItem) -> Unit, onBack: () -> Unit) {
    var turkish by remember { mutableStateOf<List<FollowedShow>>(emptyList()) }
    var foreign by remember { mutableStateOf<List<FollowedShow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    BackHandler { onBack() }

    LaunchedEffect(Unit) {
        runCatching { Network.api.following().result }
            .onSuccess { turkish = it.turkish; foreign = it.foreign }
            .onFailure { error = it.message ?: "Liste alınamadı" }
        loading = false
    }

    val firstRow = remember { FocusRequester() }
    LaunchedEffect(turkish, foreign) {
        if (turkish.isNotEmpty() || foreign.isNotEmpty()) runCatching { firstRow.requestFocus() }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = NmDim.SafeH)) {
        Text(
            text = "📋 Listem — Takip Ettiklerim",
            fontWeight = FontWeight.Bold,
            fontSize = NmType.ScreenTitle,
            color = NmColor.OnSurface,
            modifier = Modifier.padding(top = NmDim.SafeV, bottom = 8.dp),
        )

        when {
            loading -> Center("Yükleniyor…")
            error != null -> Center(error!!)
            turkish.isEmpty() && foreign.isEmpty() -> Center(
                "Henüz takip ettiğin dizi yok — bir dizinin posterini uzun basıp \"Takip et\" de.",
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().focusGroup(),
                contentPadding = PaddingValues(bottom = NmDim.SafeV),
                verticalArrangement = Arrangement.spacedBy(NmDim.ItemGap),
            ) {
                if (turkish.isNotEmpty()) {
                    item { GroupTitle("Türkçe Diziler (${turkish.size})") }
                    itemsIndexedShows(turkish, firstRow, onSelect, firstFocusable = true)
                }
                if (foreign.isNotEmpty()) {
                    item { GroupTitle("Yabancı Diziler (${foreign.size})") }
                    itemsIndexedShows(foreign, firstRow, onSelect, firstFocusable = turkish.isEmpty())
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedShows(
    shows: List<FollowedShow>,
    firstRow: FocusRequester,
    onSelect: (MediaItem) -> Unit,
    firstFocusable: Boolean,
) {
    items(shows.size) { i ->
        ShowRow(
            show = shows[i],
            modifier = if (firstFocusable && i == 0) Modifier.focusRequester(firstRow) else Modifier,
            onClick = {
                onSelect(
                    MediaItem(
                        plugin = shows[i].plugin,
                        title = shows[i].title,
                        url = encodedUrl(shows[i].contentUrl),
                        poster = shows[i].poster.takeIf { p -> p.isNotBlank() },
                    )
                )
            },
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GroupTitle(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.SemiBold,
        fontSize = NmType.RowTitle,
        color = NmColor.Primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ShowRow(show: FollowedShow, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(NmDim.RowRadius)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) NmColor.SurfaceHigh else NmColor.Surface)
            .nmFocusRing(focused, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .width(46.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(6.dp))
                .background(NmColor.SurfaceHigh),
        ) {
            PosterImage(poster = show.poster.takeIf { it.isNotBlank() }, title = show.title)
        }
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = show.title,
                fontSize = NmType.Body,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
                color = NmColor.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = nextEpisodeLine(show),
                fontSize = NmType.Caption,
                color = if (show.nextDate.isNotBlank()) NmColor.Star else NmColor.OnSurfaceMuted,
            )
        }
    }
}

/**
 * "18 Eylül Cuma · S5B1 · 139. Bölüm" biçiminde takvim satırı.
 * Tarih yoksa dizinin durumu yazılır — boş satır bırakmak "bilgi yok"tan daha kötü,
 * kullanıcı eksik mi yoksa yeni bölüm mü yok ayırt edemiyor.
 */
private fun nextEpisodeLine(show: FollowedShow): String {
    if (show.nextDate.isBlank()) {
        return when (show.status) {
            "Ended", "Canceled" -> "Dizi bitti"
            "Returning Series" -> "Yeni sezon bekleniyor — tarih açıklanmadı"
            else -> "Yeni bölüm bilgisi yok"
        }
    }
    val date = runCatching { LocalDate.parse(show.nextDate) }.getOrNull()
        ?: return show.nextDate
    val tr = Locale("tr", "TR")
    val gun = date.dayOfWeek.getDisplayName(TextStyle.FULL, tr)
    val ay = date.month.getDisplayName(TextStyle.FULL, tr)
    val kalan = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), date)
    val kalanText = when {
        kalan == 0L -> " · BUGÜN"
        kalan == 1L -> " · yarın"
        kalan > 1L -> " · $kalan gün sonra"
        else -> ""
    }
    val bolum = if (show.nextSeason > 0 || show.nextEpisode > 0) {
        " · S${show.nextSeason}B${show.nextEpisode}"
    } else {
        ""
    }
    val ad = show.nextName.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
    return "${date.dayOfMonth} $ay $gun$kalanText$bolum$ad"
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Center(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, fontSize = NmType.Body, color = NmColor.OnSurfaceMuted)
    }
}
