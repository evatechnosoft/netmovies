package com.evaitec.netmovies.tv.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.evaitec.netmovies.tv.data.MediaItem
import com.evaitec.netmovies.tv.data.Network

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(item: MediaItem, onBack: () -> Unit) {
    val context = LocalContext.current
    val exo = remember { ExoPlayer.Builder(context).build() }
    var error by remember { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }
    var retryKey by remember { mutableIntStateOf(0) }

    BackHandler(enabled = true) { onBack() }

    // Player durum dinleyicisi: hazır/hata durumunu UI'ye yansıt.
    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) ready = true
            }
            override fun onPlayerError(e: PlaybackException) {
                error = e.message ?: "Oynatma hatası (${e.errorCodeName})"
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    // load_links → stream URL + referer/user_agent header → Media3 HLS (proxy'siz).
    LaunchedEffect(item.url, retryKey) {
        error = null
        ready = false
        try {
            val links = Network.api.loadLinks(item.plugin, item.url)
            val link = links.result.firstOrNull()
            if (link == null || link.url.isBlank()) {
                error = "Oynatılacak kaynak bulunamadı"
                return@LaunchedEffect
            }
            val headers = buildMap {
                if (link.referer.isNotBlank()) put("Referer", link.referer)
            }
            val ua = link.userAgent.ifBlank { "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_5)" }

            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(ua)
                .setDefaultRequestProperties(headers)
                .setAllowCrossProtocolRedirects(true)

            val source = HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(ExoMediaItem.fromUri(link.url))

            exo.setMediaSource(source)
            exo.prepare()
            exo.playWhenReady = true
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
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        when {
            error != null -> PlayerOverlay(
                title = "Hata",
                message = error!!,
                actionLabel = "Tekrar dene",
                onAction = { retryKey++ },
                onBack = onBack,
            )
            !ready -> Overlay("Yükleniyor…")
        }
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
            Row(onAction = onAction, actionLabel = actionLabel, onBack = onBack)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Row(onAction: () -> Unit, actionLabel: String, onBack: () -> Unit) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onAction) { Text(actionLabel) }
        Button(onClick = onBack) { Text("Geri") }
    }
}
