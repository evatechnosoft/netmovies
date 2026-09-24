package com.evaitec.netmovies.tv.ui.player2

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import com.evaitec.netmovies.tv.data.EpisodeItem
import com.evaitec.netmovies.tv.data.ItemDetails
import com.evaitec.netmovies.tv.data.Library
import com.evaitec.netmovies.tv.data.Markers
import com.evaitec.netmovies.tv.data.MediaItem
import com.evaitec.netmovies.tv.data.Network
import com.evaitec.netmovies.tv.data.OynatmaAyari
import com.evaitec.netmovies.tv.data.PlaybackLog
import com.evaitec.netmovies.tv.data.ResolveResult
import com.evaitec.netmovies.tv.data.SourceEvent
import com.evaitec.netmovies.tv.data.StreamLink
import com.evaitec.netmovies.tv.data.basliktanBolum
import com.evaitec.netmovies.tv.data.episodeIndexOf
import com.evaitec.netmovies.tv.data.episodeRef
import com.evaitec.netmovies.tv.data.guessSubtitleLang
import com.evaitec.netmovies.tv.data.languageLabel
import com.evaitec.netmovies.tv.data.loggedOrNull
import com.evaitec.netmovies.tv.ui.BOLUM_YOK
import com.evaitec.netmovies.tv.ui.KAYNAK_YOK
import com.evaitec.netmovies.tv.ui.MAX_AUTO_REFRESH
import com.evaitec.netmovies.tv.ui.MIN_GECERLI_SURE_MS
import com.evaitec.netmovies.tv.ui.NEXT_COUNTDOWN_SEC
import com.evaitec.netmovies.tv.ui.NEXT_EPISODE_WINDOW_MS
import com.evaitec.netmovies.tv.ui.episodeLabel
import com.evaitec.netmovies.tv.ui.fmtDelta
import com.evaitec.netmovies.tv.ui.fmtTime
import com.evaitec.netmovies.tv.ui.guessSubtitleMime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Basılı tutma adımı: ilk 1,5 sn 10 sn, 4 sn'ye kadar 30 sn, sonrası 1 dk. */
internal fun tutmaAdimi(heldMs: Long): Long = when {
    heldMs < 1_500 -> 10_000L
    heldMs < 4_000 -> 30_000L
    else -> 60_000L
}

/**
 * Bölüm sonu teklif penceresi (süre koşulu). Süre çok kısaysa (kısa klip) her konum
 * "bitmeye az kaldı" sayılırdı — MIN_GECERLI_SURE_MS bunu keser.
 */
internal fun teklifPenceresinde(duration: Long, position: Long): Boolean =
    duration >= MIN_GECERLI_SURE_MS && (duration - position) in 0..NEXT_EPISODE_WINDOW_MS

/** Birikimli sarma hedefi: süre biliniyorsa [0, süre], değilse ≥ 0. */
internal fun sarmaHedefi(base: Long, deltaMs: Long, dur: Long): Long =
    (base + deltaMs).let { if (dur > 0) it.coerceIn(0, dur) else it.coerceAtLeast(0) }

/**
 * Oynatıcı 2 — oynatma MANTIĞI (UI yok). Eski `PlayerScreen` 153-1791'deki kaynak
 * çözümleme/fallback, ExoPlayer yaşam döngüsü, ilerleme kaydı/devam, bölüm geçişi,
 * açılış/jenerik işaretleri buradadır. Sözleşme: docs/PLAYER2-PLAN.md.
 *
 * SAHİBİ: core ajanı. Diğer ajanlar yalnız okur/çağırır; alan/metot EKLEYEBİLİR
 * ama mevcut imzaları değiştiremez.
 */
