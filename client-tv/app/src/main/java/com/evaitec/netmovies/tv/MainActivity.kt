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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import com.evaitec.netmovies.tv.data.MediaItem
import com.evaitec.netmovies.tv.ui.HomeScreen
import com.evaitec.netmovies.tv.ui.PlayerScreen

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxSize().background(Color(0xFF0F0F14))
                ) {
                    // POC: harici nav kütüphanesi yok — tek state ile Home <-> Player.
                    var selected by remember { mutableStateOf<MediaItem?>(null) }
                    val current = selected
                    if (current == null) {
                        HomeScreen(onSelect = { selected = it })
                    } else {
                        PlayerScreen(item = current, onBack = { selected = null })
                    }
                }
            }
        }
    }
}
