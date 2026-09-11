package com.evaitec.netmovies.tv.ui

import android.net.Uri
import android.view.KeyEvent
import com.evaitec.netmovies.tv.input.NmBackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.evaitec.netmovies.tv.data.Library
import com.evaitec.netmovies.tv.data.MediaItem
import com.evaitec.netmovies.tv.data.Network
import com.evaitec.netmovies.tv.data.PlaybackLog
import com.evaitec.netmovies.tv.data.languageLabel
import com.evaitec.netmovies.tv.data.loggedOrNull
import com.evaitec.netmovies.tv.data.StreamLink
import com.evaitec.netmovies.tv.data.guessSubtitleLang
import com.evaitec.netmovies.tv.input.KeyBindings
import com.evaitec.netmovies.tv.input.RemoteAction
import com.evaitec.netmovies.tv.input.RemoteInputController
import com.evaitec.netmovies.tv.ui.theme.NmColor
import com.evaitec.netmovies.tv.ui.theme.NmDim
import com.evaitec.netmovies.tv.ui.theme.NmType
import com.evaitec.netmovies.tv.ui.theme.nmFocusRing
import com.evaitec.netmovies.tv.ui.theme.nmPlayerScrim
import kotlinx.coroutines.delay
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items

// Oynatma hızı seçenekleri (çark → Hız).
private val SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(item: MediaItem, bindings: KeyBindings, library: Library, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exo = remember {
        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
    }
    // Preview (scrub önizleme) oynatıcısı: aynı kaynak, düşük kalite, duraklatılmış,
    // hızlı seek (CLOSEST_SYNC). Küçük bir surface'e render edilip thumbnail gibi gösterilir.
    val previewExo = remember {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            playWhenReady = false
            setSeekParameters(SeekParameters.CLOSEST_SYNC)
        }
    }

    var error by remember { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }
    var retryKey by remember { mutableIntStateOf(0) }

    // Çoklu kaynak. Kuyruk arama sürerken büyür: ilk çalışan link hemen oynar,
    // kalan sağlayıcılar arka planda taranır (bkz. SourceResolver).
    var links by remember { mutableStateOf<List<StreamLink>>(emptyList()) }
    var currentLinkIndex by remember { mutableIntStateOf(0) }
    // Kaynak geçişinde konum taşıyıcısı: yeni kaynak `prepare()` ile 0'dan başlar ve
    // devam-etme yalnız ilk hazır oluşta uygulanır (resumeApplied) — bu ikisi
    // birleşince 40. dakikada kaynak düşünce film BAŞA dönüyordu. Geçişten önce
    // konum buraya yazılır, yeni kaynak hazırlanırken geri verilir.
    var carryOverMs by remember(item.url) { mutableLongStateOf(0L) }
    // Kuyruk tükendiğinde otomatik tazeleme sayacı. Anahtarsız `remember`: hata
    // dinleyicisi (DisposableEffect(exo)) aynı nesneye yazsın; içerik/bölüm değişiminde
    // aşağıdaki ayrı efekt sıfırlar.
    var autoRefresh by remember { mutableIntStateOf(0) }
    // Ekranda gösterilen durum satırı — hata kutusu yerine. Kullanıcı ekranda
    // bekler, çıkmak isterse GERİ tuşuna kendi basar.
    var status by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }

    // Oynatıcı UI durumu.
    var showSettings by remember { mutableStateOf(false) }
    // Ayarlar → Kaynak raporu: son denemelerin cihazda okunabilir dökümü.
    var showReport by remember { mutableStateOf(false) }
    // Gezinme ekranı: sarma / dakikaya git / bölüm — tam ekran, okunur.
    var showSeek by remember { mutableStateOf(false) }
    var tracks by remember { mutableStateOf<Tracks?>(null) }
    var speed by remember { mutableFloatStateOf(1.0f) }
    // Kalite: true = otomatik (ExoPlayer bant genişliğine göre seçer).
    var qualityAuto by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }
    var controlsTick by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var seekHint by remember { mutableStateOf<String?>(null) }
    var hintTick by remember { mutableIntStateOf(0) }

    // Bölüm durumu: onDispose içindeki ilerleme kaydı da okuduğu için oynatıcı
    // kurulumundan ÖNCE tanımlı olmalı.
    var episodes by remember { mutableStateOf<List<com.evaitec.netmovies.tv.data.EpisodeItem>>(emptyList()) }
    // Telefon bölüm seçtiyse oradan başlar; yoksa 0.
    var currentEpIndex by remember(item.url) { mutableIntStateOf(item.episode.coerceAtLeast(0)) }
    // Başlangıç paneli bilgi alanı (özet, yıl, tür, puan) — load_item'dan, tek istek.
    var details by remember(item.url) { mutableStateOf<com.evaitec.netmovies.tv.data.ItemDetails?>(null) }
    LaunchedEffect(item.url) {
        details = runCatching { Network.api.loadItem(item.plugin, item.url).result }.getOrNull()
    }

    // Başlangıç paneli: içerik açılır açılmaz gelir ve çözümleme bitene kadar
    // ekranda kalır. Odak OYNAT'ta; bölüm ve kaynak/dil aynı panelde. Kullanıcı
    // OYNAT'a basmadan akış başlamaz — yanlış içeriğe girip izlemeye başlamak yok.
    var showStartPanel by remember(item.url) { mutableStateOf(!item.autoplay) }
    // OYNAT'a panel açıkken basıldıysa: kaynak henüz yokken de kabul edilir,
    // hazır olduğu anda başlar.
    var playRequested by remember(item.url) { mutableStateOf(item.autoplay) }
    // Panelin OYNAT satırı için "nereden devam" bilgisi. Kayıt sunucuda; panel
    // çözümlemeyi beklemeden gösterilebilsin diye ayrıca burada okunuyor.
    var resumeLabel by remember(item.url) { mutableStateOf<String?>(null) }

    // Scrub / önizleme modu.
    var scrubMode by remember { mutableStateOf(false) }
    var scrubPos by remember { mutableLongStateOf(0L) }
    var scrubTick by remember { mutableIntStateOf(0) }

    val rootFocus = remember { FocusRequester() }
    val panelFocus = remember { FocusRequester() }

    // ---- Aksiyon dağıtıcı: eşlenen tuş → oynatıcı davranışı ----
    fun flashControls() { showControls = true; controlsTick++ }
    fun seekBy(deltaMs: Long) {
        val dur = exo.duration
        val target = (exo.currentPosition + deltaMs).let {
            if (dur > 0) it.coerceIn(0, dur) else it.coerceAtLeast(0)
        }
        exo.seekTo(target)
        position = target
        seekHint = (if (deltaMs > 0) "+" else "−") + "${kotlin.math.abs(deltaMs) / 1000}sn"
        hintTick++
        flashControls()
    }
    fun enterScrub() {
        scrubPos = exo.currentPosition
        scrubMode = true
        scrubTick++          // preview'ı mevcut pozisyona seek et
        showControls = true
    }
    // Dokunmatik: ilerleme çubuğuna dokununca o orana atla.
    fun seekToFraction(f: Float) {
        val d = exo.duration
        if (d > 0) {
            val target = (d * f).toLong().coerceIn(0, d)
            exo.seekTo(target)
            position = target
            flashControls()
        }
    }
    fun dispatch(a: RemoteAction) {
        when (a) {
            RemoteAction.NONE -> Unit
            RemoteAction.PLAY_PAUSE -> { if (exo.isPlaying) exo.pause() else exo.play(); flashControls() }
            RemoteAction.SEEK_FWD_10 -> seekBy(10_000)
            RemoteAction.SEEK_BACK_10 -> seekBy(-10_000)
            RemoteAction.SEEK_FWD_60 -> seekBy(60_000)
            RemoteAction.SEEK_BACK_60 -> seekBy(-60_000)
            RemoteAction.SEEK_HOLD_FWD -> seekBy(8_000)
            RemoteAction.SEEK_HOLD_BACK -> seekBy(-8_000)
            RemoteAction.OPEN_SETTINGS -> showSettings = true
            RemoteAction.SHOW_CONTROLS -> flashControls()
            RemoteAction.TOGGLE_SCRUB -> enterScrub()
            RemoteAction.BACK -> onBack()
        }
    }
    val controller = remember { RemoteInputController(bindings, scope) { dispatch(it) } }

    // Telefon kumandasının oynatma komutları. Tuşlar (D-pad, geri) sentetik KeyEvent
    // olarak zaten aşağıdaki onKeyEvent'ten akıyor; burada yalnız tuş karşılığı
    // olmayan eylemler var: serbest saniyeyle sarma ve durdurup çıkma.
    LaunchedEffect(Unit) {
        com.evaitec.netmovies.tv.data.RemoteBus.komutlar.collect { cmd ->
            if (cmd.type != "transport") return@collect
            when (cmd.action) {
                "play_pause" -> dispatch(RemoteAction.PLAY_PAUSE)
                "seek" -> seekBy((cmd.value * 1000).toLong())
                "stop" -> onBack()
            }
        }
    }

    // Scrub modunda D-pad: ◀/▶ imleç, OK atla, Geri iptal (native olayları doğrudan işlenir).
    fun handleScrubKey(e: KeyEvent): Boolean {
        if (e.action != KeyEvent.ACTION_DOWN) return true
        when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { scrubPos = (scrubPos - 10_000).coerceAtLeast(0); scrubTick++ }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val d = exo.duration
                scrubPos = (scrubPos + 10_000).let { if (d > 0) it.coerceAtMost(d) else it }
                scrubTick++
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                exo.seekTo(scrubPos); scrubMode = false; flashControls()
            }
            KeyEvent.KEYCODE_BACK -> scrubMode = false
            else -> Unit
        }
        return true
    }

    // Ayar menüsü açıksa Geri onu kapatsın; kontroller görünürse gizlesin; yoksa çık.
    // Kaynak aranırken/bulunamadığında da Geri doğrudan çıkar — ekranda tutan
    // bir hata kutusu yok.
    NmBackHandler(enabled = true) {
        when {
            scrubMode -> scrubMode = false
            // Başlangıç panelinde GERİ = içerikten çık: panel oynatmanın önündeki
            // ilk adım, kapatıp boş ekranda kalmanın anlamı yok.
            showStartPanel -> onBack()
            showSettings -> showSettings = false
            showControls -> showControls = false
            else -> onBack()
        }
    }

    DisposableEffect(previewExo) {
        onDispose { previewExo.release() }
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                // Kaynak gerçekten açılınca bant kalkar; yoksa "sıradaki deneniyor"
                // yazısı film oynarken ekranda asılı kalıyordu.
                if (state == Player.STATE_READY) { ready = true; status = null }
            }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlayerError(e: PlaybackException) {
                // Otomatik kaynak geçişi: çalmayan link kullanıcıyı ekrandan atmaz,
                // sessizce sıradaki denenir. Kuyruk bittiyse arama sürüyorsa beklenir.
                val failed = links.getOrNull(currentLinkIndex)
                PlaybackLog.fail(
                    "oynatma",
                    "${failed?.let { languageLabel(it) } ?: "kaynak"} açılmadı · ${e.errorCodeName}: ${e.message ?: "-"}",
                )
                if (links.size > currentLinkIndex + 1) {
                    // Kaldığın yer korunur: yeni kaynak aynı dakikadan devam eder.
                    carryOverMs = exo.currentPosition.coerceAtLeast(0L)
                    currentLinkIndex++
                    val next = links[currentLinkIndex]
                    status = "Kaynak açılmadı, sıradaki deneniyor (${currentLinkIndex + 1}/${links.size}) · ${languageLabel(next)}"
                } else if (searching) {
                    status = "Kaynak açılmadı, başka sağlayıcı aranıyor…"
                } else if (autoRefresh < MAX_AUTO_REFRESH) {
                    // Proxy jetonu bayatlayınca kuyruktaki TÜM linkler aynı anda ölür —
                    // hepsi aynı jetonla üretilmiştir, sıradakini denemek de çare değil.
                    // "Bulunamadı" ekranında durmak yerine bağlantıyı tazele: konum
                    // korunur, kaynaklar sıfırdan çözümlenir (taze jeton gelir).
                    carryOverMs = exo.currentPosition.coerceAtLeast(0L)
                    autoRefresh++
                    status = "Bağlantı tazeleniyor…"
                    PlaybackLog.warn("kuyruk", "kaynak kalmadı · otomatik tazeleme $autoRefresh/$MAX_AUTO_REFRESH")
                    retryKey++
                } else {
                    status = "Çalışan kaynak bulunamadı — çıkmak için GERİ tuşuna bas."
                }
            }
            override fun onTracksChanged(t: Tracks) { tracks = t }
        }
        exo.addListener(listener)
        onDispose {
            // Konumu release'den ÖNCE al: sonrasında currentPosition sıfırlanır.
            library.saveProgress(
                item,
                exo.currentPosition / 1000.0,
                exo.duration.coerceAtLeast(0) / 1000.0,
                currentEpIndex,
                isSerie = episodes.isNotEmpty(),
            )
            // Devam Et rafı ve ilerleme çubuğu ancak sunucudan tazelenince güncellenir;
            // yoksa ana ekran izlemeden önceki hâlini gösteriyordu.
            library.sync()
            exo.removeListener(listener)
            exo.release()
        }
    }

    // Oynatılan içeriği İzlenenler'e ekle (isim ile satır olarak görünür).
    LaunchedEffect(item.plugin, item.url) { library.addWatched(item) }

    // Telefon kumandasına "şu an oynayan": 5 sn'de bir ad + konum + süre. Sunucu 20 sn
    // bildirim almazsa şeridi düşürür; ekrandan çıkınca döngü de biter.
    LaunchedEffect(item.url) {
        while (true) {
            val sure = exo.duration
            if (sure > 0) runCatching {
                Network.api.remoteState(
                    title = item.title.orEmpty(),
                    position = exo.currentPosition / 1000.0,
                    duration = sure / 1000.0,
                    playing = exo.isPlaying,
                    plugin = item.plugin,
                    url = com.evaitec.netmovies.tv.data.rawUrl(item.url),
                    poster = item.poster.orEmpty(),
                )
            }
            delay(5000)
        }
    }

    // Devam bilgisi paneli beklemez: çözümleme sürerken okunur, 30sn–%92 aralığı
    // oynatıcıdaki devam kuralıyla aynı — panelde "devam" yazıp sonra baştan
    // başlaması olmasın.
    LaunchedEffect(item.url) {
        // content_key tür-agnostik (watch_store.py): tip bilinmeden de kayıt bulunur.
        val row = library.loadProgress(item.title.orEmpty()) ?: return@LaunchedEffect
        val savedMs = (row.positionSeconds * 1000).toLong()
        val durMs   = (row.durationSeconds * 1000).toLong()
        if (savedMs > 30_000 && (durMs <= 0 || savedMs < durMs * 0.92)) {
            val bolum = row.episode.takeIf { it.isNotBlank() }?.let { "$it · " } ?: ""
            resumeLabel = bolum + fmtTime(savedMs)

            // Telefondan gönderilen içerik onayı atlıyordu (`autoplay`), ama yarım
            // kalmış bir kayıt varsa atlanacak bir soru VAR: kaldığın yerden mi,
            // baştan mı (Dean: "direkt The Ark başladı... sorması lazım, bitirdim
            // belki"). Akış henüz başlamadıysa panel geri açılır; kullanıcı zaten
            // OYNAT'a basacaksa bir tuş, yanlış yerden başlamak ise geri alınamaz.
            if (playRequested && exo.currentPosition <= 0L) {
                playRequested = false
                exo.playWhenReady = false
                showStartPanel = true
            }
        }
    }

    // Tazeleme hakkı yalnız içerik/bölüm değişince yenilenir. retryKey'i anahtara
    // KOYMA: tazeleme sayacı kendi tetiklediği efektte sıfırlanırsa döngü kapanmaz.
    LaunchedEffect(item.url, currentEpIndex) { autoRefresh = 0 }

    // Kaynak kuyruğu — zincir SUNUCUDA (/api/v1/resolve_sources).
    // İstemci yalnız iki çağrı yapar: önce fast (seçili sağlayıcı, hemen oynasın),
    // sonra full (alternatif sağlayıcılar, arka planda kuyruğa eklenir).
    // Arama/eşleştirme/dil sıralaması burada TEKRARLANMAZ — TV, telefon ve web
    // aynı listeyi aynı sırada görür.
    LaunchedEffect(item.url, currentEpIndex, retryKey) {
        error = null
        ready = false
        links = emptyList()
        currentLinkIndex = 0
        searching = true
        status = "Kaynak aranıyor…"
        PlaybackLog.startSession(item.title, item.plugin)

        fun absorb(result: com.evaitec.netmovies.tv.data.ResolveResult?, phase: String) {
            if (result == null) return
            // Sunucunun teşhis kaydı istemci günlüğüne karışır: rapor tek yerde okunur.
            result.diagnostics.forEach { d ->
                when (d.level) {
                    "fail" -> PlaybackLog.fail("sunucu·${d.stage}", d.message)
                    "warn" -> PlaybackLog.warn("sunucu·${d.stage}", d.message)
                    else -> PlaybackLog.info("sunucu·${d.stage}", d.message)
                }
            }
            if (episodes.isEmpty() && result.episodes.isNotEmpty()) episodes = result.episodes

            val known = links.map { it.url }.toSet()
            val fresh = result.sources.filter { it.url.isNotBlank() && it.url !in known }
            if (fresh.isEmpty()) {
                PlaybackLog.info("kuyruk", "$phase · yeni kaynak yok")
                return
            }
            // Oynayan link yerinde kalır; sunucu sırası kuyruğun kalanına uygulanır.
            links = links.take(currentLinkIndex + 1) + links.drop(currentLinkIndex + 1) + fresh
            PlaybackLog.info("kuyruk", "$phase · +${fresh.size} kaynak (toplam ${links.size})")
        }

        // 1) Hızlı yol — seçili sağlayıcı.
        status = "${item.plugin} deneniyor…"
        val fast = loggedOrNull("çözümleme", "resolve_sources · fast") {
            Network.api.resolveSources(
                plugin = item.plugin,
                encodedUrl = item.url,
                title = item.title,
                episode = currentEpIndex,
                mode = "fast",
            ).result
        }
        absorb(fast, "fast")
        if (links.isNotEmpty()) status = null else status = "Alternatif sağlayıcılar aranıyor…"

        // 2) Tam zincir — alternatif sağlayıcılar (sunucu tarar).
        val full = loggedOrNull("çözümleme", "resolve_sources · full") {
            Network.api.resolveSources(
                plugin = item.plugin,
                encodedUrl = item.url,
                title = item.title,
                episode = currentEpIndex,
                mode = "full",
            ).result
        }
        absorb(full, "full")

        searching = false
        if (links.isEmpty()) {
            PlaybackLog.fail("sonuç", "hiçbir sağlayıcı oynatılabilir kaynak vermedi")
            status = "Bu içerik için çalışan kaynak bulunamadı — çıkmak için GERİ tuşuna bas."
        } else {
            if (status != null) status = null
            PlaybackLog.info("sonuç", "${links.size} kaynak hazır · oynatılan: ${languageLabel(links[currentLinkIndex])}")
        }
    }

    // Seçili kaynağı hazırla.
    // Anahtar OYNAYAN linkin URL'i — `links` listesi değil. Liste `absorb()` ile
    // büyüdüğünde (full taraması alternatifleri kuyruğa ekler) aynı kaynak yeniden
    // `prepare()` ediliyor ve video BAŞA dönüyordu; kaldığın yerden devam da öyle
    // uygulanıp hemen sıfırlanıyordu.
    val currentLinkUrl = links.getOrNull(currentLinkIndex)?.url
    LaunchedEffect(currentLinkUrl, retryKey) {
        val link = links.getOrNull(currentLinkIndex) ?: return@LaunchedEffect
        if (link.url.isBlank()) {
            // Boş link kullanıcıya hata kutusu göstermez; sıradakine geçilir.
            PlaybackLog.warn("oynatma", "boş link atlandı: ${link.name}")
            if (links.size > currentLinkIndex + 1) currentLinkIndex++
            return@LaunchedEffect
        }
        PlaybackLog.info("oynatma", "deneniyor: ${languageLabel(link)}")
        qualityAuto = true   // önceki akışın track override'ı yeni akışta geçersiz
        try {
            ready = false; error = null
            val headers = buildMap { if (link.referer.isNotBlank()) put("Referer", link.referer) }
            val ua = link.userAgent.ifBlank { "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_5)" }
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(ua)
                .setDefaultRequestProperties(headers)
                .setAllowCrossProtocolRedirects(true)

            val hls = HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(ExoMediaItem.fromUri(link.url))

            val subSources = link.subtitles
                .filter { it.url.isNotBlank() }
                .map { sub ->
                    val cfg = ExoMediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                        .setMimeType(guessSubtitleMime(sub.url))
                        .setLabel(sub.name.ifBlank { "Altyazı" })
                        .setLanguage(guessSubtitleLang(sub.name))
                        .setSelectionFlags(0)
                        .build()
                    SingleSampleMediaSource.Factory(dataSourceFactory).createMediaSource(cfg, C.TIME_UNSET)
                }

            val source = if (subSources.isEmpty()) hls else MergingMediaSource(hls, *subSources.toTypedArray())
            exo.setMediaSource(source)
            exo.prepare()
            if (carryOverMs > 0) {
                exo.seekTo(carryOverMs)
                position = carryOverMs
                carryOverMs = 0L
            }
            // Panel açıkken hazırlanır ama oynamaz: kullanıcı OYNAT'a bastığında
            // (playRequested) akış zaten buffer'lanmış olur, bekleme kısalır.
            exo.playWhenReady = playRequested
            exo.setPlaybackSpeed(speed)

            // Preview oynatıcısı: aynı kaynak (ayrı MediaSource örneği), en düşük kalite.
            val previewHls = HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(ExoMediaItem.fromUri(link.url))
            previewExo.setMediaSource(previewHls)
            previewExo.prepare()
            previewExo.playWhenReady = false
            previewExo.trackSelectionParameters = previewExo.trackSelectionParameters.buildUpon()
                .setMaxVideoSize(426, 240)
                .setForceLowestBitrate(true)
                .build()
        } catch (e: Exception) {
            // Hazırlama hatası da sessiz geçiş: sıradaki kaynak denenir — ama loglanır.
            PlaybackLog.fail("oynatma", "hazırlanamadı: ${languageLabel(link)}", e)
            if (links.size > currentLinkIndex + 1) {
                currentLinkIndex++
                status = "Kaynak açılmadı, sıradaki deneniyor (${currentLinkIndex + 1}/${links.size})…"
            } else if (!searching) {
                status = "Çalışan kaynak bulunamadı — çıkmak için GERİ tuşuna bas."
            }
        }
    }

    // Scrub imleci değişince preview'ı seek et (debounce ~120ms).
    LaunchedEffect(scrubTick) {
        if (scrubMode) { delay(120); runCatching { previewExo.seekTo(scrubPos) } }
    }
    // Scrub modunda 6sn hareketsizlikte çık.
    LaunchedEffect(scrubTick, scrubMode) {
        if (scrubMode) { delay(6000); scrubMode = false }
    }

    // Konum takibi.
    LaunchedEffect(ready) {
        while (true) {
            position = exo.currentPosition
            duration = exo.duration.coerceAtLeast(0)
            delay(500)
        }
    }

    // Kaldığın yerden devam — kayıt SUNUCUDA (telefonda bıraktığın yer TV'de açılır).
    // Yalnız ilk hazır oluşta ve 30sn–%92 aralığında uygulanır: başlangıçtaki birkaç
    // saniye ve neredeyse biten içerik atlanır.
    var resumeApplied by remember(item.url) { mutableStateOf(false) }
    LaunchedEffect(ready, item.url) {
        if (!ready || resumeApplied) return@LaunchedEffect
        resumeApplied = true
        val row = library.loadProgress(item.title.orEmpty(), episodes.isNotEmpty())
            ?: return@LaunchedEffect
        val savedMs = (row.positionSeconds * 1000).toLong()
        val dur = exo.duration
        if (savedMs > 30_000 && (dur <= 0 || savedMs < dur * 0.92)) {
            exo.seekTo(savedMs)
            position = savedMs
            seekHint = "▶ ${fmtTime(savedMs)} konumundan devam"
            hintTick++
        }
    }

    // Periyodik kayıt: 15 sn. Sunucu upsert yapıyor, tek satır güncellenir.
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        while (true) {
            delay(15_000)
            library.saveProgress(
                item,
                exo.currentPosition / 1000.0,
                exo.duration.coerceAtLeast(0) / 1000.0,
                currentEpIndex,
                isSerie = episodes.isNotEmpty(),
            )
        }
    }

    // Kontrol overlay otomatik gizleme.
    LaunchedEffect(controlsTick, showControls) {
        if (showControls) { delay(3500); showControls = false }
    }
    // Sarma göstergesi otomatik gizleme.
    LaunchedEffect(hintTick) {
        if (seekHint != null) { delay(900); seekHint = null }
    }

    // Odak sahipliği: kök kutu odaklı değilse D-pad tuşları controller'a HİÇ gelmez —
    // ilk basış odağı taşımakla harcanıyor, kullanıcı "iki kere basınca giriyor" diyordu.
    // Tek `requestFocus()` ilk karede henüz yerleşmemiş düğümde sessizce başarısız
    // oluyordu (ModalCard'da aynı sorun kare kare denemeyle çözülmüştü).
    LaunchedEffect(showSettings, showSeek, scrubMode, ready) {
        if (showSeek) return@LaunchedEffect   // gezinme ekranı odağı kendi alır
        repeat(10) {
            val target = if (showSettings) panelFocus else rootFocus
            if (runCatching { target.requestFocus() }.isSuccess) return@LaunchedEffect
            withFrameNanos { }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            // Video zemini SAF SİYAH: uygulamanın morumsu arka planı 21:9 içerikte
            // kenarlarda şerit gibi görünüyordu (Dean: "mor oynatıcı çerçevesi").
            .background(androidx.compose.ui.graphics.Color.Black)
            .focusRequester(rootFocus)
            // GERİ'yi paneller AŞAĞI inmeden yakala. TV Material odak grubu GERİ'yi
            // "ilk öğeye dön" diye yutuyordu: odak OYNAT'a atlıyor, BackHandler hiç
            // çalışmıyordu (Dean: "play'e döndürüyor ama geri çıkmıyor").
            // onPreviewKeyEvent kökten aşağı ilk çalışan yoldur.
            // Yalnız bu durum ele alınır; diğer panellerin kendi işleyicileri var.
            .onPreviewKeyEvent { ke ->
                if (!showStartPanel || ke.nativeKeyEvent.keyCode != KeyEvent.KEYCODE_BACK) {
                    return@onPreviewKeyEvent false
                }
                if (ke.nativeKeyEvent.action == KeyEvent.ACTION_UP) onBack()
                true
            }
            .onKeyEvent { ke ->
                when {
                    // Hata ekranında tuşları tüketme: overlay butonları (Tekrar dene / Geri)
                    // arası d-pad navigasyonu ve BACK, Compose'a serbest kalsın.
                    error != null -> false
                    // Telefon kumandasındaki "Menü": oynatıcı ayarlarını (altyazı,
                    // kalite, kaynak) açar. TV kumandalarının çoğunda bu tuş yok,
                    // bu yüzden buton eşlemesine değil doğrudan buraya bağlı.
                    ke.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU -> {
                        if (ke.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) showSettings = true
                        true
                    }
                    scrubMode -> handleScrubKey(ke.nativeKeyEvent)
                    // Uzun basışla açılan panelin, tuş bırakılırken kendi kendini
                    // kapatmasını engeller — bırakma olayı controller'a aittir.
                    controller.consumesPendingUp(ke.nativeKeyEvent) -> true
                    // Bölüm seçici de bir modal: tuşlar yutulunca liste hiç hareket
                    // etmiyordu (Dean: "bölüm seçimi açılıyor, hareket etmiyor").
                    showSettings || showSeek || showStartPanel -> false
                    else -> controller.process(ke.nativeKeyEvent)
                }
            }
            .focusable()
            // Dokunmatik (telefon): videoya dokun → kontrolleri aç/kapat.
            .pointerInput(Unit) {
                detectTapGestures {
                    if (!showSettings && !scrubMode) {
                        if (showControls) showControls = false else flashControls()
                    }
                }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exo
                    useController = false             // tüm kontrol bizde (buton-eşleme)
                    keepScreenOn = true
                    isFocusable = false
                    isFocusableInTouchMode = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Sarma göstergesi (ortada, geçici).
        seekHint?.let {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.clip(RoundedCornerShape(NmDim.PanelRadius)).background(NmColor.Scrim)
                        .padding(horizontal = 28.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = it,
                        fontWeight = FontWeight.Bold,
                        fontSize = NmType.ScreenTitle,
                        color = NmColor.OnSurface,
                    )
                }
            }
        }

        // Kontrol overlay: dokunmatikte etkileşimli butonlar; D-pad'de görsel bilgi.
        if (showControls && !scrubMode) {
            ControlsOverlay(
                isPlaying = isPlaying,
                position = position,
                duration = duration,
                onPlayPause = { if (exo.isPlaying) exo.pause() else exo.play(); flashControls() },
                onSeekBack = { seekBy(-10_000) },
                onSeekFwd = { seekBy(10_000) },
                onOpenSettings = { showSettings = true },
                onScrub = { enterScrub() },
                onSeekToFraction = { seekToFraction(it) },
            )
        }

        // Scrub / önizleme overlay'i (thumbnail = preview oynatıcı karesi).
        if (scrubMode) {
            ScrubOverlay(previewExo = previewExo, scrubPos = scrubPos, duration = duration)
        }

        if (showSeek) {
            SeekScreen(
                position = position,
                duration = duration,
                episodes = episodes,
                currentEpIndex = currentEpIndex,
                onSeekBy = { delta -> seekBy(delta) },
                onSeekTo = { target -> exo.seekTo(target); position = target },
                onSelectEpisode = { idx -> currentEpIndex = idx },
                onClose = { showSeek = false },
            )
        }

        if (showStartPanel) {
            StartPanel(
                title = item.title.orEmpty(),
                details = details,
                rating = item.rating,
                episodes = episodes,
                currentEpIndex = currentEpIndex,
                links = links,
                currentLinkIndex = currentLinkIndex,
                resumeLabel = resumeLabel,
                hazir = links.isNotEmpty(),
                // Bölüme basmak DOĞRUDAN başlatır: seçtikten sonra panelin
                // tepesindeki OYNAT'a dönmek fazladan bir yolculuktu (Dean).
                onSelect = { idx ->
                    currentEpIndex = idx
                    playRequested = true
                    showStartPanel = false
                    exo.playWhenReady = true
                },
                onSelectLink = { idx -> currentLinkIndex = idx },
                onPlay = { playRequested = true; showStartPanel = false; exo.playWhenReady = true },
                onOpenSettings = { showSettings = true },
            )
        }

        if (showSettings) {
            SettingsPanel(
                links = links,
                currentLinkIndex = currentLinkIndex,
                episodes = episodes,
                currentEpIndex = currentEpIndex,
                tracks = tracks,
                speed = speed,
                panelFocus = panelFocus,
                isFavorite = library.isFavorite(item),
                onToggleFavorite = { library.toggleFavorite(item) },
                onSelectSource = { idx -> currentLinkIndex = idx; showSettings = false },
                onSelectEpisode = { epIdx -> currentEpIndex = epIdx; showSettings = false },
                onSelectAudio = { group, trackIndex ->
                    exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex)))
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                        .build()
                },
                onSelectSubtitle = { group, trackIndex ->
                    exo.trackSelectionParameters = if (group == null) {
                        exo.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                    } else {
                        exo.trackSelectionParameters.buildUpon()
                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex)))
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
                    }
                },
                onOpenSeek = { showSettings = false; showSeek = true },
                qualityAuto = qualityAuto,
                onSelectQuality = { group, trackIndex ->
                    qualityAuto = group == null
                    exo.trackSelectionParameters = if (group == null) {
                        // Otomatik: override kalkar, ExoPlayer bant genişliğine göre seçer.
                        exo.trackSelectionParameters.buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
                    } else {
                        exo.trackSelectionParameters.buildUpon()
                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex)))
                            .build()
                    }
                },
                onSelectSpeed = { s -> speed = s; exo.setPlaybackSpeed(s) },
                showReport = showReport,
                onToggleReport = { showReport = !showReport },
                onClose = { showSettings = false },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        // Hata kutusu yok: kullanıcı ekranda kalır, ne olduğunu okur, çıkmak
        // isterse GERİ tuşuna kendisi basar. "Tekrar dene" düğmesi gerekmiyor —
        // sıradaki kaynağa geçiş kendiliğinden yapılıyor.
        when {
            !ready && !showSettings -> Overlay(status ?: "Yükleniyor…")
            status != null && !showSettings -> StatusBanner(status!!)
        }
    }
}

