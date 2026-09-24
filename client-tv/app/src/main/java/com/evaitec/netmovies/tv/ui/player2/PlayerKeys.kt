package com.evaitec.netmovies.tv.ui.player2

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.KeyEvent
import com.evaitec.netmovies.tv.data.MediaItem
import com.evaitec.netmovies.tv.input.KeyBindings

/**
 * Oynatıcı 2 — tuş eşleme. Kumanda, medya tuşları ve telefon kumandası (RemoteBus
 * transport) aynı eylemlere iner. SAHİBİ: keys ajanı.
 */
class PlayerKeys(
    val core: PlayerCore,
    val ui: PlayerUiState,
    val onExit: () -> Unit,
    /** Canlı yayında kanal değiştir: -1 / +1. */
    val kanalAtla: (Int) -> Unit,
) {
    /** Kök Box `onPreviewKeyEvent`: KeyPairGate, ipucu, bilgi tuşları, başlangıç panelinde GERİ. */
    fun onPreviewKey(e: KeyEvent): Boolean = TODO("keys")

    /** Kök Box `onKeyEvent`: MENU, medya tuşları, atla/sonraki, scrub, RemoteInputController. */
    fun onKey(e: KeyEvent): Boolean = TODO("keys")
}

/** RemoteInputController + KeyPairGate + RemoteBus toplayıcısı + NmBackHandler kurulumu. */
@Composable
fun rememberPlayerKeys(
    core: PlayerCore,
    ui: PlayerUiState,
    bindings: KeyBindings,
    kanallar: List<MediaItem>,
    onKanal: (MediaItem) -> Unit,
    onExit: () -> Unit,
): PlayerKeys = TODO("keys")
