package com.evaitec.netmovies.tv.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.evaitec.netmovies.tv.data.MediaItem
import com.evaitec.netmovies.tv.data.Network
import com.evaitec.netmovies.tv.data.StreamLink

// Oynatma hızı seçenekleri (çark → Hız).
private val SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(item: MediaItem, onBack: () -> Unit) {
    val context = LocalContext.current
    // 10sn ileri/geri: Media3 transport kontrolündeki FF/RW butonları bu artışı gösterir.
    val exo = remember {
        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
    }

    var error by remember { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }
    var retryKey by remember { mutableIntStateOf(0) }

    // Çoklu kaynak: load_links birden çok sunucu/kalite döndürebilir → hepsini tut.
    var links by remember { mutableStateOf<List<StreamLink>>(emptyList()) }
    var currentLinkIndex by remember { mutableIntStateOf(0) }

    // Çark menüsü durumu + oynatıcı track/hız durumu.
    var showSettings by remember { mutableStateOf(false) }
    var tracks by remember { mutableStateOf<Tracks?>(null) }
    var speed by remember { mutableFloatStateOf(1.0f) }

    // Ayar menüsü açıksa Geri onu kapatsın; değilse ekrandan çık.
    BackHandler(enabled = true) {
        if (showSettings) showSettings = false else onBack()
    }

    // Player durum/hata/track dinleyicisi.
    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) ready = true
            }
            override fun onPlayerError(e: PlaybackException) {
                error = e.message ?: "Oynatma hatası (${e.errorCodeName})"
            }
            override fun onTracksChanged(t: Tracks) {
                tracks = t
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    // İçeriğin kaynak listesini bir kez çek (retry ile tazelenir).
    LaunchedEffect(item.url, retryKey) {
        error = null
        ready = false
        try {
            val resp = Network.api.loadLinks(item.plugin, item.url)
            if (resp.result.isEmpty()) {
                error = "Oynatılacak kaynak bulunamadı"
                return@LaunchedEffect
            }
            links = resp.result
            currentLinkIndex = 0
        } catch (e: Exception) {
            error = e.message ?: "Bağlantı hatası"
        }
    }

    // Seçili kaynağı hazırla (kaynak değişince yeniden oynatılır).
    LaunchedEffect(links, currentLinkIndex) {
        val link = links.getOrNull(currentLinkIndex) ?: return@LaunchedEffect
        if (link.url.isBlank()) {
            error = "Geçersiz kaynak"
            return@LaunchedEffect
        }
        try {
            ready = false
            error = null

            val headers = buildMap {
                if (link.referer.isNotBlank()) put("Referer", link.referer)
            }
            val ua = link.userAgent.ifBlank { "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_5)" }
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(ua)
                .setDefaultRequestProperties(headers)
                .setAllowCrossProtocolRedirects(true)

            val hls = HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(ExoMediaItem.fromUri(link.url))

            // Altyazıları yan-yükle (sideload) → çark → Altyazı listesinde çıkar.
            val subSources = link.subtitles
                .filter { it.url.isNotBlank() }
                .map { sub ->
                    val cfg = ExoMediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                        .setMimeType(guessSubtitleMime(sub.url))
                        .setLabel(sub.name.ifBlank { "Altyazı" })
                        .setLanguage(guessLang(sub.name))
                        .setSelectionFlags(0)
                        .build()
                    SingleSampleMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(cfg, C.TIME_UNSET)
                }

            val source =
                if (subSources.isEmpty()) hls
                else MergingMediaSource(hls, *subSources.toTypedArray())

            exo.setMediaSource(source)
            exo.prepare()
            exo.playWhenReady = true
            exo.setPlaybackSpeed(speed)
        } catch (e: Exception) {
            error = e.message ?: "Bağlantı hatası"
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exo
                    useController = true
                    keepScreenOn = true              // film oynarken ekran uykuya dalmasın
                    controllerShowTimeoutMs = 4000
                    setShowFastForwardButton(true)   // 10sn ileri
                    setShowRewindButton(true)        // 10sn geri
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowSubtitleButton(false)     // altyazı bizim çark menüsünde
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Çark (ayarlar) butonu — sağ üst, D-pad ile odaklanabilir.
        GearButton(
            onClick = { showSettings = true },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
        )

        if (showSettings) {
            SettingsPanel(
                links = links,
                currentLinkIndex = currentLinkIndex,
                tracks = tracks,
                speed = speed,
                onSelectSource = { idx -> currentLinkIndex = idx; showSettings = false },
                onSelectAudio = { group, trackIndex ->
                    exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex)))
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                        .build()
                },
                onSelectSubtitle = { group, trackIndex ->
                    if (group == null) {
                        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .build()
                    } else {
                        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex)))
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .build()
                    }
                },
                onSelectSpeed = { s -> speed = s; exo.setPlaybackSpeed(s) },
                onClose = { showSettings = false },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        when {
            error != null -> PlayerOverlay(
                title = "Hata",
                message = error!!,
                actionLabel = "Tekrar dene",
                onAction = { retryKey++ },
                onBack = onBack,
            )
            !ready && !showSettings -> Overlay("Yükleniyor…")
        }
    }
}

