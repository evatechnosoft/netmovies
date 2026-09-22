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
import androidx.compose.ui.zIndex
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.evaitec.netmovies.tv.ui.theme.NetMoviesTheme
import com.evaitec.netmovies.tv.ui.theme.NmColor
import com.evaitec.netmovies.tv.data.Library
import com.evaitec.netmovies.tv.data.MediaItem
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.evaitec.netmovies.tv.input.KeyBindings
import com.evaitec.netmovies.tv.ui.BrowseScreen
import com.evaitec.netmovies.tv.ui.HomeScreen
import com.evaitec.netmovies.tv.ui.KeyMapScreen
import com.evaitec.netmovies.tv.ui.PlayerScreen
import com.evaitec.netmovies.tv.ui.TouchButton
import com.evaitec.netmovies.tv.ui.UpdateBanner

class MainActivity : ComponentActivity() {

    companion object {
        /** Acilista "devam edelim mi" sorulan en buyuk kayit yasi (sn). Daha eski
         *  izleme kazayla kesilmis sayilmaz; Devam Et rafindan acilir. */
        private const val DEVAM_PENCERESI_SN = 30L * 60
    }

    /** Önceki açılışta çökme olduysa yığın izi — ekrandaki şerit bunu gösterir. */
    private var cokmeIzi by mutableStateOf<List<String>?>(null)

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

    /** Kart telefondan gelen içeriği değil, açılıştaki "kaldığın yer"i soruyor. */
    private var bekleyenDevam by mutableStateOf(false)