// .vtt / .srt uzantısından MIME tahmini (bilinmiyorsa VTT).
private fun guessSubtitleMime(url: String): String {
    val u = url.lowercase()
    return when {
        u.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
        u.endsWith(".ass") || u.endsWith(".ssa") -> MimeTypes.TEXT_SSA
        else -> MimeTypes.TEXT_VTT
    }
}

private fun fmtTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ControlsOverlay(
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekFwd: () -> Unit,
    onOpenSettings: () -> Unit,
    onScrub: () -> Unit,
    onSeekToFraction: (Float) -> Unit,
) {
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Box(Modifier.fillMaxSize()) {
        // Sağ üst: mod butonları (önizleme / ayarlar) — TV güvenli alan içinde.
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(horizontal = NmDim.SafeH, vertical = NmDim.SafeV),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextPill("Önizleme", onScrub)
            TextPill("Ayarlar", onOpenSettings)
        }

        // Alt kontrol çubuğu: degrade zemin + ilerleme + ortalanmış kontrol grubu.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(nmPlayerScrim)
                .padding(horizontal = NmDim.SafeH, vertical = NmDim.SafeV),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // İlerleme çubuğu — dokununca o orana atla.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .pointerInput(duration) {
                        detectTapGestures { o ->
                            if (size.width > 0) onSeekToFraction((o.x / size.width).coerceIn(0f, 1f))
                        }
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                        .background(NmColor.TrackIdle),
                ) {
                    Box(
                        Modifier.fillMaxWidth(fraction).height(5.dp).clip(RoundedCornerShape(3.dp))
                            .background(NmColor.Primary),
                    )
                }
            }
            // Tek satır: geçen süre — kontrol grubu (ortada) — toplam süre.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = fmtTime(position) + "  ·  −" + fmtTime((duration - position).coerceAtLeast(0)),
                    color = NmColor.OnSurfaceMuted,
                    fontSize = NmType.Caption,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBtn(Icons.Filled.Replay10, 40.dp, 26.dp, onSeekBack)
                    IconBtn(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        52.dp, 32.dp, onPlayPause, accent = true,
                    )
                    IconBtn(Icons.Filled.Forward10, 40.dp, 26.dp, onSeekFwd)
                }
                Text(fmtTime(duration), color = NmColor.OnSurfaceMuted, fontSize = NmType.Caption)
            }
        }
    }
}