// .vtt / .srt uzantısından MIME tahmini (bilinmiyorsa VTT).
private fun guessSubtitleMime(url: String): String {
    val u = url.lowercase()
    return when {
        u.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
        u.endsWith(".ass") || u.endsWith(".ssa") -> MimeTypes.TEXT_SSA
        else -> MimeTypes.TEXT_VTT
    }
}

// Altyazı adından kaba dil kodu (track eşleştirme/etiket için).
private fun guessLang(name: String): String {
    val n = name.lowercase()
    return when {
        "türk" in n || "turk" in n || n.startsWith("tr") -> "tr"
        "ing" in n || "eng" in n || n.startsWith("en")   -> "en"
        else -> "und"
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GearButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xCC1A1726))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text("⚙  Ayarlar", color = Color(0xFFEDEDF2))
    }
}

@OptIn(UnstableApi::class, ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsPanel(
    links: List<StreamLink>,
    currentLinkIndex: Int,
    tracks: Tracks?,
    speed: Float,
    onSelectSource: (Int) -> Unit,
    onSelectAudio: (Tracks.Group, Int) -> Unit,
    onSelectSubtitle: (Tracks.Group?, Int) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val audioGroups = tracks?.groups?.filter { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 } ?: emptyList()
    val textGroups = tracks?.groups?.filter { it.type == C.TRACK_TYPE_TEXT && it.length > 0 } ?: emptyList()
    val textDisabled = textGroups.none { g -> (0 until g.length).any { g.isTrackSelected(it) } }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(320.dp)
            .background(Color(0xF20F0F14))
            .padding(20.dp),
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SectionTitle("Kaynak")
            if (links.isEmpty()) MutedRow("—")
            links.forEachIndexed { idx, link ->
                SettingRow(
                    label = link.name.ifBlank { "Kaynak ${idx + 1}" },
                    selected = idx == currentLinkIndex,
                    onClick = { onSelectSource(idx) },
                )
            }

            if (audioGroups.isNotEmpty()) {
                SectionTitle("Dil (Ses)")
                audioGroups.forEach { group ->
                    for (i in 0 until group.length) {
                        val fmt = group.getTrackFormat(i)
                        SettingRow(
                            label = fmt.label ?: fmt.language ?: "Ses ${i + 1}",
                            selected = group.isTrackSelected(i),
                            onClick = { onSelectAudio(group, i) },
                        )
                    }
                }
            }

            if (textGroups.isNotEmpty()) {
                SectionTitle("Altyazı")
                SettingRow(label = "Kapalı", selected = textDisabled, onClick = { onSelectSubtitle(null, 0) })
                textGroups.forEach { group ->
                    for (i in 0 until group.length) {
                        val fmt = group.getTrackFormat(i)
                        SettingRow(
                            label = fmt.label ?: fmt.language ?: "Altyazı ${i + 1}",
                            selected = group.isTrackSelected(i),
                            onClick = { onSelectSubtitle(group, i) },
                        )
                    }
                }
            }

            SectionTitle("Hız")
            SPEEDS.forEach { s ->
                SettingRow(
                    label = if (s == 1.0f) "Normal" else "${s}x",
                    selected = s == speed,
                    onClick = { onSelectSpeed(s) },
                )
            }

            androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
            SettingRow(label = "Kapat", selected = false, onClick = onClose)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = Color(0xFF8B5CF6),
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MutedRow(text: String) {
    Text(text, color = Color(0x99EDEDF2), modifier = Modifier.padding(vertical = 6.dp))
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color(0x338B5CF6) else Color(0x00000000))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = (if (selected) "● " else "   ") + label,
            color = if (selected) Color(0xFFEDEDF2) else Color(0xCCEDEDF2),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Overlay(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayerOverlay(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title)
            Text(message)
            ActionRow(onAction = onAction, actionLabel = actionLabel, onBack = onBack)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ActionRow(onAction: () -> Unit, actionLabel: String, onBack: () -> Unit) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TouchButton(actionLabel, onAction)
        TouchButton("Geri", onBack)
    }
}
