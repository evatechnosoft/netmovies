package com.evaitec.netmovies.tv.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession

/**
 * Oynatıcıyı sisteme MediaSession olarak tanıtır: kumandanın medya tuşları
 * (oynat/duraklat, ileri/geri, sonraki/önceki) ekran odağı panelde olsa ya da
 * tuş pencereye hiç gelmese de çalışır; Asistan da "duraklat" diyebilir.
 *
 * Oynatıcı tek öğeyle oynuyor, bölümler playlist değil. Sonraki/önceki bu yüzden
 * ForwardingPlayer'la uygulamanın kendi bölüm geçişine bağlanır. Komutlar hep
 * sunulur (bölüm listesi sonradan yüklenince oturum yeniden haber almaz);
 * [onNext] null ise (film, son bölüm) tuş boşa gider.
 */
@Composable
fun MedyaOturumu(exo: ExoPlayer, onNext: (() -> Unit)?, onPrev: (() -> Unit)?) {
    val context = LocalContext.current
    val sonraki by rememberUpdatedState(onNext)
    val onceki by rememberUpdatedState(onPrev)
    DisposableEffect(exo) {
        val oturum = oturumKur(context, exo, { sonraki }, { onceki })
        onDispose { oturum.release() }
    }
}

private fun oturumKur(
    context: Context,
    exo: ExoPlayer,
    sonraki: () -> (() -> Unit)?,
    onceki: () -> (() -> Unit)?,
): MediaSession {
    val oyuncu = object : ForwardingPlayer(exo) {
        override fun getAvailableCommands(): Player.Commands {
            return super.getAvailableCommands().buildUpon().addAll(
                Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            ).build()
        }

        override fun isCommandAvailable(command: Int): Boolean = getAvailableCommands().contains(command)

        override fun seekToNext() { sonraki()?.invoke() }
        override fun seekToNextMediaItem() { sonraki()?.invoke() }
        override fun seekToPrevious() { onceki()?.invoke() }
        override fun seekToPreviousMediaItem() { onceki()?.invoke() }
    }
    return MediaSession.Builder(context, oyuncu).build()
}
