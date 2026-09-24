package com.evaitec.netmovies.tv.ui.player2

import androidx.compose.runtime.Composable
import com.evaitec.netmovies.tv.data.Library
import com.evaitec.netmovies.tv.data.MediaItem
import com.evaitec.netmovies.tv.input.KeyBindings

/**
 * Oynatıcı 2 — ekran. Eski `PlayerScreen` ile AYNI imza: MainActivity anahtarla
 * birini seçer, eskisi yedek kalır. SAHİBİ: ui ajanı. Mevcut alt composable'lar
 * (ControlsOverlay, SettingsPanel, StartPanel, ScrubOverlay, …) `ui/PlayerScreen.kt`
 * içinde `internal` — yeniden yazma, çağır.
 */
@Composable
fun PlayerScreen2(
    item: MediaItem,
    bindings: KeyBindings,
    library: Library,
    onBack: () -> Unit,
    kanallar: List<MediaItem> = emptyList(),
    onKanal: (MediaItem) -> Unit = {},
): Unit = TODO("ui")