// Küçük yuvarlak ikon buton (vektör; renk tint → emoji/sarı yok). pointerInput tap →
// D-pad focus'unu bozmaz. accent=true → dolu mor (oynat/duraklat).
@Composable
private fun IconBtn(icon: ImageVector, box: androidx.compose.ui.unit.Dp, ic: androidx.compose.ui.unit.Dp, onTap: () -> Unit, accent: Boolean = false) {
    Box(
        modifier = Modifier
            .size(box)
            .clip(CircleShape)
            .background(if (accent) NmColor.Primary else NmColor.ScrimSoft)
            .pointerInput(Unit) { detectTapGestures { onTap() } },
        contentAlignment = Alignment.Center,
    ) {
        Image(icon, contentDescription = null, modifier = Modifier.size(ic), colorFilter = ColorFilter.tint(NmColor.OnPrimary))
    }
}

// Küçük metin pill (önizleme/ayarlar). Emoji yok.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TextPill(label: String, onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(NmDim.PillRadius))
            .background(NmColor.ScrimSoft)
            .pointerInput(Unit) { detectTapGestures { onTap() } }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(label, color = NmColor.OnSurface, fontSize = NmType.Caption, fontWeight = FontWeight.Medium)
    }
}

// Kuyruk tükendiğinde kaç kez otomatik yeniden çözümleme yapılır. Ölü kaynakta
// sonsuz döngüye girmemek için sınırlı.
private const val MAX_AUTO_REFRESH = 2


