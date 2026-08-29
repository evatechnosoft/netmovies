package com.evaitec.netmovies.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
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
import com.evaitec.netmovies.tv.data.MediaItem
import com.evaitec.netmovies.tv.ui.HomeScreen
import com.evaitec.netmovies.tv.ui.PlayerScreen
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
                        // POC: harici nav kütüphanesi yok — tek state ile Home <-> Player.
                        var selected by remember { mutableStateOf<MediaItem?>(null) }
                        val current = selected
                        if (current == null) {
                            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                                UpdateBanner()   // güncelleme varsa üstte şerit
                                HomeScreen(onSelect = { selected = it })
                            }
                        } else {
                            PlayerScreen(item = current, onBack = { selected = null })
                        }
                    }
                }
            }
        }
    }
}
