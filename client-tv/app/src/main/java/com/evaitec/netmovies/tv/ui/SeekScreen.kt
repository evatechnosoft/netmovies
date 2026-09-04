package com.evaitec.netmovies.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import com.evaitec.netmovies.tv.data.EpisodeItem
import com.evaitec.netmovies.tv.ui.theme.NmColor
import com.evaitec.netmovies.tv.ui.theme.NmDim
import com.evaitec.netmovies.tv.ui.theme.NmType
import com.evaitec.netmovies.tv.ui.theme.nmFocusRing

// Gezinme ekranı: sarma, belirli dakikaya atlama, bölüm seçme — hepsi TAM EKRANDA.
// Oynatıcının üstündeki küçük yarı saydam katmanda bunlar okunmuyordu; kalan süre
// hiç yazmıyordu ve dakikaya atlamanın yolu yoktu (yalnız 10sn/60sn adımlar vardı).

private val JUMPS = listOf(
    -300_000L to "−5 dk",
    -60_000L to "−1 dk",
    -10_000L to "−10 sn",
    10_000L to "+10 sn",
    60_000L to "+1 dk",
    300_000L to "+5 dk",
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeekScreen(
    position: Long,
    duration: Long,
    episodes: List<EpisodeItem>,
    currentEpIndex: Int,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onSelectEpisode: (Int) -> Unit,
    onClose: () -> Unit,
) {
    var minuteInput by remember { mutableStateOf("") }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(10) {
            if (runCatching { firstFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            withFrameNanos { }
        }
    }
    BackHandler { onClose() }

    Box(Modifier.fillMaxSize().background(NmColor.Scrim), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .clip(RoundedCornerShape(NmDim.PanelRadius))
                .background(NmColor.SurfaceDialog)
                .focusGroup()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Geçen / kalan / toplam — üçü birden, kalan büyük ve vurgulu.
            val remaining = (duration - position).coerceAtLeast(0)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Geçen ${fmtClock(position)}", fontSize = NmType.Label, color = NmColor.OnSurfaceMuted)
                Text(
                    text = "Kalan ${fmtClock(remaining)}",
                    fontSize = NmType.ScreenTitle,
                    fontWeight = FontWeight.Bold,
                    color = NmColor.Primary,
                )
                Text("Toplam ${fmtClock(duration)}", fontSize = NmType.Label, color = NmColor.OnSurfaceMuted)
            }
            ProgressBar(position, duration)

            SectionLabel("Sarma")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                JUMPS.forEachIndexed { i, (delta, label) ->
                    Chip(
                        label = label,
                        modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    ) { onSeekBy(delta) }
                }
            }

            SectionLabel("Dakikaya git")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (minuteInput.isEmpty()) "—" else "${minuteInput}. dk",
                    fontSize = NmType.Body,
                    fontWeight = FontWeight.Bold,
                    color = NmColor.OnSurface,
                    modifier = Modifier.padding(end = 6.dp),
                )
                ('0'..'9').forEach { ch ->
                    Digit(ch.toString()) { if (minuteInput.length < 3) minuteInput += ch }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("⌫ Sil") { minuteInput = minuteInput.dropLast(1) }
                Chip("▶ Git") {
                    val minutes = minuteInput.toLongOrNull()
                    if (minutes != null) {
                        val target = (minutes * 60_000L).coerceIn(0, if (duration > 0) duration else Long.MAX_VALUE)
                        onSeekTo(target)
                        minuteInput = ""
                        onClose()
                    }
                }
            }

            if (episodes.isNotEmpty()) {
                SectionLabel("Bölümler (${episodes.size})")
                episodes.forEachIndexed { idx, ep ->
                    EpisodeRow(
                        label = ep.title ?: "Bölüm ${idx + 1}",
                        selected = idx == currentEpIndex,
                    ) { onSelectEpisode(idx); onClose() }
                }
            }

            Chip("✕ Kapat") { onClose() }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = NmType.Caption,
        fontWeight = FontWeight.SemiBold,
        color = NmColor.OnSurfaceFaint,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ProgressBar(position: Long, duration: Long) {
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(NmColor.TrackIdle),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(NmColor.Primary),
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Chip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(NmDim.PillRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (focused) NmColor.Primary else NmColor.SurfaceHigh)
            .nmFocusRing(focused, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            fontSize = NmType.Label,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            color = if (focused) NmColor.OnPrimary else NmColor.OnSurface,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Digit(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(NmDim.RowRadius)
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(shape)
            .background(if (focused) NmColor.Primary else NmColor.Surface)
            .nmFocusRing(focused, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = NmType.Body,
            fontWeight = FontWeight.Bold,
            color = if (focused) NmColor.OnPrimary else NmColor.OnSurface,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeRow(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(NmDim.RowRadius)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    focused -> NmColor.Primary
                    selected -> NmColor.PrimarySelected
                    else -> NmColor.Surface
                }
            )
            .nmFocusRing(focused, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = (if (selected) "● " else "") + label,
            fontSize = NmType.Label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (focused) NmColor.OnPrimary else NmColor.OnSurface,
        )
    }
}

/** sa:dd:ss (bir saatin altında dd:ss). */
private fun fmtClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