// Scrub/önizleme overlay'i: küçük preview oynatıcı karesi (thumbnail) imleç konumunda +
// ilerleme çubuğu. Süre imlecin altında.
@OptIn(UnstableApi::class, ExperimentalTvMaterial3Api::class)
@Composable
private fun ScrubOverlay(previewExo: ExoPlayer, scrubPos: Long, duration: Long) {
    val fraction = if (duration > 0) (scrubPos.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(nmPlayerScrim)
                .padding(horizontal = NmDim.SafeH, vertical = NmDim.SafeV),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "⏱  Önizleme — ◀ ▶ gez · OK atla · Geri iptal",
                fontWeight = FontWeight.SemiBold,
                fontSize = NmType.Label,
                color = NmColor.OnSurface,
            )
            BoxWithConstraints(Modifier.fillMaxWidth().height(150.dp)) {
                val thumbW = 220.dp
                val offsetX = (maxWidth - thumbW) * fraction
                Box(
                    modifier = Modifier
                        .offset(x = offsetX)
                        .width(thumbW)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(NmDim.CardRadius))
                        .background(Color.Black)
                        .nmFocusRing(true, RoundedCornerShape(NmDim.CardRadius)),
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = previewExo
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    Text(
                        text = fmtTime(scrubPos),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = NmType.Caption,
                        color = NmColor.OnSurface,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .background(NmColor.Scrim)
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
            Box(
                Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(NmColor.TrackIdle),
            ) {
                Box(
                    Modifier.fillMaxWidth(fraction).height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(NmColor.Primary),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmtTime(scrubPos), color = NmColor.OnSurfaceMuted, fontSize = NmType.Caption)
                Text(fmtTime(duration), color = NmColor.OnSurfaceMuted, fontSize = NmType.Caption)
            }
        }
    }
}