class PlayerCore(
    val item: MediaItem,
    val library: Library,
    val exo: ExoPlayer,
) {
    // ---- kaynak / oynatma
    var links by mutableStateOf<List<StreamLink>>(emptyList())
    var currentLinkIndex by mutableIntStateOf(0)
    var ready by mutableStateOf(false)
    var searching by mutableStateOf(false)
    /** Köşe durum metni (KAYNAK_YOK, BOLUM_YOK, "Kaynak aranıyor…"). */
    var status by mutableStateOf<String?>(null)
    var isPlaying by mutableStateOf(false)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var tracks by mutableStateOf<Tracks?>(null)
    var speed by mutableFloatStateOf(1f)
    var qualityAuto by mutableStateOf(true)
    /** Önizleme oynatıcısı aynı başlıklarla (Referer/UA) çeksin diye. */
    var aktifFactory by mutableStateOf<DefaultHttpDataSource.Factory?>(null)
    /** Kısa süreli ipucu ("+30 sn", "Kaldığın yer 12:04"). UI 0,9 sn sonra siler. */
    var seekHint by mutableStateOf<String?>(null)
    var hintTick by mutableIntStateOf(0)

    // ---- UI kancaları (UI bağlar; çekirdek UI durumunu bilmez)
    /** Eski `flashControls()`: seekBy/playPause/tamponBasina/canliyaDon/seekToFraction çağırır. */
    var flashControls: () -> Unit = {}
    /**
     * Eski kodda panelOynat `panelAsList=false`, goToEpisode ayrıca `showControls=false`
     * yapıyordu. [gecis] = true → goToEpisode (kontroller de gizlenir).
     */
    var uiKapat: (gecis: Boolean) -> Unit = {}

    // ---- içerik / bölüm
    var details by mutableStateOf<ItemDetails?>(null)
    var episodes by mutableStateOf<List<EpisodeItem>>(emptyList())
    // Telefon bölüm seçtiyse oradan başlar; yoksa 0.
    private var epIndexState by mutableIntStateOf(item.episode.coerceAtLeast(0))
    /**
     * Bölüm değişince bölüme ait durumlar (işaret, geri sayım, iptal, akış bitti/geçersiz)
     * sıfırlanır — eski koddaki `remember(item.url, currentEpIndex)` karşılığı, eşzamanlı.
     */
    var currentEpIndex: Int
        get() = epIndexState
        set(v) {
            if (v != epIndexState) {
                markers = null
                geriSayim = null
                otoGecisIptal = false
                akisBitti = false
                akisGecersiz = false
            }
            epIndexState = v
        }
    var listeKaynagi by mutableStateOf<String?>(null)
    var resumeLabel by mutableStateOf<String?>(null)
    var resumeEpisode by mutableStateOf<Int?>(null)
    var playRequested by mutableStateOf(item.autoplay)
    /** Başlangıç paneli açık mı — C/Q mantığı bunu yazar (eski koddaki gibi). */
    var showStartPanel by mutableStateOf(!item.autoplay)

    // ---- işaretler / sonraki bölüm
    var markers by mutableStateOf<Markers?>(null)
    var geriSayim by mutableStateOf<Int?>(null)
    var otoGecisIptal by mutableStateOf(false)

    // ---- iç durum
    internal lateinit var scope: CoroutineScope
    // Kaynak geçişinde konum taşıyıcısı (bkz. eski PlayerScreen: 40. dakikada başa dönme).
    internal var carryOverMs by mutableLongStateOf(0L)
    internal var retryKey by mutableIntStateOf(0)
    internal var autoRefresh by mutableIntStateOf(0)
    /** Kaynak zinciri bölüm belli olunca başlar (eski PlayerScreen'deki detayHazir gerekçesi). */
    var detayHazir by mutableStateOf(false)
    /** Oynayan kaynak açılmadı, arama sürüyor: yeni kaynak gelince ona geçilir. */
    internal var siradakiBekleniyor by mutableStateOf(false)

    /** KAYNAK_YOK ekranındaki "Tekrar dene". */
    fun tekrarDene() { autoRefresh = 0; carryOverMs = 0L; retryKey++ }
    // Bölüm geçişi istendi, yeni kaynak henüz açılmadı. Bölüme göre SIFIRLANMAZ.
    internal var gecisBekleyen by mutableStateOf<Int?>(null)
    internal var akisBitti by mutableStateOf(false)
    internal var akisGecersiz by mutableStateOf(false)
    internal var seekTarget by mutableStateOf<Long?>(null)
    internal var seekJob by mutableStateOf<Job?>(null)
    private var lastHoldAt = 0L
    internal var aktifPlugin by mutableStateOf(item.plugin)
    internal var aktifUrl by mutableStateOf(item.url)
    internal val bildirilen = mutableSetOf<String>()
    internal var resumeApplied by mutableStateOf(false)
    internal var bolumAramasiYapildi by mutableStateOf(false)

    val currentLinkUrl: String? get() = links.getOrNull(currentLinkIndex)?.url

    val canliYayin: Boolean get() = exo.isCurrentMediaItemLive
    val nextEpIndex: Int? get() = (currentEpIndex + 1).takeIf { episodes.isNotEmpty() && it <= episodes.lastIndex }
    val prevEpIndex: Int? get() = (currentEpIndex - 1).takeIf { episodes.isNotEmpty() && it >= 0 }
    /** Oynatıcı üstünde "S2 B5 — Başlık" gibi etiket. */
    val simdikiEtiket: String?
        get() {
            val ad = item.title.orEmpty()
            val ep = episodes.getOrNull(currentEpIndex) ?: return ad
            return listOf(ad, "S${ep.season}B${ep.episode ?: (currentEpIndex + 1)}")
                .filter { it.isNotBlank() }.joinToString("  ·  ")
        }

    // Açılış aralığı ve jenerik başlangıcı — milisaniye.
    private val introBas: Long? get() = markers?.introStart?.let { (it * 1000).toLong() }
    private val introBit: Long? get() = markers?.introEnd?.let { (it * 1000).toLong() }
    private val jenerikBas: Long? get() = markers?.creditsStart?.let { (it * 1000).toLong() }
    /** ControlsOverlay için (Long ms). */
    val introRangeMs: Pair<Long, Long>?
        get() { val b = introBas; val e = introBit; return if (b != null && e != null) b to e else null }
    val creditsStartMs: Long? get() = jenerikBas
    /** Milisaniye (Float). ControlsOverlay Long istediği için [introRangeMs] tercih edilir. */
    val introRange: Pair<Float, Float>? get() = introRangeMs?.let { it.first.toFloat() to it.second.toFloat() }
    val creditsStart: Float? get() = jenerikBas?.toFloat()

    /**
     * Açılış şarkısı çalıyor mu. UI panellerini bilmez: çağıran
     * `&& !(showSettings || showSeek || scrubMode)` ekler (eski `!panelAcik`).
     */
    val acilisAtlanabilir: Boolean
        get() {
            val b = introBas; val e = introBit
            return b != null && e != null && position in b..e && !showStartPanel
        }

    private val jenerikte: Boolean
        get() = jenerikBas?.let { duration > 0 && position >= it } == true

    /** Geri sayım koşulu: jenerik başladı (işaret varsa) ya da akış bitti. */
    internal val sayimBaslasin: Boolean
        get() = nextEpIndex != null && !otoGecisIptal && !akisGecersiz &&
            gecisBekleyen == null && (jenerikte || akisBitti)

    /**
     * Son 70 sn / jenerik penceresi: SAĞ = sonraki bölüm. Jenerik işareti varsa kart
     * çıkmaz. UI panellerini bilmez: çağıran `&& !(showSettings || showSeek || scrubMode)` ekler.
     */
    val sonrakiTeklif: Boolean
        get() = nextEpIndex != null && jenerikBas == null && geriSayim == null &&
            !akisGecersiz && gecisBekleyen == null && teklifPenceresinde(duration, position) &&
            !showStartPanel

    // ---- eylemler
    /** Sarma bekliyorsa OK = "burada dur" (hedef hemen uygulanır); değilse oynat/duraklat. */
    fun playPause() {
        if (seekTarget != null) { seekJob?.cancel(); commitSeek(); flashControls() }
        else { if (exo.isPlaying) exo.pause() else exo.play(); flashControls() }
    }

    /** Birikimli seek (350 ms debounce, tek seekTo). */
    fun seekBy(deltaMs: Long) {
        val target = sarmaHedefi(seekTarget ?: exo.currentPosition, deltaMs, exo.duration)
        seekTarget = target
        position = target
        val ofset = target - exo.currentPosition
        seekHint = (if (ofset >= 0) "+" else "−") + fmtDelta(ofset) + "  →  " + fmtTime(target)
        hintTick++
        flashControls()
        seekJob?.cancel()
        seekJob = scope.launch { delay(350); commitSeek() }
    }

    /** Basılı tutma: yon=-1/+1, adım 10→30→60 sn. Tekrarlar 120 ms'de bire indirilir. */
    fun seekHold(yon: Int, heldMs: Long) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastHoldAt < 120) return
        lastHoldAt = now
        seekBy(yon * tutmaAdimi(heldMs))
    }

    fun commitSeek() {
        val t = seekTarget ?: return
        seekTarget = null
        exo.seekTo(t)
        position = t
    }

    fun seekTo(ms: Long) {
        exo.seekTo(ms)
        position = ms
    }

    fun seekToFraction(f: Float) {
        val d = exo.duration
        if (d > 0) {
            val target = (d * f).toLong().coerceIn(0, d)
            exo.seekTo(target)
            position = target
            flashControls()
        }
    }

    fun skipIntro() {
        val bit = introBit ?: return
        exo.seekTo(bit)
        position = bit
        seekHint = "⏭ Açılış atlandı"
        hintTick++
    }

    fun tamponBasina() {
        exo.seekTo(0)
        position = 0
        seekHint = "⏮ Tamponun başı"
        hintTick++
        flashControls()
    }

    fun canliyaDon() {
        exo.seekToDefaultPosition()
        position = exo.currentPosition
        seekHint = "⏭ Canlı"
        hintTick++
        flashControls()
    }

    fun goToEpisode(idx: Int) {
        // Geçiş sürerken ikinci istek YOK: tuş tekrarı 2-3 bölüm atlatıyordu.
        // Kilit STATE_READY'de ya da çözümleme bitince açılır.
        if (gecisBekleyen != null) return
        gecisBekleyen = idx
        carryOverMs = 0L
        currentEpIndex = idx
        playRequested = true
        showStartPanel = false
        uiKapat(true)
        exo.playWhenReady = true
    }

    /** Başlangıç panelindeki OYNAT: kayıtlı bölüm varsa ona geçip oynatır. */
    fun panelOynat() {
        val kayit = resumeEpisode
        if (resumeLabel != null && kayit != null && kayit != currentEpIndex && kayit in episodes.indices) {
            currentEpIndex = kayit
        }
        playRequested = true
        showStartPanel = false
        uiKapat(false)
        exo.playWhenReady = true
    }

    fun selectLink(idx: Int) { currentLinkIndex = idx }

    fun selectAudio(group: Tracks.Group, index: Int) {
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(index)))
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .build()
    }

    fun selectSubtitle(group: Tracks.Group?, index: Int) {
        exo.trackSelectionParameters = if (group == null) {
            exo.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        } else {
            exo.trackSelectionParameters.buildUpon()
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(index)))
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        }
    }

    fun selectQuality(group: Tracks.Group?, index: Int) {
        qualityAuto = group == null
        exo.trackSelectionParameters = if (group == null) {
            // Otomatik: override kalkar, ExoPlayer bant genişliğine göre seçer.
            exo.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
        } else {
            exo.trackSelectionParameters.buildUpon()
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(index)))
                .build()
        }
    }

    fun selectSpeed(s: Float) { speed = s; exo.setPlaybackSpeed(s) }

    /**
     * Bölümler sekmesi açıldı: en zengin bölüm listesini bir kez çek (episodesBest).
     * Daha uzun liste bulunursa oynatma da o sağlayıcıya geçer; şu anki bölüm
     * SEZON+BÖLÜM numarasıyla yeniden eşlenir.
     */
    fun requestBestEpisodes() {
        if (bolumAramasiYapildi || episodes.isEmpty()) return
        bolumAramasiYapildi = true
        scope.launch {
            val yanit = runCatching {
                Network.api.episodesBest(
                    title = item.title.orEmpty(),
                    plugin = aktifPlugin,
                    encodedUrl = aktifUrl,
                ).result
            }.getOrNull() ?: return@launch
            if (yanit.episodes.size <= episodes.size) return@launch

            val simdiki = episodes.getOrNull(currentEpIndex)
            episodes = yanit.episodes
            listeKaynagi = yanit.plugin
            if (yanit.plugin != aktifPlugin && yanit.encodedUrl.isNotBlank()) {
                aktifPlugin = yanit.plugin
                aktifUrl = com.evaitec.netmovies.tv.data.encodedUrl(yanit.encodedUrl)
            }
            if (simdiki?.episode != null) {
                val yeni = yanit.episodes.indexOfFirst {
                    it.season == simdiki.season && it.episode == simdiki.episode
                }
                if (yeni >= 0) currentEpIndex = yeni
            }
        }
    }

    // Kaynak sonucunu sunucuya bildir; aynı kaynak için tek kez.
    internal fun kaynakBildir(link: StreamLink?, oynadi: Boolean) {
        val plugin = link?.plugin?.takeIf { it.isNotBlank() } ?: return
        if (!bildirilen.add("$plugin:$oynadi")) return
        scope.launch { runCatching { Network.api.sourceEvent(SourceEvent(plugin = plugin, ok = oynadi)) } }
    }

    internal fun ilerlemeKaydet() {
        library.saveProgress(
            item,
            exo.currentPosition / 1000.0,
            exo.duration.coerceAtLeast(0) / 1000.0,
            episodeRef(
                episodes.getOrNull(currentEpIndex)?.season,
                episodes.getOrNull(currentEpIndex)?.episode,
                currentEpIndex,
            ),
            isSerie = episodes.isNotEmpty(),
        )
    }
}

