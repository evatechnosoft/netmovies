package com.evaitec.netmovies.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    // Telefon kumandasındaki tuşu GERÇEK bir kumanda tuşuna çevirir. Sentetik olay
    // normal tuş yolundan aktığı için odak, oynatıcı ve buton-eşleme ayarları
    // kendiliğinden geçerli olur.
    private val tusKodlari = mapOf(
        "UP" to android.view.KeyEvent.KEYCODE_DPAD_UP,
        "DOWN" to android.view.KeyEvent.KEYCODE_DPAD_DOWN,
        "LEFT" to android.view.KeyEvent.KEYCODE_DPAD_LEFT,
        "RIGHT" to android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
        "CENTER" to android.view.KeyEvent.KEYCODE_DPAD_CENTER,
        "BACK" to android.view.KeyEvent.KEYCODE_BACK,
        "MENU" to android.view.KeyEvent.KEYCODE_MENU,
    )

    fun tusGonder(ad: String) {
        val kod = tusKodlari[ad] ?: return
        val an = android.os.SystemClock.uptimeMillis()
        dispatchKeyEvent(android.view.KeyEvent(an, an, android.view.KeyEvent.ACTION_DOWN, kod, 0))
        dispatchKeyEvent(android.view.KeyEvent(an, an + 1, android.view.KeyEvent.ACTION_UP, kod, 0))
    }

    // Açık içerik (Player). Telefondan "TV'de oynat" gelince bir şey OYNUYORSA doğrudan
    // geçilmez: `bekleyenUzak` dolar, ekranda "açayım mı?" kartı çıkar; OK = aç, GERİ = kal.
    // Tuşlar odaktan bağımsız burada (dispatchKeyEvent) yakalanır — oynatıcının kök
    // kutusu odağı geri alsa da kart tuşsuz kalmaz. Ekran boşsa eski davranış: hemen açılır.
    private var selected by mutableStateOf<MediaItem?>(null)
    private var bekleyenUzak by mutableStateOf<MediaItem?>(null)

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val bekleyen = bekleyenUzak
        if (bekleyen == null) {
            // GERİ, Compose'un odak sistemine İNMEDEN önce ekranın işleyicisine
            // gider: odak grupları tuşu "ilk öğeye dön" diye yutuyordu.
            if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                if (event.action == android.view.KeyEvent.ACTION_UP) {
                    if (com.evaitec.netmovies.tv.input.BackBus.geri()) return true
                } else if (com.evaitec.netmovies.tv.input.BackBus.varMi()) {
                    return true
                }
            }
            return super.dispatchKeyEvent(event)
        }
        if (event.action == android.view.KeyEvent.ACTION_DOWN) when (event.keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER,
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ->
                { bekleyenUzak = null; selected = bekleyen }
            android.view.KeyEvent.KEYCODE_BACK -> bekleyenUzak = null
        }
        return true   // kart açıkken diğer tuşlar oynatıcıya sızmaz
    }


    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.evaitec.netmovies.tv.data.ServerResolver.init(this)
        setContent {
            NetMoviesTheme {
                run {
                    androidx.compose.foundation.layout.Box(
                        Modifier.fillMaxSize().background(NmColor.Background)
                    ) {
                        // POC: harici nav kütüphanesi yok — state ile Home / Player / Buton Eşleme.
                        var showKeyMap by remember { mutableStateOf(false) }
                        var showRemote by remember { mutableStateOf(false) }
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
                        // Telefondan gelen metin: Gözat ekranının arama kutusuna düşer.
                        var kumandaMetni by remember { mutableStateOf<String?>(null) }

                        // ---------------------------------------------------- TELEFON KUMANDASI
                        // TEK yoklama döngüsü. Uzun-yoklama (wait=25) sayesinde komut sunucuya
                        // düşer düşmez gelir; 4 sn'lik turlarda her tuş ortalama iki saniye
                        // gecikiyordu ve D-pad kumanda gibi hissettirmiyordu.
                        //
                        // Tuşlar GERÇEK KeyEvent olarak enjekte edilir (dispatchKeyEvent): böylece
                        // Compose'un odak sistemi, oynatıcının tuş işleyicisi ve kullanıcının
                        // Buton Eşleme ayarları olduğu gibi geçerli kalır — kontrol mantığı
                        // kumanda için ikinci kez yazılmaz.
                        if (isTv) {
                            androidx.compose.runtime.LaunchedEffect(Unit) {
                                val ses = getSystemService(android.content.Context.AUDIO_SERVICE)
                                    as android.media.AudioManager
                                while (true) {
                                    val sonuc = runCatching {
                                        com.evaitec.netmovies.tv.data.Network.api.remotePoll(wait = 25)
                                    }
                                    if (sonuc.isFailure) {
                                        // Sunucu kapalı/tünel düşük: saniyede iki istekle dövme.
                                        kotlinx.coroutines.delay(3000)
                                        continue
                                    }
                                    val cmd = sonuc.getOrNull()?.result ?: continue

                                    when (cmd.type) {
                                        "play" -> if (cmd.url.isNotBlank()) {
                                            val gelen = MediaItem(
                                                plugin = cmd.plugin,
                                                title = cmd.title.ifBlank { null },
                                                url = com.evaitec.netmovies.tv.data.encodedUrl(cmd.url),
                                                poster = cmd.poster.ifBlank { null },
                                                autoplay = true,
                                                episode = cmd.episode,
                                            )
                                            // Bir şey oynuyorsa sormadan kesme (Dean: "film
                                            // çalışırken direkt geçiş yapıyor").
                                            if (selected != null) bekleyenUzak = gelen else selected = gelen
                                        }

                                        "key" -> when (cmd.key) {
                                            // HOME'u tuş olarak yollamak uygulamayı arka plana
                                            // atardı; kastedilen NetMovies'in ana ekranı.
                                            "HOME" -> {
                                                selected = null; showBrowse = false; showAdmin = false
                                                showFollowing = false; showChannels = false; showKeyMap = false
                                            }
                                            else -> tusGonder(cmd.key)
                                        }

                                        // Ses seviyesi ekrandan bağımsız: oynatıcı kapalıyken de
                                        // çalışsın diye sistem sesine gider.
                                        "transport" -> if (cmd.action == "volume") {
                                            ses.adjustStreamVolume(
                                                android.media.AudioManager.STREAM_MUSIC,
                                                if (cmd.value >= 0) android.media.AudioManager.ADJUST_RAISE
                                                else android.media.AudioManager.ADJUST_LOWER,
                                                android.media.AudioManager.FLAG_SHOW_UI,
                                            )
                                        } else {
                                            com.evaitec.netmovies.tv.data.RemoteBus.yayinla(cmd)
                                        }

                                        "text" -> {
                                            if (!showBrowse) { browseVaultMode = false; showBrowse = true }
                                            kumandaMetni = cmd.text.ifBlank { null }
                                        }

                                        "nav" -> when (cmd.screen) {
                                            "home" -> {
                                                selected = null; showBrowse = false; showAdmin = false
                                                showFollowing = false; showChannels = false; showKeyMap = false
                                            }
                                            "browse" -> { browseVaultMode = false; showBrowse = true }
                                            "following" -> showFollowing = true
                                            "channels" -> showChannels = true
                                            "admin" -> showAdmin = true
                                        }
                                    }
                                }
                            }
                        }
                        val bindings = remember(this@MainActivity) { KeyBindings(this@MainActivity) }
                        // Buton eşlemesi sunucuda da duruyor: yeniden kurulumda ya da
                        // başka bir TV'de aynı düzen gelsin (Dean: "her yüklemede sıfırlanıyor").
                        androidx.compose.runtime.LaunchedEffect(Unit) { bindings.sunucudanYukle() }
                        val library = remember(this@MainActivity) { Library(this@MainActivity) }
                        val current = selected
                        when {
                            current != null ->
                                PlayerScreen(item = current, bindings = bindings, library = library, onBack = { selected = null })
                            showKeyMap ->
                                KeyMapScreen(bindings = bindings, onBack = { showKeyMap = false })
                            showRemote ->
                                com.evaitec.netmovies.tv.ui.RemoteScreen(
                                    url = com.evaitec.netmovies.tv.data.ServerResolver.uiBase().toString().trimEnd('/') + "/rc",
                                    onBack = { showRemote = false },
                                )
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
                                    // Telefondan yazılan metin: geldiğinde arama kutusuna düşer.
                                    remoteQuery = kumandaMetni,
                                    onRemoteQueryUsed = { kumandaMetni = null },
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
                                        onOpenRemote = { showRemote = true },
                                        onOpenVault = { browseVaultMode = true; showBrowse = true },
                                        onOpenAdmin = { showAdmin = true },
                                        onOpenFollowing = { showFollowing = true },
                                        onOpenChannels = { showChannels = true },
                                        library = library,
                                    )
                                }
                        }

                        bekleyenUzak?.let { UzakOnayKarti(it.title.orEmpty()) }
                    }
                }
            }
        }
    }

    // "Açayım mı?" kartı: oynayan filmin üstünde alt-orta, tuşlar dispatchKeyEvent'te.
    // Cevapsız kalırsa 30 sn sonra kendi kapanır; film kesintisiz sürer.
    @OptIn(ExperimentalTvMaterial3Api::class)
    @androidx.compose.runtime.Composable
    private fun UzakOnayKarti(baslik: String) {
        androidx.compose.runtime.LaunchedEffect(baslik) {
            kotlinx.coroutines.delay(30_000)
            bekleyenUzak = null
        }
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize().padding(bottom = 48.dp),
            contentAlignment = androidx.compose.ui.Alignment.BottomCenter,
        ) {
            androidx.compose.foundation.layout.Column(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(NmColor.SurfaceDialog)
                    .padding(horizontal = 28.dp, vertical = 18.dp),
            ) {
                // Yazı yok, simge var (Dean): OK = ⏭ yeni içeriğe geç · GERİ = ▶ sürdür.
                androidx.tv.material3.Text(
                    "📱  $baslik",
                    color = NmColor.OnSurface,
                    fontSize = 22.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
                androidx.tv.material3.Text(
                    "OK ⏭        GERİ ▶",
                    color = NmColor.Primary,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(top = 6.dp).align(androidx.compose.ui.Alignment.CenterHorizontally),
                )
            }
        }
    }
}
