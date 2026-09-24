package com.evaitec.netmovies.tv.ui.player2

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Oynatıcı 2 — UI bayrakları ve TEK GERİ mantığı. Eski kodda GERİ iki yerde
 * (NmBackHandler + onPreviewKeyEvent) tekrar ediyordu; burada tek fonksiyon.
 *
 * SAHİBİ: keys ajanı. Alan EKLEYEBİLİR, mevcut imzaları değiştiremez.
 */
class PlayerUiState {
    var showSettings by mutableStateOf(false)
    var showSeek by mutableStateOf(false)
    var showReport by mutableStateOf(false)
    var showKeys by mutableStateOf(false)
    var panelAsList by mutableStateOf(false)
    var secilenSezon by mutableStateOf<Int?>(null)
    var panelGeriGelsin by mutableStateOf(false)
    /** Ayarlar doğrudan Bölümler sekmesinde açılsın. */
    var ayarBolumler by mutableStateOf(false)
    var showControls by mutableStateOf(false)
    var controlsTick by mutableIntStateOf(0)
    var scrubMode by mutableStateOf(false)
    var scrubPos by mutableLongStateOf(0L)
    var scrubTick by mutableIntStateOf(0)
    var keyHint by mutableStateOf<String?>(null)
    var keyTick by mutableIntStateOf(0)

    fun flashControls(): Unit = TODO("keys")

    /**
     * GERİ önceliği (eski PlayerScreen:643-666 ile birebir): geri sayım → iptal;
     * scrub → kapat; başlangıç paneli → liste/sezon kapat, kaynak varsa OYNAT, yoksa
     * çık; ayarlar → kapat (+paneli geri getir); kontroller → gizle; yoksa [onExit].
     */
    fun onBack(core: PlayerCore, onExit: () -> Unit): Unit = TODO("keys")
}
