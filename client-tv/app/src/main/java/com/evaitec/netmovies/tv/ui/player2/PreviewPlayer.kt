package com.evaitec.netmovies.tv.ui.player2

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.hls.HlsMediaSource
import kotlinx.coroutines.delay

/**
 * Scrub önizleme oynatıcısı (eski PlayerScreen U 1147-1177 + J 668-670 + 1130-1133).
 * Scrub ile doğar, scrub ile ölür; kaynak değişince eski akışı göstermesin diye
 * bırakılır. Tembel: ana akışla paralel hazırlanıp sesi çalmasın.
 */
@OptIn(UnstableApi::class)
@Composable
fun rememberPreviewPlayer(core: PlayerCore, ui: PlayerUiState): ExoPlayer? {
  val context = LocalContext.current
  var previewExo by remember { mutableStateOf<ExoPlayer?>(null) }
  val linkUrl = core.currentLinkUrl

  // Kaynak değişti: elde kalan önizleme eski akışı gösterir, bırakılır.
  LaunchedEffect(linkUrl) {
    previewExo?.release()
    previewExo = null
  }

  LaunchedEffect(ui.scrubMode, linkUrl) {
    if (ui.scrubMode) {
      val factory = core.aktifFactory
      if (previewExo == null && linkUrl != null && factory != null) {
        previewExo = runCatching {
          ExoPlayer.Builder(context).build().apply {
            volume = 0f
            playWhenReady = false
            setSeekParameters(SeekParameters.CLOSEST_SYNC)
            trackSelectionParameters = trackSelectionParameters.buildUpon()
              .setMaxVideoSize(426, 240)
              .setForceLowestBitrate(true)
              .build()
            setMediaSource(
              HlsMediaSource.Factory(factory).createMediaSource(ExoMediaItem.fromUri(linkUrl))
            )
            prepare()
            seekTo(ui.scrubPos)
          }
        }.getOrNull()
      }
    } else {
      previewExo?.release()
      previewExo = null
    }
  }

  // Scrub imleci değişince önizlemeyi seek et (debounce ~120 ms).
  LaunchedEffect(ui.scrubTick) {
    if (ui.scrubMode) { delay(120); runCatching { previewExo?.seekTo(ui.scrubPos) } }
  }

  DisposableEffect(Unit) {
    onDispose { previewExo?.release(); previewExo = null }
  }
  return previewExo
}
