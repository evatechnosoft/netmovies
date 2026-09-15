package com.evaitec.netmovies.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.evaitec.netmovies.tv.ui.theme.NmColor
import com.evaitec.netmovies.tv.ui.theme.NmDim
import com.evaitec.netmovies.tv.ui.theme.NmType
import com.evaitec.netmovies.tv.ui.theme.nmFocusRing

// Dakika tuş takımı: SOL ALTTA küçük kutu, telefon tuşu düzeninde 4x4.
// Önce tam ekrandı ve 0-9 tek sırada diziliydi: görüntüyü tamamen kapatıyordu
// (Dean: "koca ekran kaplıyor o sarma sayfası") ve tek sıra rakamda kumandayla
// 7'ye gitmek yedi basış demekti. Telefon düzeninde rakam iki adımda geliyor.
// Son sıra bölüm gezinmesi: 4x4'ün artan satırı boş durmasın.


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeekScreen(
    position: Long,
    duration: Long,
    /** null = dizi değil ya da o yönde bölüm yok. */
    prevEpisodeLabel: String?,
    nextEpisodeLabel: String?,
    onSeekTo: (Long) -> Unit,
    onPrevEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onOpenEpisodes: (() -> Unit)?,
    /** Canlı kanal mı — DVR tamponu var, "baştan izle" ve "canlıya dön" anlamlı. */
    canliYayin: Boolean = false,
    onTamponBasina: () -> Unit = {},
    onCanliyaDon: () -> Unit = {},
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

    fun git() {
        val minutes = minuteInput.toLongOrNull() ?: return
        val target = (minutes * 60_000L).coerceIn(0, if (duration > 0) duration else Long.MAX_VALUE)
        onSeekTo(target)
        minuteInput = ""
        onClose()
    }

    // Zemin karartması YOK: görüntü tuş takımının yanında açık kalır.
    Box(Modifier.fillMaxSize().padding(NmDim.SafeArea), contentAlignment = Alignment.BottomStart) {
        Column(
            modifier = Modifier
                .width(PAD_WIDTH)
                .clip(RoundedCornerShape(NmDim.PanelRadius))
                .background(NmColor.SurfaceDialog)
                .focusGroup()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Geçen · kalan · yazılan dakika — tek satır, tuş takımı kadar dar.
            val remaining = (duration - position).coerceAtLeast(0)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmtClock(position), fontSize = NmType.Caption, color = NmColor.OnSurfaceMuted)
                Text(
                    text = if (minuteInput.isEmpty()) "−${fmtClock(remaining)}" else "${minuteInput}. dk",
                    fontSize = NmType.Body,
                    fontWeight = FontWeight.Bold,
                    color = NmColor.Primary,
                )
                Text(fmtClock(duration), fontSize = NmType.Caption, color = NmColor.OnSurfaceMuted)
            }
            ProgressBar(position, duration)

            // Telefon tuşu düzeni, 4 sütun × 4 satır. Dördüncü sütun eylem
            // (sil / 0 / git), son satır bölüm gezinmesi.
            PadRow {
                Digit("1", Modifier.weight(1f).focusRequester(firstFocus)) { minuteInput = yaz(minuteInput, '1') }
                Digit("2", Modifier.weight(1f)) { minuteInput = yaz(minuteInput, '2') }
                Digit("3", Modifier.weight(1f)) { minuteInput = yaz(minuteInput, '3') }
                Digit("⌫", Modifier.weight(1f)) { minuteInput = minuteInput.dropLast(1) }
            }
            PadRow {
                Digit("4", Modifier.weight(1f)) { minuteInput = yaz(minuteInput, '4') }
                Digit("5", Modifier.weight(1f)) { minuteInput = yaz(minuteInput, '5') }
                Digit("6", Modifier.weight(1f)) { minuteInput = yaz(minuteInput, '6') }
                Digit("0", Modifier.weight(1f)) { minuteInput = yaz(minuteInput, '0') }
            }
            PadRow {
                Digit("7", Modifier.weight(1f)) { minuteInput = yaz(minuteInput, '7') }
                Digit("8", Modifier.weight(1f)) { minuteInput = yaz(minuteInput, '8') }
                Digit("9", Modifier.weight(1f)) { minuteInput = yaz(minuteInput, '9') }
                Digit("▶", Modifier.weight(1f), accent = true) { git() }
            }
            // Son satır: canlı yayında DVR uçları, kayıtta bölüm gezinmesi.
            PadRow {
                if (canliYayin) {
                    Digit("⏮", Modifier.weight(1f)) { onTamponBasina(); onClose() }
                    Digit("⏭", Modifier.weight(1f)) { onCanliyaDon(); onClose() }
                    Spacer(Modifier.weight(1f))
                } else {
                    Digit("⏮", Modifier.weight(1f), enabled = prevEpisodeLabel != null) { onPrevEpisode(); onClose() }
                    Digit("⏭", Modifier.weight(1f), enabled = nextEpisodeLabel != null) { onNextEpisode(); onClose() }
                    Digit("📑", Modifier.weight(1f), enabled = onOpenEpisodes != null) { onOpenEpisodes?.invoke(); onClose() }
                }
                Digit("✕", Modifier.weight(1f)) { onClose() }
            }

            // Hangi bölüme gidileceği tuşta yazmıyor: tek satır altta.
            (nextEpisodeLabel ?: prevEpisodeLabel)?.let {
                Text(
                    text = "⏭ $it",
                    fontSize = NmType.Caption,
                    color = NmColor.OnSurfaceFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Tuş takımı genişliği: 4 sütun rahat sığar, görüntünün çeyreğini geçmez. */
private val PAD_WIDTH = 300.dp

private fun yaz(mevcut: String, ch: Char): String =
    if (mevcut.length < 3) mevcut + ch else mevcut

@Composable
private fun PadRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
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

/**
 * Tuş takımı düğmesi. `enabled=false` → görünür ama odak almaz: 4x4 düzeni
 * bozulmasın diye kaldırılmıyor (filmde bölüm tuşu yoktur ama yeri durur).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Digit(
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(NmDim.RowRadius)
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .background(
                when {
                    focused -> NmColor.Primary
                    accent  -> NmColor.PrimarySelected
                    else    -> NmColor.Surface
                }
            )
            .nmFocusRing(focused, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = NmType.Body,
            fontWeight = FontWeight.Bold,
            color = when {
                focused -> NmColor.OnPrimary
                enabled -> NmColor.OnSurface
                else    -> NmColor.OnSurfaceFaint
            },
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