@OptIn(UnstableApi::class, ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsPanel(
    links: List<StreamLink>,
    currentLinkIndex: Int,
    episodes: List<com.evaitec.netmovies.tv.data.EpisodeItem> = emptyList(),
    currentEpIndex: Int = 0,
    tracks: Tracks?,
    speed: Float,
    panelFocus: FocusRequester,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onSelectSource: (Int) -> Unit,
    onSelectEpisode: (Int) -> Unit = {},
    onSelectAudio: (Tracks.Group, Int) -> Unit,
    onSelectSubtitle: (Tracks.Group?, Int) -> Unit,
    qualityAuto: Boolean,
    onOpenSeek: () -> Unit,
    onSelectQuality: (Tracks.Group?, Int) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    showReport: Boolean,
    onToggleReport: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val videoGroups = tracks?.groups?.filter { it.type == C.TRACK_TYPE_VIDEO && it.length > 0 } ?: emptyList()
    // Bölüm tek varyantlı akışta da çizilir: gizlendiğinde "kalite ayarı yok" sanılıyordu.
    // Kaynakların çoğu tek çözünürlük veriyor (fastplay master.txt'te tek EXT-X-STREAM-INF);
    // o zaman liste tek satır olur ama hangi kalitede oynadığı görünür.
    val videoTrackCount = videoGroups.sumOf { it.length }
    val audioGroups = tracks?.groups?.filter { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 } ?: emptyList()
    val textGroups = tracks?.groups?.filter { it.type == C.TRACK_TYPE_TEXT && it.length > 0 } ?: emptyList()
    val textDisabled = textGroups.none { g -> (0 until g.length).any { g.isTrackSelected(it) } }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(NmDim.PanelWidth)
            .background(NmColor.SurfaceDialog)
            .border(
                width = 1.dp,
                color = NmColor.PrimaryHairline,
                shape = RoundedCornerShape(topStart = NmDim.PanelRadius, bottomStart = NmDim.PanelRadius),
            )
            .focusRequester(panelFocus)
            .focusGroup()
            .padding(horizontal = 22.dp, vertical = NmDim.SafeV),
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(NmDim.ItemGap),
        ) {
            // Sıra kuralı sabit: Türkçe dublaj → Türkçe altyazı → dil bilinmiyor.
            // Etiket her satırda yazar, hangi dilin oynadığı tahmine bırakılmaz.
            SectionTitle("📺 Sağlayıcı & Kaynak")
            if (links.isEmpty()) MutedRow("—")
            links.forEachIndexed { idx, link ->
                SettingRow(languageLabel(link), idx == currentLinkIndex) { onSelectSource(idx) }
            }

            if (episodes.isNotEmpty()) {
                SectionTitle("📑 Bölümler (${episodes.size})")
                episodes.forEachIndexed { idx, ep ->
                    SettingRow(episodeLabel(ep, idx), idx == currentEpIndex) { onSelectEpisode(idx) }
                }
            }

            SectionTitle("🧭 Gezinme")
            SettingRow("Sarma · dakikaya git · bölüm", false) { onOpenSeek() }

            SectionTitle("🩺 Kaynak raporu")
            SettingRow(if (showReport) "▾ Gizle" else "▸ Son denemeleri göster", showReport, onToggleReport)
            if (showReport) {
                val report = PlaybackLog.snapshot()
                if (report.isEmpty()) MutedRow("Kayıt yok")
                report.take(40).forEach { entry -> MutedRow(entry.format()) }
            }

            if (videoTrackCount > 0) {
                SectionTitle("🎚 Kalite")
                SettingRow("Otomatik", qualityAuto) { onSelectQuality(null, 0) }
                videoGroups.forEach { group ->
                    for (i in 0 until group.length) {
                        val fmt = group.getTrackFormat(i)
                        val label = when {
                            fmt.height > 0 -> "${fmt.height}p"
                            fmt.bitrate > 0 -> "${fmt.bitrate / 1000} kbps"
                            else -> "Kalite ${i + 1}"
                        }
                        SettingRow(label, !qualityAuto && group.isTrackSelected(i)) {
                            onSelectQuality(group, i)
                        }
                    }
                }
            }

            if (audioGroups.isNotEmpty()) {
                SectionTitle("🔊 Ses Dili")
                audioGroups.forEach { group ->
                    for (i in 0 until group.length) {
                        val fmt = group.getTrackFormat(i)
                        SettingRow(fmt.label ?: fmt.language ?: "Ses ${i + 1}", group.isTrackSelected(i)) {
                            onSelectAudio(group, i)
                        }
                    }
                }
            }

            if (textGroups.isNotEmpty()) {
                SectionTitle("💬 Altyazı")
                SettingRow("Kapalı", textDisabled) { onSelectSubtitle(null, 0) }
                textGroups.forEach { group ->
                    for (i in 0 until group.length) {
                        val fmt = group.getTrackFormat(i)
                        SettingRow(fmt.label ?: fmt.language ?: "Altyazı ${i + 1}", group.isTrackSelected(i)) {
                            onSelectSubtitle(group, i)
                        }
                    }
                }
            }

            SectionTitle("⚡ Hız")
            SPEEDS.forEach { s ->
                SettingRow(if (s == 1.0f) "Normal" else "${s}x", s == speed) { onSelectSpeed(s) }
            }

            SectionTitle("⭐ Kitaplık")
            SettingRow(
                if (isFavorite) "★ Favorilerden çıkar" else "☆ Favorilere ekle",
                isFavorite,
                onToggleFavorite,
            )

            androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
            SettingRow("✕ Kapat", false, onClose)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
// Bölüm etiketi: kaynak başlığı bölüm numarasını taşımıyor ("Red Flags"), sezon/bölüm
// bilgisi ayrı alanlarda geliyor. İkisi birleşmezse listede hangi bölüm olduğu okunmuyor.
private fun episodeLabel(ep: com.evaitec.netmovies.tv.data.EpisodeItem, index: Int): String {
    val numara = ep.episode?.let { "S${ep.season}B$it" } ?: "Bölüm ${index + 1}"
    val ad     = ep.title?.takeIf { it.isNotBlank() }
    return if (ad != null) "$numara · $ad" else numara
}

// İçerik açılınca gelen başlangıç paneli. Odak doğrudan OYNAT'ta: kullanıcı
// çözümleme sürerken paneli okuyup tek OK ile başlatır, "play'i arama" yok.
// Bölüm listesi ve kaynak/dil aynı panelde — ayrı istek yok, liste zaten
// resolve_sources yanıtından geliyor.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StartPanel(
    title: String,
    details: com.evaitec.netmovies.tv.data.ItemDetails?,
    rating: Double?,
    episodes: List<com.evaitec.netmovies.tv.data.EpisodeItem>,
    currentEpIndex: Int,
    links: List<com.evaitec.netmovies.tv.data.StreamLink>,
    currentLinkIndex: Int,
    resumeLabel: String?,
    hazir: Boolean,
    onSelect: (Int) -> Unit,
    onSelectLink: (Int) -> Unit,
    onPlay: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    // Sezon rafı: seçili bölümün sezonu açık gelir; SOL/SAĞ sezon, YUKARI/AŞAĞI bölüm.
    val seasons = remember(episodes) { episodes.map { it.season }.distinct().sorted() }
    var season by remember(episodes, currentEpIndex) {
        mutableIntStateOf(episodes.getOrNull(currentEpIndex)?.season ?: seasons.firstOrNull() ?: 1)
    }
    val bilgi = listOfNotNull(
        details?.yearText?.takeIf { it.isNotBlank() },
        details?.tagsText?.takeIf { it.isNotBlank() },
        (details?.ratingText?.takeIf { it.isNotBlank() } ?: rating?.let { "%.1f".format(it) })?.let { "★ $it" },
    ).joinToString("  ·  ")
    val playFocus = remember { FocusRequester() }
    // Tek requestFocus ilk karede sessizce düşüyor; birkaç kare denenir.
    LaunchedEffect(Unit) {
        repeat(6) {
            withFrameNanos {}
            if (runCatching { playFocus.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    // Odak nöbeti: liste yeniden oluşunca (bölümler geç gelir, sezon değişir) odak
    // hiçbir satırda kalmıyor ve D-pad ölüyordu (Dean: "cursor kayboluyor, bir daha
    // bir şey seçmiyor"). Panelin tamamı odaksız kalırsa OYNAT'a geri alınır.
    var panelOdakli by remember { mutableStateOf(false) }
    LaunchedEffect(panelOdakli, episodes.size, season) {
        if (panelOdakli) return@LaunchedEffect
        repeat(8) {
            withFrameNanos {}
            if (panelOdakli) return@LaunchedEffect
            if (runCatching { playFocus.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    // Oynat satırının ne yapacağı tek cümlede görünsün: yarım kalan varsa devam,
    // yeni diziye giriliyorsa 1. bölüm, filmde düz oynat.
    val playLabel = when {
        resumeLabel != null   -> "▶  Devam et — $resumeLabel"
        episodes.isNotEmpty() -> "▶  ${episodeLabel(episodes[currentEpIndex], currentEpIndex)} — baştan"
        else                  -> "▶  Oynat"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NmColor.Scrim)
            .onFocusChanged { panelOdakli = it.hasFocus }
            .focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.9f)
                .width(NmDim.PanelWidth * 1.6f)
                .clip(RoundedCornerShape(NmDim.PanelRadius))
                .background(NmColor.SurfaceDialog)
                .padding(horizontal = 26.dp, vertical = NmDim.SafeV),
            verticalArrangement = Arrangement.spacedBy(NmDim.ItemGap),
        ) {
            Text(
                text = title,
                fontSize = NmType.ScreenTitle,
                fontWeight = FontWeight.Bold,
                color = NmColor.OnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (bilgi.isNotBlank()) MutedRow(bilgi)
            // Dizide özet kısa tutulur: bölüm listesi kaydırmadan görünsün
            // (Dean: "kaç bölüm olduğu gözükmüyor").
            details?.description?.takeIf { it.isNotBlank() && it != "None" }?.let {
                Text(
                    text = it,
                    fontSize = NmType.Body,
                    color = NmColor.OnSurfaceMuted,
                    maxLines = if (episodes.isEmpty()) 3 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.focusRequester(playFocus)) {
                SettingRow(playLabel, true) { onPlay() }
            }
            if (!hazir) MutedRow("Kaynak aranıyor… OYNAT'a basabilirsin, hazır olunca başlar.")

            if (episodes.isNotEmpty()) {
                // Üstte sezon rafı (SOL/SAĞ), altta o sezonun bölümleri adlarıyla (YUKARI/AŞAĞI).
                if (seasons.size > 1) {
                    SectionTitle("📑 Sezon")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(NmDim.ItemGap)) {
                        items(seasons.size, key = { seasons[it] }) { i ->
                            val s = seasons[i]
                            Box(Modifier.width(110.dp)) { SettingRow("S$s", s == season) { season = s } }
                        }
                    }
                }
                val secili = episodes.withIndex().filter { it.value.season == season }
                SectionTitle("🎬 Bölümler (${secili.size})")
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(NmDim.ItemGap),
                ) {
                    items(secili.size, key = { "${secili[it].value.url}#${secili[it].index}" }) { i ->
                        val (idx, ep) = secili[i]
                        SettingRow(episodeLabel(ep, idx), idx == currentEpIndex) { onSelect(idx) }
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            // Dizide kaynak listesi paneli uzatıp bölümleri aşağı itiyordu; orada
            // yalnız ayarlar satırı kalır, kaynak seçimi ayar panelinden yapılır.
            if (links.isNotEmpty() && episodes.isEmpty()) {
                SectionTitle("🌐 Kaynak · dil")
                links.take(6).forEachIndexed { i, l ->
                    SettingRow(languageLabel(l), i == currentLinkIndex) { onSelectLink(i) }
                }
            }
            SettingRow("⚙  Kaynak · kalite · altyazı", false, onOpenSettings)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = NmColor.Primary,
        fontWeight = FontWeight.Bold,
        fontSize = NmType.RowTitle,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MutedRow(text: String) {
    Text(
        text = text,
        color = NmColor.OnSurfaceFaint,
        fontSize = NmType.Body,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingRow(label: String, selected: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(NmDim.RowRadius)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    isFocused -> NmColor.Primary
                    selected  -> NmColor.PrimarySelected
                    else      -> NmColor.Surface
                }
            )
            .nmFocusRing(isFocused, shape)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = (if (selected) "●  " else "     ") + label,
            fontSize = NmType.Label,
            color = if (isFocused) NmColor.OnPrimary else if (selected) NmColor.OnSurface else NmColor.OnSurfaceMuted,
            fontWeight = if (isFocused || selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Overlay(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.clip(RoundedCornerShape(NmDim.PanelRadius)).background(NmColor.ScrimSoft)
                .padding(horizontal = 26.dp, vertical = 14.dp),
        ) {
            Text(message, fontSize = NmType.Body, color = NmColor.OnSurface)
        }
    }
}

// Oynatma sürerken alt köşede görünen küçük durum satırı (kaynak geçişi vb.).
@Composable
private fun StatusBanner(message: String) {
    Box(Modifier.fillMaxSize().padding(NmDim.SafeArea), contentAlignment = Alignment.BottomStart) {
        Box(
            Modifier.clip(RoundedCornerShape(NmDim.PanelRadius)).background(NmColor.ScrimSoft)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(message, fontSize = NmType.Label, color = NmColor.OnSurface)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayerOverlay(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    onBack: () -> Unit,
) {
    val shape = RoundedCornerShape(NmDim.PanelRadius)
    Box(
        Modifier.fillMaxSize().background(NmColor.Scrim).padding(NmDim.SafeArea),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .clip(shape)
                .background(NmColor.SurfaceDialog)
                .padding(32.dp),
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = NmType.ScreenTitle, color = NmColor.Primary)
            Text(message, color = NmColor.OnSurfaceMuted, fontSize = NmType.Label)
            ActionRow(onAction = onAction, actionLabel = actionLabel, onBack = onBack)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ActionRow(onAction: () -> Unit, actionLabel: String, onBack: () -> Unit) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { firstFocus.requestFocus() }
    }
    Row(
        modifier = Modifier.focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TouchButton(actionLabel, onAction, modifier = Modifier.focusRequester(firstFocus), accent = true)
        TouchButton("Geri", onBack)
    }
}
