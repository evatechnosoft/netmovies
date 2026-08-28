package com.evaitec.netmovies.tv.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.evaitec.netmovies.tv.data.MediaItem
import com.evaitec.netmovies.tv.data.Network

@OptIn(UnstableApi::class)
@androidx.compose.runtime.Composable
fun PlayerScreen(item: MediaItem, onBack: () -> Unit) {
    val context = LocalContext.current
    val exo = remember { ExoPlayer.Builder(context).build() }
    var error by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = true) { onBack() }

    // load_links ile stream URL'ini + referer/user_agent'ı çözüp ExoPlayer'a header
    // ile besle (web proxy'sine gerek kalmadan doğrudan CDN'den HLS).
    LaunchedEffect(item.url) {
        try {
            val links = Network.api.loadLinks(item.plugin, item.url)
            val link  = links.result.firstOrNull()
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
            error = e.message ?: "Oynatma hatası"
        }
    }

    DisposableEffect(Unit) {
        onDispose { exo.release() }
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
        error?.let { ErrorOverlay(it) }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun ErrorOverlay(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Hata: $message")
    }
}