    /** GERİ basılı tutulup çıkış tetiklendi mi — bırakma olayı ikinci kez işlenmesin. */
    private var uzunGeriYapildi = false

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        // Cokme seridi acikken ilk tus onu kapatir — dugmesine odak gitmiyordu
        // (Dean: "kapata basamiyoruz"). Odak sistemine hic guvenmiyoruz.
        if (cokmeIzi != null && event.action == android.view.KeyEvent.ACTION_DOWN) {
            cokmeIzi = null
            return true
        }
        val bekleyen = bekleyenUzak
        if (bekleyen == null) {
            // GERİ, Compose'un odak sistemine İNMEDEN önce ekranın işleyicisine
            // gider: odak grupları tuşu "ilk öğeye dön" diye yutuyordu.
            if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                    // GERİ'yi BASILI TUTMAK uygulamadan çıkarır. Tek basış artık
                    // hiçbir yerde çıkmıyor: ana ekranda yanlışlıkla bir basış
                    // uygulamayı kapatıyordu (Dean: "çıkması için basılı tutma
                    // koyalım, zaten istediğimde HOME ile çıkıyorum").
                    if (event.repeatCount > 0) {
                        if (!uzunGeriYapildi) { uzunGeriYapildi = true; finish() }
                        return true
                    }
                    uzunGeriYapildi = false
                    if (com.evaitec.netmovies.tv.input.BackBus.varMi()) return true
                } else if (event.action == android.view.KeyEvent.ACTION_UP) {
                    // Uzun basış çıkışı tetiklediyse bırakma olayı yutulur, yoksa
                    // ekran ayrıca bir GERİ daha işler.
                    if (uzunGeriYapildi) { uzunGeriYapildi = false; return true }
                    if (com.evaitec.netmovies.tv.input.BackBus.geri()) return true
                }
            }
            return super.dispatchKeyEvent(event)
        }
        if (event.action == android.view.KeyEvent.ACTION_DOWN) when (event.keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER,
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ->
                { bekleyenUzak = null; selected = bekleyen }
            android.view.KeyEvent.KEYCODE_BACK -> bekleyenUzak = null
            // Açılıştaki devam kartı ana ekranın ÜSTÜNDE duruyor: başka bir tuşa
            // basmak kartı kapatıp tuşu ana ekrana geçirir, kullanıcı kilitlenmez.
            // Telefondan gelen kartta bu yok — oynayan filme tuş sızmamalı.
            else -> if (bekleyenDevam) {
                bekleyenUzak = null
                return super.dispatchKeyEvent(event)
            }
        }
        return true   // kart açıkken diğer tuşlar oynatıcıya sızmaz
    }


    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.evaitec.netmovies.tv.data.ServerResolver.init(this)
        // Çökme izi: bu açılıştan önceki çökme varsa sunucuya gider, sonra silinir.
        com.evaitec.netmovies.tv.data.CrashLog.kur(this)
        com.evaitec.netmovies.tv.data.CrashLog.bekleyen(this)?.let { satirlar ->
            satirlar.forEach { com.evaitec.netmovies.tv.data.PlaybackLog.warn("cokme", it) }
            // Ekranda da göster: sunucuya gönderim ağa bağlı, iki turdur hiçbir iz
            // ulaşmadı (`client_log` boş). Şerit televizyonda okunur, fotoğrafı
            // yeter (Dean, 19 Eylül: "yine patlıyor").
            cokmeIzi = satirlar
            // Dosya OKUNUR OKUNMAZ silinir. Onceden "sunucuya ulasinca" siliniyordu;
            // gonderim tutmayinca ayni serit HER acilista geliyordu (Dean, 19 Eylul).
            // Satirlar bellekte, gonderim asagida yine 3 kez deneniyor.
            com.evaitec.netmovies.tv.data.CrashLog.temizle(this)
            lifecycleScope.launch {
                // Sunucu adresi açılışın ilk saniyelerinde henüz çözülmemiş olabilir:
                // tek deneme sessizce kaybediyordu.
                repeat(3) { deneme ->
                    val gonderildi = runCatching {
                        com.evaitec.netmovies.tv.data.Network.api.clientLog(
                            mapOf("lines" to (listOf("=== ÖNCEKİ AÇILIŞTA ÇÖKME ===") + satirlar))
                        )
                    }.isSuccess
                    if (gonderildi) return@launch
                    kotlinx.coroutines.delay(4000)
                }
            }
        }
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
                        var showAgenda by remember { mutableStateOf(false) }
                        // Gözat'a ajandadan mı girildi: GERİ oraya dönsün, arama
                        // sonucu tek eşleşmeyse dizi doğrudan açılsın.
                        var ajandadanGeldi by remember { mutableStateOf(false) }
                        var showSearch by remember { mutableStateOf(false) }
                        // Canlı TV listesi oynatıcıya taşınır: kanal değiştirmek
                        // için ekrandan çıkmak gerekmesin.
                        var kanalListesi by remember { mutableStateOf<List<com.evaitec.netmovies.tv.data.MediaItem>>(emptyList()) }
                        // Arama durumu ekranın DIŞINDA yaşar: ekran bileşimden
                        // çıkınca `remember` ölüyor, dönüşte sonuç kayboluyordu.
                        val searchState = remember { com.evaitec.netmovies.tv.ui.SearchState() }
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
                        // Bölüm seçilerek açma: TV'de doğrudan o bölüm, telefonda
                        // TV'ye o bölümle komut. `pick` ile aynı yol, tek farkı
                        // bölüm sırasının taşınması.
                        val pickEpisode: (MediaItem, Int) -> Unit = { item, idx ->
                            if (isTv) {
                                selected = item.copy(episode = idx, autoplay = true)
                            } else {
                                scope.launch {
                                    val ok = runCatching {
                                        com.evaitec.netmovies.tv.data.Network.api.remotePlay(
                                            plugin = item.plugin,
                                            url = com.evaitec.netmovies.tv.data.rawUrl(item.url),
                                            title = item.title.orEmpty(),
                                            poster = item.poster.orEmpty(),
                                            episode = idx,
                                        ).result.ok
                                    }.getOrDefault(false)
                                    android.widget.Toast.makeText(
                                        this@MainActivity,
                                        if (ok) "📺 TV'ye gönderildi: ${item.title.orEmpty()} · ${idx + 1}. bölüm"
                                        else "TV'ye gönderilemedi — sunucuya ulaşılamadı",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        }
                        var browseVaultMode by remember { mutableStateOf(false) }
                        // Gözat'ın yeri oynatıcıdan bağımsız yaşar: bir diziye girip
                        // GERİ ile çıkınca aynı kaynakta, aynı rafta, aynı posterde
                        // kalınır (Dean: "geri çıkınca taa başka yere atıyor").
                        val browseState = remember { com.evaitec.netmovies.tv.ui.BrowseState() }
                        // Ana ekran için aynısı: içerikten GERİ ile çıkınca raf ve
                        // poster odağı korunur.
                        val homePosition = remember { com.evaitec.netmovies.tv.ui.HomePosition() }
                        // Telefondan gelen metin: Gözat ekranının arama kutusuna düşer.
                        var kumandaMetni by remember { mutableStateOf<String?>(null) }

                        // "Ana sayfa": tüm ekranları kapat, uygulamanın ana ekranına dön.
                        // Üç yerden çağrılır — telefon kumandası (key HOME / nav home) ve
                        // oynatıcıdaki hızlı pad. Sistemin HOME tuşu DEĞİL: o tuş uygulamaya
                        // hiç gelmez (Android onu launcher'a verir), uygulamayı arka plana atar.
                        val anaSayfa = {
                            selected = null; showBrowse = false; showAdmin = false
                            showFollowing = false; showChannels = false; showKeyMap = false; showAgenda = false
                        }

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
                                            if (selected != null) { bekleyenDevam = false; bekleyenUzak = gelen } else selected = gelen
                                        }

                                        "key" -> when (cmd.key) {
                                            // HOME'u tuş olarak yollamak uygulamayı arka plana
                                            // atardı; kastedilen NetMovies'in ana ekranı.
                                            "HOME" -> anaSayfa()
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
                                            "home" -> anaSayfa()
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

                        // Izlerken uygulama arka planda oldurulurse (Ayarlar'a Bluetooth icin
                        // cikmak yetiyor) acilista ana ekrana dusuluyordu. Konum zaten sunucuda
                        // (15 sn'de bir yaziliyor): taze kayit varsa kart cikar, OK kaldigin
                        // yerden surdurur. Telefonda sorulmaz - orada oynatma yok.
                        androidx.compose.runtime.LaunchedEffect(Unit) {
                            if (isTv && selected == null && bekleyenUzak == null) {
                                library.sonKalinanYer(DEVAM_PENCERESI_SN)?.let {
                                    bekleyenDevam = true
                                    bekleyenUzak = it
                                }
                            }
                        }
                        val current = selected
                        when {
                            current != null ->
                                PlayerScreen(
                                    item = current,
                                    bindings = bindings,
                                    library = library,
                                    onBack = { selected = null },
                                    onHome = anaSayfa,
                                    kanallar = kanalListesi,
                                    onKanal = { selected = it },
                                )
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
                                    onKanallar = { kanalListesi = it },
                                )
                            showSearch ->
                                com.evaitec.netmovies.tv.ui.SearchScreen(
                                    state = searchState,
                                    onSelect = pick,
                                    onBack = { showSearch = false },
                                )
                            showAgenda ->
                                com.evaitec.netmovies.tv.ui.AgendaScreen(
                                    onBack = { showAgenda = false },
                                    // Ajandadaki satır oynatma adresi taşımıyor;
                                    // başlık telefon kumandasıyla aynı kanaldan
                                    // (remoteQuery) Gözat'ın aramasına düşer.
                                    onAra = { baslik ->
                                        kumandaMetni = baslik
                                        ajandadanGeldi = true
                                        showAgenda = false
                                        showBrowse = true
                                    },
                                )
                            showFollowing ->
                                com.evaitec.netmovies.tv.ui.FollowingScreen(
                                    onSelect = pick,
                                    onBack = { showFollowing = false },
                                )
                            showBrowse ->
                                BrowseScreen(
                                    state = browseState,
                                    // Yetiskin kaynaklar NORMAL Gozat'ta hic gorunmez;
                                    // yalnizca Ozel Koleksiyon ekraninda listelenir.
                                    showVault = false,
                                    vaultMode = browseVaultMode,
                                    onSelect = pick,
                                    // Telefondan yazılan metin: geldiğinde arama kutusuna düşer.
                                    remoteQuery = kumandaMetni,
                                    onRemoteQueryUsed = { kumandaMetni = null },
                                    // Ajandadan gelen başlıkta tek eşleşme doğrudan açılır.
                                    otomatikAc = ajandadanGeldi,
                                    // ...ve GERİ, gelinen yere döner: ajandadan girip
                                    // ana ekrana düşmek "teker teker dönmek" oluyordu.
                                    onBack = {
                                        showBrowse = false
                                        browseVaultMode = false
                                        if (ajandadanGeldi) {
                                            ajandadanGeldi = false
                                            showAgenda = true
                                        }
                                    }
                                )
                            else ->
                                androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                                    UpdateBanner()   // güncelleme varsa üstte şerit
                                    HomeScreen(
                                        position = homePosition,
                                        onSelect = pick,
                                        onSelectEpisode = pickEpisode,
                                        menuOnTap = !isTv,
                                        // Ana ekranda GERİ: liste aşağıdaysa en üste döner,
                                        // en üstteyken uygulamadan çıkar (TV alışkanlığı).
                                        onExit = { finish() },
                                        onOpenBrowse = { browseVaultMode = false; showBrowse = true },
                                        onOpenSearch = { showSearch = true },
                                        onOpenKeyMap = { showKeyMap = true },
                                        onOpenRemote = { showRemote = true },
                                        onOpenVault = { browseVaultMode = true; showBrowse = true },
                                        onOpenAdmin = { showAdmin = true },
                                        onOpenFollowing = { showFollowing = true },
                                        onOpenAgenda = { showAgenda = true },
                                        onOpenChannels = { showChannels = true },
                                        library = library,
                                    )
                                }
                        }

                        bekleyenUzak?.let { UzakOnayKarti(it.title.orEmpty(), bekleyenDevam) }

                        // En üstte: önceki açılıştaki çökme izi.
                        cokmeIzi?.let { satirlar ->
                            CokmeSeridi(satirlar) { cokmeIzi = null }
                        }
                    }
                }
            }
        }
    }

    // "Açayım mı?" kartı: oynayan filmin üstünde alt-orta, tuşlar dispatchKeyEvent'te.
    // Cevapsız kalırsa 30 sn sonra kendi kapanır; film kesintisiz sürer.
    @OptIn(ExperimentalTvMaterial3Api::class)
    @androidx.compose.runtime.Composable
    private fun UzakOnayKarti(baslik: String, devam: Boolean) {
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
                    if (devam) "▶  $baslik" else "📱  $baslik",
                    color = NmColor.OnSurface,
                    fontSize = 22.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
                androidx.tv.material3.Text(
                    if (devam) "OK ▶ devam        GERİ ✕" else "OK ⏭        GERİ ▶",
                    color = NmColor.Primary,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(top = 6.dp).align(androidx.compose.ui.Alignment.CenterHorizontally),
                )
            }
        }
    }
}

/** Önceki açılıştaki çökmenin ilk satırları. Televizyonda okunur boyutta:
 *  sunucuya gönderim ağa bağlı, bu şerit fotoğraflanabiliyor. */
@androidx.compose.runtime.Composable
private fun CokmeSeridi(satirlar: List<String>, onKapat: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxWidth()
            .zIndex(20f)
            .padding(10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(androidx.compose.ui.graphics.Color(0xFF7F1D1D))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        androidx.compose.foundation.layout.Column {
            androidx.tv.material3.Text(
                text = "ÖNCEKİ AÇILIŞTA ÇÖKME (kurulu: v${BuildConfig.VERSION_NAME}) — fotoğrafla, herhangi bir tuş kapatır",
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = 13.sp,
            )
            satirlar.take(8).forEach {
                androidx.tv.material3.Text(
                    // 160 karakter VerifyError mesajini tam ortasindan kesiyordu —
                    // asil neden ("register vN has type ...") kesilen kisimdaydi.
                    text = it.take(400),
                    color = androidx.compose.ui.graphics.Color(0xFFFECACA),
                    fontSize = 11.sp,
                )
            }
            TouchButton("Kapat", onKapat)
        }
    }
}
