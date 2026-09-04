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
import kotlinx.coroutines.launch
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
                        var showAdmin by remember { mutableStateOf(false) }
                        var showFollowing by remember { mutableStateOf(false) }
                        var showChannels by remember { mutableStateOf(false) }

                        // Aynı APK telefona da kuruluyor (leanback zorunlu değil).
                        // TELEFONDA seçilen içerik cihazda açılmaz, TELEVİZYONA gönderilir:
                        // Dean'in istediği akış "telefondan arat, seç, TV'de başlasın".
                        // Yansıtma değil — komut gider, akışı TV çözer.
                        // Telefon burada KUMANDADIR: cihazda oynatma yolu yok (uzun-bas
                        // menüsündeki Oynat da aynı komutu TV'ye gönderir).
                        val isTv = remember {
                            val mode = getSystemService(android.content.Context.UI_MODE_SERVICE)
                                as android.app.UiModeManager
                            mode.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
                        }
                        val scope = androidx.compose.runtime.rememberCoroutineScope()
                        val pick: (MediaItem) -> Unit = { item ->
                            if (isTv) {
                                selected = item
                            } else {
                                scope.launch {
                                    val ok = runCatching {
                                        com.evaitec.netmovies.tv.data.Network.api.remotePlay(
                                            plugin = item.plugin,
                                            url = com.evaitec.netmovies.tv.data.rawUrl(item.url),
                                            title = item.title.orEmpty(),
                                            poster = item.poster.orEmpty(),
                                        ).result.ok
                                    }.getOrDefault(false)
                                    android.widget.Toast.makeText(
                                        this@MainActivity,
                                        if (ok) "📺 TV'ye gönderildi: ${item.title.orEmpty()}"
                                        else "TV'ye gönderilemedi — sunucuya ulaşılamadı",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        }
                        var browseVaultMode by remember { mutableStateOf(false) }
                        val bindings = remember(this@MainActivity) { KeyBindings(this@MainActivity) }
                        val library = remember(this@MainActivity) { Library(this@MainActivity) }
                        val current = selected
                        when {
                            current != null ->
                                PlayerScreen(item = current, bindings = bindings, library = library, onBack = { selected = null })
                            showKeyMap ->
                                KeyMapScreen(bindings = bindings, onBack = { showKeyMap = false })
                            showAdmin ->
                                com.evaitec.netmovies.tv.ui.AdminScreen(onBack = { showAdmin = false })
                            showChannels ->
                                com.evaitec.netmovies.tv.ui.ChannelsScreen(
                                    onSelect = pick,
                                    onBack = { showChannels = false },
                                )
                            showFollowing ->
                                com.evaitec.netmovies.tv.ui.FollowingScreen(
                                    onSelect = pick,
                                    onBack = { showFollowing = false },
                                )
                            showBrowse ->
                                BrowseScreen(
                                    // Yetiskin kaynaklar NORMAL Gozat'ta hic gorunmez;
                                    // yalnizca Ozel Koleksiyon ekraninda listelenir.
                                    showVault = false,
                                    vaultMode = browseVaultMode,
                                    onSelect = pick,
                                    onBack = { showBrowse = false; browseVaultMode = false }
                                )
                            else ->
                                androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                                    UpdateBanner()   // güncelleme varsa üstte şerit
                                    HomeScreen(
                                        onSelect = pick,
                                        // Ana ekranda GERİ: liste aşağıdaysa en üste döner,
                                        // en üstteyken uygulamadan çıkar (TV alışkanlığı).
                                        onExit = { finish() },
                                        onOpenBrowse = { browseVaultMode = false; showBrowse = true },
                                        onOpenKeyMap = { showKeyMap = true },
                                        onOpenVault = { browseVaultMode = true; showBrowse = true },
                                        onOpenAdmin = { showAdmin = true },
                                        onOpenFollowing = { showFollowing = true },
                                        onOpenChannels = { showChannels = true },
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