/**
 * Tüm yan etkiler (LaunchedEffect/DisposableEffect) burada kurulur; dönen çekirdek
 * `item.url` değişince yenilenir. Dispose: canlı değilse saveProgress → sync → release.
 * [onExit]: KAYNAK_YOK ve hiç oynamadıysa 2,5 sn sonra çağrılır.
 *
 * ExoPlayer ekran ömrü boyunca TEK (eski kodla aynı): kanal geçişinde (item.url)
 * çekirdek yenilenir, oynatıcı aynı kalır.
 */
@OptIn(UnstableApi::class)
@Composable
fun rememberPlayerCore(item: MediaItem, library: Library, onExit: () -> Unit): PlayerCore {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Cihaza özel: tunneling desteği televizyonun kod çözücüsünün meselesi.
    val oynaticiPrefs = remember { context.getSharedPreferences("player", android.content.Context.MODE_PRIVATE) }
    val tunneling = remember { mutableStateOf(oynaticiPrefs.getBoolean("tunneling", true)) }
    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setTunnelingEnabled(tunneling.value)
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setExceedVideoConstraintsIfNecessary(true)
            )
        }
    }
    val exo = remember {
        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .setTrackSelector(trackSelector)
            // Ev ağı için büyük tampon: tek yavaş segment sese yansımasın.
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(30_000, 90_000, 3_000, 6_000)
                    .setBackBuffer(20_000, true)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()
            )
            .build()
    }
    val core = remember(item.url) { PlayerCore(item, library, exo).also { it.scope = scope } }
    val coreRef = rememberUpdatedState(core)
    val onExitRef = rememberUpdatedState(onExit)

    // Tunneling'i kalıcı kapat: desteklemeyen cihaz her açılışta yeniden denenmemeli.
    fun tunnelingKapat(neden: String) {
        tunneling.value = false
        oynaticiPrefs.edit().putBoolean("tunneling", false).apply()
        trackSelector.setParameters(trackSelector.buildUponParameters().setTunnelingEnabled(false))
        PlaybackLog.warn("oynatma", "tunneling kapatıldı · $neden")
    }

    // C — ayrıntı + bölüm listesi zincirden ÖNCE; bölüm sayfası kartı; kayıttan bölüm.
    LaunchedEffect(core) {
      val c = core
      try {
        c.details = runCatching {
            Network.api.loadItem(c.aktifPlugin, c.aktifUrl, item.title, item.mediaType.ifBlank { null }).result
        }.getOrNull()
        val bolumler = c.details?.episodes.orEmpty()
        if (bolumler.isNotEmpty() && c.episodes.isEmpty()) c.episodes = bolumler

        // Kart BÖLÜM sayfası olabilir: adresle, olmazsa başlıkla eşle.
        if (item.episode < 0 && bolumler.isNotEmpty()) {
            val acilan = com.evaitec.netmovies.tv.data.rawUrl(item.url).trimEnd('/')
            var sira = bolumler.indexOfFirst { it.url.trimEnd('/') == acilan }
            if (sira < 0) sira = basliktanBolum(item.title, bolumler)
            if (sira >= 0) {
                c.currentEpIndex = sira
                c.showStartPanel = false
                c.playRequested = true
                exo.playWhenReady = true
            }
        }

        // `episode = -1` = "kayıttan/baştan": kayıttaki bölümden devam.
        if (item.episode < 0 && bolumler.isNotEmpty() && c.currentEpIndex == 0) {
            val kayit = library.loadProgress(item.title.orEmpty(), isSerie = true)
            episodeIndexOf(kayit?.episode.orEmpty(), bolumler)?.let { c.currentEpIndex = it }
        }

        // Telefondan gelen dizi, bölüm seçilmediyse TV'de panelle karar verilir.
        if (bolumler.isNotEmpty() && item.autoplay && item.episode < 0 && exo.currentPosition <= 0L) {
            c.playRequested = false
            exo.playWhenReady = false
            c.showStartPanel = true
        }
      } finally {
        c.detayHazir = true
      }
    }

    // K — yönetim panelindeki kalite tavanı; süreç ömrü boyunca bir kez çekilir.
    LaunchedEffect(Unit) {
        if (!OynatmaAyari.okundu) {
            runCatching { Network.api.clientConfig().result.defaultQuality }
                .onSuccess { OynatmaAyari.kaliteTavani = it; OynatmaAyari.okundu = true }
        }
        OynatmaAyari.tavanBoyutu()?.let { (g, y) ->
            trackSelector.setParameters(trackSelector.buildUponParameters().setMaxVideoSize(g, y))
            PlaybackLog.info("kalite", "tavan: ${OynatmaAyari.kaliteTavani}p (${g}x$y)")
        }
    }

    // M — oynatıcı dinleyicileri + dispose. Dinleyici HER ZAMAN güncel çekirdeğe yazar.
    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                val c = coreRef.value
                if (state == Player.STATE_READY) {
                    // Kısa akış = kaldırılmış bölüm klibi olabilir; canlı muaf.
                    val sure = exo.duration
                    val cokKisa = !exo.isCurrentMediaItemLive && sure in 1 until MIN_GECERLI_SURE_MS
                    if (cokKisa) {
                        val kaynak = c.links.getOrNull(c.currentLinkIndex)
                        PlaybackLog.warn(
                            "oynatma",
                            "${kaynak?.let { languageLabel(it) } ?: "kaynak"} çok kısa (${sure}ms) · " +
                                "kaldırılmış bölüm klibi olabilir",
                        )
                        c.kaynakBildir(kaynak, false)
                        if (c.links.size > c.currentLinkIndex + 1) {
                            // Kısa klibin konumu sonraki kaynağa TAŞINMAZ.
                            c.carryOverMs = 0L
                            c.currentLinkIndex++
                            val next = c.links[c.currentLinkIndex]
                            c.status = "Kaynak çok kısa, sıradaki deneniyor (${c.currentLinkIndex + 1}/${c.links.size}) · ${languageLabel(next)}"
                            c.ready = false
                        } else {
                            // Kuyrukta başka kaynak yok: DUR, otomatik sonraki bölüme GEÇME.
                            c.akisGecersiz = true
                            c.ready = false
                            c.status = BOLUM_YOK
                        }
                    } else {
                        c.ready = true; c.status = null
                        c.gecisBekleyen = null   // yeni bölüm açıldı: geçiş kilidi kalkar
                        c.kaynakBildir(c.links.getOrNull(c.currentLinkIndex), true)
                    }
                }
                // Bölüm bitti: yalnız işaret konur, geri sayım efekti devreye girer.
                if (state == Player.STATE_ENDED && !c.akisGecersiz && c.gecisBekleyen == null) c.akisBitti = true
            }

            override fun onIsPlayingChanged(playing: Boolean) { coreRef.value.isPlaying = playing }

            override fun onPlayerError(e: PlaybackException) {
                val c = coreRef.value
                // Kod çözücü/ses hattı hatası: tunneling kalıcı kapanır, AYNI kaynak yeniden.
                val kodCozucuHatasi = e.errorCode in setOf(
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FAILED,
                    PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
                    PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
                )
                if (tunneling.value && kodCozucuHatasi) {
                    tunnelingKapat("kod çözücü hatası: ${e.errorCodeName}")
                    c.carryOverMs = exo.currentPosition.coerceAtLeast(0L)
                    c.retryKey++
                    return
                }
                val failed = c.links.getOrNull(c.currentLinkIndex)
                PlaybackLog.fail(
                    "oynatma",
                    "${failed?.let { languageLabel(it) } ?: "kaynak"} açılmadı · ${e.errorCodeName}: ${e.message ?: "-"}",
                )
                c.kaynakBildir(failed, false)
                if (c.links.size > c.currentLinkIndex + 1) {
                    c.carryOverMs = exo.currentPosition.coerceAtLeast(0L)
                    c.currentLinkIndex++
                    val next = c.links[c.currentLinkIndex]
                    c.status = "Kaynak açılmadı, sıradaki deneniyor (${c.currentLinkIndex + 1}/${c.links.size}) · ${languageLabel(next)}"
                } else if (c.searching) {
                    c.siradakiBekleniyor = true
                    c.carryOverMs = exo.currentPosition.coerceAtLeast(0L)
                    c.status = "Kaynak açılmadı, başka sağlayıcı aranıyor…"
                } else if (c.autoRefresh < MAX_AUTO_REFRESH) {
                    // Proxy jetonu bayatlayınca tüm linkler birlikte ölür: bağlantıyı tazele.
                    c.carryOverMs = exo.currentPosition.coerceAtLeast(0L)
                    c.autoRefresh++
                    c.status = "Bağlantı tazeleniyor…"
                    PlaybackLog.warn("kuyruk", "kaynak kalmadı · otomatik tazeleme ${c.autoRefresh}/$MAX_AUTO_REFRESH")
                    c.retryKey++
                } else {
                    c.status = KAYNAK_YOK
                }
            }

            override fun onTracksChanged(t: Tracks) { coreRef.value.tracks = t }
        }
        // Ses kesilmesi teşhisi: underrun / sink hatası / biçim değişimi → Kaynak raporu.
        val sesDinleyici = object : AnalyticsListener {
            override fun onAudioUnderrun(
                eventTime: AnalyticsListener.EventTime,
                bufferSize: Int,
                bufferSizeMs: Long,
                elapsedSinceLastFeedMs: Long,
            ) {
                PlaybackLog.warn(
                    "ses",
                    "tampon boşaldı · ${bufferSizeMs}ms tampon · son beslemeden ${elapsedSinceLastFeedMs}ms",
                )
            }

            override fun onAudioSinkError(eventTime: AnalyticsListener.EventTime, audioSinkError: Exception) {
                PlaybackLog.fail("ses", "çıkış hatası", audioSinkError)
                if (tunneling.value) tunnelingKapat("ses çıkışı hatası")
            }

            override fun onAudioInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: androidx.media3.common.Format,
                decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?,
            ) {
                PlaybackLog.info(
                    "ses",
                    "biçim: ${format.sampleMimeType ?: "?"} · ${format.bitrate}bps · " +
                        "${format.channelCount}ch · ${format.sampleRate}Hz · " +
                        "kod çözücü ${if (decoderReuseEvaluation?.result == 0) "yeniden kuruldu" else "korundu"}",
                )
            }
        }

        exo.addListener(listener)
        exo.addAnalyticsListener(sesDinleyici)
        onDispose {
            // Konumu release'den ÖNCE al. Canlı yayın kaydedilmez.
            if (!exo.isCurrentMediaItemLive) coreRef.value.ilerlemeKaydet()
            library.sync()
            exo.removeListener(listener)
            exo.removeAnalyticsListener(sesDinleyici)
            exo.release()
        }
    }

    // N — İzlenenler.
    LaunchedEffect(item.plugin, item.url) { library.addWatched(item) }

    // O — telefon kumandasına "şu an oynayan", 5 sn'de bir.
    LaunchedEffect(core) {
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

    // P — oynatma günlüğü sunucuya (ilk 6 sn, sonra 30 sn; kapanışta son hâl).
    LaunchedEffect(core) {
        var sonKayit: String? = null
        suspend fun gonder() {
            val satirlar = PlaybackLog.snapshot().map { it.format() }
            if (satirlar.isNotEmpty() && satirlar.first() != sonKayit) {
                sonKayit = satirlar.first()
                runCatching { Network.api.clientLog(mapOf("lines" to satirlar)) }
            }
        }
        try {
            delay(6_000)
            while (true) {
                gonder()
                delay(30_000)
            }
        } finally {
            withContext(NonCancellable) { gonder() }
        }
    }

    // Q — devam bilgisi paneli beklemez; yarım kayıt varsa autoplay'de de panel sorar.
    LaunchedEffect(core, core.episodes) {
        val c = core
        val row = library.loadProgress(item.title.orEmpty()) ?: return@LaunchedEffect
        val savedMs = (row.positionSeconds * 1000).toLong()
        val durMs = (row.durationSeconds * 1000).toLong()
        if (savedMs > 30_000 && (durMs <= 0 || savedMs < durMs * 0.92)) {
            c.resumeEpisode = episodeIndexOf(row.episode, c.episodes)
            val bolum = c.resumeEpisode
                ?.let { c.episodes.getOrNull(it) }
                ?.let { episodeLabel(it, c.resumeEpisode ?: 0) + " · " }
                ?: ""
            c.resumeLabel = bolum + fmtTime(savedMs)
            if (c.playRequested && exo.currentPosition <= 0L) {
                c.playRequested = false
                exo.playWhenReady = false
                c.showStartPanel = true
            }
        }
    }

    // R — tazeleme hakkı yalnız içerik/bölüm değişince yenilenir (retryKey anahtarda DEĞİL).
    LaunchedEffect(core, core.aktifUrl, core.currentEpIndex) { core.autoRefresh = 0 }

    // S — kaynak kuyruğu: zincir SUNUCUDA (fast → full).
    LaunchedEffect(core, core.aktifUrl, core.aktifPlugin, core.currentEpIndex, core.retryKey, core.detayHazir) {
        val c = core
        if (!c.detayHazir) return@LaunchedEffect
        c.siradakiBekleniyor = false
        c.ready = false
        c.links = emptyList()
        c.currentLinkIndex = 0
        c.searching = true
        c.status = "Kaynak aranıyor…"
        PlaybackLog.startSession(item.title, item.plugin)
        PlaybackLog.info("açılış", if (item.autoplay) "uzak komut / onay (autoplay)" else "kullanıcı seçimi")

        fun absorb(result: ResolveResult?, phase: String) {
            if (result == null) return
            result.diagnostics.forEach { d ->
                when (d.level) {
                    "fail" -> PlaybackLog.fail("sunucu·${d.stage}", d.message)
                    "warn" -> PlaybackLog.warn("sunucu·${d.stage}", d.message)
                    else -> PlaybackLog.info("sunucu·${d.stage}", d.message)
                }
            }
            if (c.episodes.isEmpty() && result.episodes.isNotEmpty()) c.episodes = result.episodes

            val known = c.links.map { it.url }.toSet()
            val fresh = result.sources.filter { it.url.isNotBlank() && it.url !in known }
            if (fresh.isEmpty()) {
                PlaybackLog.info("kuyruk", "$phase · yeni kaynak yok")
                return
            }
            // Oynayan link yerinde kalır.
            val ilkYeni = c.links.size
            c.links = c.links.take(c.currentLinkIndex + 1) + c.links.drop(c.currentLinkIndex + 1) + fresh
            if (c.siradakiBekleniyor) {
                c.siradakiBekleniyor = false
                c.currentLinkIndex = ilkYeni
                c.status = "Sıradaki kaynak deneniyor (${ilkYeni + 1}/${c.links.size}) · ${languageLabel(c.links[ilkYeni])}"
            }
            PlaybackLog.info("kuyruk", "$phase · +${fresh.size} kaynak (toplam ${c.links.size})")
        }

        // Bölüm seçiliyse bölümün KENDİ adresi (indeks değil).
        val bolumUrl = c.episodes.getOrNull(c.currentEpIndex)?.url?.takeIf { it.isNotBlank() }
        val cozumUrl = bolumUrl ?: c.aktifUrl

        c.status = "${c.aktifPlugin} deneniyor…"
        val fast = loggedOrNull("çözümleme", "resolve_sources · fast") {
            Network.api.resolveSources(
                plugin = c.aktifPlugin,
                encodedUrl = cozumUrl,
                title = item.title,
                episode = c.currentEpIndex,
                mode = "fast",
            ).result
        }
        absorb(fast, "fast")
        c.status = if (c.links.isNotEmpty()) null else "Alternatif sağlayıcılar aranıyor…"

        val full = loggedOrNull("çözümleme", "resolve_sources · full") {
            Network.api.resolveSources(
                plugin = c.aktifPlugin,
                encodedUrl = cozumUrl,
                title = item.title,
                episode = c.currentEpIndex,
                mode = "full",
            ).result
        }
        absorb(full, "full")

        c.searching = false
        // Zincir bitti: kilit mutlaka kalkar.
        c.gecisBekleyen = null
        if (c.links.isEmpty()) {
            PlaybackLog.fail("sonuç", "hiçbir sağlayıcı oynatılabilir kaynak vermedi")
            c.status = KAYNAK_YOK
        } else {
            if (c.status != null) c.status = null
            PlaybackLog.info("sonuç", "${c.links.size} kaynak hazır · oynatılan: ${languageLabel(c.links[c.currentLinkIndex])}")
        }
    }

    // T — seçili kaynağı hazırla. Anahtar OYNAYAN linkin URL'i (liste büyüyünce başa dönmesin).
    LaunchedEffect(core, core.currentLinkUrl, core.retryKey) {
        val c = core
        val link = c.links.getOrNull(c.currentLinkIndex) ?: return@LaunchedEffect
        if (link.url.isBlank()) {
            PlaybackLog.warn("oynatma", "boş link atlandı: ${link.name}")
            if (c.links.size > c.currentLinkIndex + 1) c.currentLinkIndex++
            return@LaunchedEffect
        }
        PlaybackLog.info("oynatma", "deneniyor: ${languageLabel(link)}")
        c.qualityAuto = true   // önceki akışın track override'ı yeni akışta geçersiz
        // Tavan oynatıcıya ait: geçişte de geçerli kalır.
        OynatmaAyari.tavanBoyutu()?.let { (g, y) ->
            trackSelector.setParameters(trackSelector.buildUponParameters().setMaxVideoSize(g, y))
        }
        try {
            c.ready = false
            val headers = buildMap { if (link.referer.isNotBlank()) put("Referer", link.referer) }
            val ua = link.userAgent.ifBlank { "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_5)" }
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(ua)
                .setDefaultRequestProperties(headers)
                .setAllowCrossProtocolRedirects(true)
            c.aktifFactory = dataSourceFactory

            val hls = HlsMediaSource.Factory(dataSourceFactory)
                // Tek segment hatası kaynağı düşürmesin: üç deneme.
                .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(3))
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
            if (c.carryOverMs > 0) {
                exo.seekTo(c.carryOverMs)
                c.position = c.carryOverMs
                c.carryOverMs = 0L
            }
            // Panel açıkken hazırlanır ama oynamaz.
            exo.playWhenReady = c.playRequested
            exo.setPlaybackSpeed(c.speed)
            // Dublaj kaynakta altyazı kapalı başlar.
            exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, com.evaitec.netmovies.tv.data.isDubbed(link))
                .build()
            // Önizleme oynatıcısı UI'da: `currentLinkUrl` değişince kendisi bırakır.
        } catch (e: Exception) {
            PlaybackLog.fail("oynatma", "hazırlanamadı: ${languageLabel(link)}", e)
            if (c.links.size > c.currentLinkIndex + 1) {
                c.currentLinkIndex++
                c.status = "Kaynak açılmadı, sıradaki deneniyor (${c.currentLinkIndex + 1}/${c.links.size})…"
            } else if (!c.searching) {
                c.status = "Çalışan kaynak bulunamadı — çıkmak için GERİ tuşuna bas."
            }
        }
    }

    // V — konum takibi. Sarma bekliyorken konum HEDEFİ gösterir.
    LaunchedEffect(core, core.ready) {
        val c = core
        while (true) {
            if (c.seekTarget == null) c.position = exo.currentPosition
            c.duration = exo.duration.coerceAtLeast(0)
            delay(500)
        }
    }

    // W — kaldığın yerden devam: ilk hazır oluşta, 30sn–%92, yalnız aynı bölümün kaydı.
    LaunchedEffect(core, core.ready) {
        val c = core
        if (!c.ready || c.resumeApplied) return@LaunchedEffect
        c.resumeApplied = true
        val row = library.loadProgress(item.title.orEmpty(), c.episodes.isNotEmpty()) ?: return@LaunchedEffect
        val kayitIdx = episodeIndexOf(row.episode, c.episodes)
        if (c.episodes.isNotEmpty() && kayitIdx != c.currentEpIndex) return@LaunchedEffect
        val savedMs = (row.positionSeconds * 1000).toLong()
        val dur = exo.duration
        if (savedMs > 30_000 && (dur <= 0 || savedMs < dur * 0.92)) {
            exo.seekTo(savedMs)
            c.position = savedMs
            c.seekHint = "▶ ${fmtTime(savedMs)} konumundan devam"
            c.hintTick++
        }
    }

    // X — periyodik kayıt: 15 sn; canlı yayın kaydedilmez.
    LaunchedEffect(core, core.ready) {
        if (!core.ready) return@LaunchedEffect
        while (true) {
            delay(15_000)
            if (exo.isCurrentMediaItemLive) continue
            core.ilerlemeKaydet()
        }
    }

    // AB — kaynak bulunamadı → oku ve çık; oynamış içerik kapanmaz.
    LaunchedEffect(core, core.status) {
        if (core.status != KAYNAK_YOK) return@LaunchedEffect
        if (core.position > 0L) return@LaunchedEffect
        // Kapanmıyor: ekran KaynakYokEkrani ile "Tekrar dene" sunar (Dean, 24 Eylül).
    }

    // AC — işaretler: süre öğrenilir öğrenilmez, kaynağın altyazısından.
    LaunchedEffect(core, core.currentLinkUrl, core.currentEpIndex, core.duration > 0) {
        val c = core
        if (c.duration <= 0) return@LaunchedEffect
        val altyazi = c.links.getOrNull(c.currentLinkIndex)?.subtitles
            ?.firstOrNull { it.url.isNotBlank() } ?: return@LaunchedEffect
        c.markers = runCatching {
            Network.api.markers(altyazi.url, c.duration / 1000.0).result
        }.onFailure {
            PlaybackLog.warn("isaret", "işaret alınamadı: ${it.message ?: "-"}")
        }.getOrNull()
        c.markers?.let {
            PlaybackLog.info(
                "isaret",
                "açılış=${it.introStart?.toInt() ?: "-"}–${it.introEnd?.toInt() ?: "-"} " +
                    "jenerik=${it.creditsStart?.toInt() ?: "-"} (${it.source ?: "-"})",
            )
        }
    }

    // AD — geri sayım: jenerikte ya da akış bitince; iptal edilmişse bir daha başlamaz.
    val sayim = core.sayimBaslasin
    val sonraki = core.nextEpIndex
    LaunchedEffect(core, sayim, sonraki) {
        if (!sayim || sonraki == null) { core.geriSayim = null; return@LaunchedEffect }
        for (kalan in NEXT_COUNTDOWN_SEC downTo 1) {
            core.geriSayim = kalan
            delay(1000)
        }
        core.geriSayim = null
        core.goToEpisode(sonraki)
    }

    return core
}
