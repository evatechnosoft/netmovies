package com.evaitec.netmovies.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.evaitec.netmovies.tv.ui.theme.NetMoviesTheme
import com.evaitec.netmovies.tv.ui.theme.NmColor
import com.evaitec.netmovies.tv.data.Library
import com.evaitec.netmovies.tv.data.MediaItem
import com.evaitec.netmovies.tv.input.KeyBindings
import com.evaitec.netmovies.tv.ui.BrowseScreen
import com.evaitec.netmovies.tv.ui.HomeScreen
import com.evaitec.netmovies.tv.ui.KeyMapScreen
import com.evaitec.netmovies.tv.ui.PlayerScreen
import com.evaitec.netmovies.tv.ui.TouchButton
import com.evaitec.netmovies.tv.ui.UpdateBanner

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NetMoviesTheme {
                run {
                    androidx.compose.foundation.layout.Box(
                        Modifier.fillMaxSize().background(NmColor.Background)
                    ) {
                        // POC: harici nav kütüphanesi yok — state ile Home / Player / Buton Eşleme.
                        var selected by remember { mutableStateOf<MediaItem?>(null) }
                        var showKeyMap by remember { mutableStateOf(false) }
                        var showBrowse by remember { mutableStateOf(false) }
                        var browseVaultMode by remember { mutableStateOf(false) }
                        var showVault by remember { mutableStateOf(false) }
                        var mouseMode by remember { mutableStateOf(false) }
                        val bindings = remember(this@MainActivity) { KeyBindings(this@MainActivity) }
                        val library = remember(this@MainActivity) { Library(this@MainActivity) }
                        val current = selected
                        when {
                            current != null ->
                                PlayerScreen(item = current, bindings = bindings, library = library, onBack = { selected = null })
                            showKeyMap ->
                                KeyMapScreen(bindings = bindings, onBack = { showKeyMap = false })
                            showBrowse ->
                                BrowseScreen(
                                    showVault = showVault,
                                    vaultMode = browseVaultMode,
                                    onSelect = { selected = it },
                                    onBack = { showBrowse = false; browseVaultMode = false }
                                )
                            else ->
                                androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                                    UpdateBanner()   // güncelleme varsa üstte şerit
                                    HomeScreen(
                                        onSelect = { selected = it },
                                        // Ana ekranda GERİ: liste aşağıdaysa en üste döner,
                                        // en üstteyken uygulamadan çıkar (TV alışkanlığı).
                                        onExit = { finish() },
                                        onOpenBrowse = { browseVaultMode = false; showBrowse = true },
                                        onOpenKeyMap = { showKeyMap = true },
                                        onOpenVault = { browseVaultMode = true; showBrowse = true },
                                        showVault = showVault,
                                        onToggleVault = { showVault = !showVault },
                                        onToggleMouseMode = { mouseMode = !mouseMode },
                                        library = library,
                                    )
                                }
                        }

                        // Sanal Fare İmleci Katmanı (Mouse Mode)
                        com.evaitec.netmovies.tv.ui.VirtualMouseOverlay(
                            active = mouseMode,
                            onToggle = { mouseMode = !mouseMode }
                        )
                    }
                }
            }
        }
    }
}
