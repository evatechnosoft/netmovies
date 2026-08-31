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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.compose.ui.platform.LocalContext
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
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary        = Color(0xFF8B5CF6),
                    onPrimary      = Color(0xFFFFFFFF),
                    background     = Color(0xFF0F0F14),
                    onBackground   = Color(0xFFEDEDF2),
                    surface        = Color(0xFF1A1726),
                    onSurface      = Color(0xFFEDEDF2),
                    surfaceVariant = Color(0xFF241F33),
                )
            ) {
                // tv-material3 Text rengini LocalContentColor'dan okur; içerik bir Surface
                // içinde olmadığından varsayılan Color.Black kalıyordu → koyu zeminde yazı
                // görünmüyordu. Tema onBackground rengini tüm içeriğe zorla.
                CompositionLocalProvider(LocalContentColor provides Color(0xFFEDEDF2)) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.fillMaxSize().background(Color(0xFF0F0F14))
                    ) {
                        // POC: harici nav kütüphanesi yok — state ile Home / Player / Buton Eşleme.
                        var selected by remember { mutableStateOf<MediaItem?>(null) }
                        var showKeyMap by remember { mutableStateOf(false) }
                        var showBrowse by remember { mutableStateOf(false) }
                        var browseVaultMode by remember { mutableStateOf(false) }
                        var showVault by remember { mutableStateOf(false) }
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
                                        onOpenBrowse = { browseVaultMode = false; showBrowse = true },
                                        onOpenKeyMap = { showKeyMap = true },
                                        onOpenVault = { browseVaultMode = true; showBrowse = true },
                                        showVault = showVault,
                                        onToggleVault = { showVault = !showVault },
                                        library = library,
                                    )
                                }
                        }
                    }
                }
            }
        }
    }
}
