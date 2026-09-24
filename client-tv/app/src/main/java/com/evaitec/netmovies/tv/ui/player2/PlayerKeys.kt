package com.evaitec.netmovies.tv.ui.player2

import android.content.Context
import android.view.KeyEvent as NKey
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalContext
import com.evaitec.netmovies.tv.data.MediaItem
import com.evaitec.netmovies.tv.data.RemoteBus
import com.evaitec.netmovies.tv.input.KeyBindings
import com.evaitec.netmovies.tv.input.KeyPairGate
import com.evaitec.netmovies.tv.input.NmBackHandler
import com.evaitec.netmovies.tv.input.RemoteAction
import com.evaitec.netmovies.tv.input.RemoteInputController
import com.evaitec.netmovies.tv.ui.MEDIA_KEYS
import com.evaitec.netmovies.tv.ui.bilgiGosterirMi
import com.evaitec.netmovies.tv.ui.keyLabel
import kotlinx.coroutines.delay

/**
 * Oynatıcı 2 — tuş eşleme. Kumanda, medya tuşları ve telefon kumandası (RemoteBus
 * transport) aynı eylemlere iner. SAHİBİ: keys ajanı.
 */
class PlayerKeys(
    val core: PlayerCore,
    val ui: PlayerUiState,
    val onExit: () -> Unit,
    /** Canlı yayında kanal değiştir: -1 / +1. */
    val kanalAtla: (Int) -> Unit,
) {
    /** rememberPlayerKeys kurar (scope + bindings gerekiyor). */
    lateinit var controller: RemoteInputController
    lateinit var bindings: KeyBindings
    /** Kanal geçişi yalnız canlı yayında ve liste doluyken açık. */
    var kanalSayisi = 0

    // Yarım tuş olayı kapısı — gerekçesi ve testi input/KeyPairGate.kt'de.
    private val tusKapisi = KeyPairGate()
    // MENÜ basılı mı tutuldu: tek basış ayarları açar, basılı tutma sisteme kalır.
    private var menuUzun = false

    private val kanalGecisiVar: Boolean get() = core.canliYayin && kanalSayisi > 1

    private fun seekBy(ms: Long) { core.seekBy(ms); ui.flashControls() }
    private fun seekHold(yon: Int, heldMs: Long) { core.seekHold(yon, heldMs); ui.flashControls() }

    private fun enterScrub() {
        ui.scrubPos = core.exo.currentPosition
        ui.scrubMode = true
        ui.scrubTick++          // preview'ı mevcut pozisyona seek et
        ui.showControls = true
    }

    fun dispatch(a: RemoteAction) {
        when (a) {
            RemoteAction.NONE -> Unit
            // Sarma bekliyorsa OK = "burada dur" — core.playPause bekleyen hedefi uygular.
            RemoteAction.PLAY_PAUSE -> { core.playPause(); ui.flashControls() }
            RemoteAction.SEEK_FWD_10 -> seekBy(10_000)
            RemoteAction.SEEK_BACK_10 -> seekBy(-10_000)
            RemoteAction.SEEK_FWD_60 -> seekBy(60_000)
            RemoteAction.SEEK_BACK_60 -> seekBy(-60_000)
            // Eşleme ekranından tek basışa atanmışsa da çalışsın: tutma süresi yok, 10 sn.
            RemoteAction.SEEK_HOLD_FWD -> seekBy(10_000)
            RemoteAction.SEEK_HOLD_BACK -> seekBy(-10_000)
            RemoteAction.OPEN_SETTINGS -> if (kanalGecisiVar) kanalAtla(-1) else ui.showSettings = true
            // Filmde bölüm listesi yok: tuş boşa basılmasın, ayarlar açılır.
            RemoteAction.OPEN_EPISODES -> {
                ui.ayarBolumler = core.episodes.isNotEmpty()
                ui.showSettings = true
                ui.showControls = false
            }
            RemoteAction.SHOW_CONTROLS -> ui.flashControls()
            // Canlı yayında YUKARI/AŞAĞI kanal değiştirir; dizi/filmde scrub.
            RemoteAction.TOGGLE_SCRUB -> if (kanalGecisiVar) kanalAtla(+1) else enterScrub()
            RemoteAction.BACK -> onExit()
        }
    }

    fun onHold(a: RemoteAction, heldMs: Long) {
        when (a) {
            RemoteAction.SEEK_HOLD_FWD -> seekHold(+1, heldMs)
            RemoteAction.SEEK_HOLD_BACK -> seekHold(-1, heldMs)
            else -> dispatch(a)
        }
    }

    /** Telefon kumandası transport komutu (RemoteBus). */
    fun onTransport(action: String, value: Float) {
        when (action) {
            "play_pause" -> dispatch(RemoteAction.PLAY_PAUSE)
            "seek" -> seekBy((value * 1000).toLong())
            "stop" -> onExit()
        }
    }

    // Scrub modunda D-pad: ◀/▶ imleç, OK atla, Geri iptal.
    private fun handleScrubKey(e: NKey): Boolean {
        if (e.action != NKey.ACTION_DOWN) return true
        when (e.keyCode) {
            NKey.KEYCODE_DPAD_LEFT -> { ui.scrubPos = (ui.scrubPos - 10_000).coerceAtLeast(0); ui.scrubTick++ }
            NKey.KEYCODE_DPAD_RIGHT -> {
                val d = core.exo.duration
                ui.scrubPos = (ui.scrubPos + 10_000).let { if (d > 0) it.coerceAtMost(d) else it }
                ui.scrubTick++
            }
            NKey.KEYCODE_DPAD_CENTER, NKey.KEYCODE_ENTER -> {
                core.seekTo(ui.scrubPos); ui.scrubMode = false; ui.flashControls()
            }
            NKey.KEYCODE_BACK -> ui.scrubMode = false
            else -> Unit
        }
        return true
    }

    /** Kök Box `onPreviewKeyEvent`: KeyPairGate, ipucu, bilgi tuşları, başlangıç panelinde GERİ. */
    fun onPreviewKey(e: KeyEvent): Boolean {
        val ne = e.nativeKeyEvent
        // DOWN'unu görmediğimiz UP (önceki ekranda basılmış tuşun bırakılması) yutulur.
        if (!tusKapisi.kabul(ne.action, ne.keyCode)) return true
        // Tuş göstergesi: kökten aşağı ilk yol, her tuş buraya uğrar.
        if (ui.showKeys && ne.action == NKey.ACTION_DOWN && ne.repeatCount == 0) {
            ui.keyHint = keyLabel(ne.keyCode, bindings)
            ui.keyTick++
        }
        // Boşta duran tuş → süre/ilerleme bilgisini kısa süre göster.
        if (bilgiGosterirMi(ne.keyCode)) {
            val baskaPanel = ui.showSettings || ui.showSeek || core.showStartPanel || ui.scrubMode
            if (ne.action == NKey.ACTION_DOWN && ne.repeatCount == 0 && !baskaPanel) ui.flashControls()
            return true
        }
        // GERİ'yi paneller aşağı inmeden yakala (TV Material odak grubu yutuyordu).
        // Not: MainActivity GERİ'yi BackBus'a verdiği sürece buraya gelmez; yedek yol.
        if (!core.showStartPanel || ne.keyCode != NKey.KEYCODE_BACK) return false
        if (ne.action == NKey.ACTION_UP) ui.onBack(core, onExit)
        return true
    }

    /** Kök Box `onKeyEvent`: MENU, medya tuşları, atla/sonraki, scrub, RemoteInputController. */
    fun onKey(e: KeyEvent): Boolean {
        val ne = e.nativeKeyEvent
        val sag = ne.keyCode == NKey.KEYCODE_DPAD_RIGHT
        val panelAcik = ui.panelAcik(core)
        return when {
            // MENÜ: TEK basış oynatıcı ayarları, BASILI TUTMA sisteme kalır (tüketilmez).
            ne.keyCode == NKey.KEYCODE_MENU -> when {
                ne.action == NKey.ACTION_DOWN && ne.repeatCount == 0 -> { menuUzun = false; true }
                ne.action == NKey.ACTION_DOWN -> { menuUzun = true; false }
                ne.action == NKey.ACTION_UP && !menuUzun -> { ui.showSettings = true; true }
                else -> false
            }
            // Kumandanın oynatma tuşları (⏪ ⏩ ⏮ ⏭ ⏯) — buton eşlemesine girmez.
            ne.keyCode in MEDIA_KEYS -> {
                if (ne.action == NKey.ACTION_DOWN) {
                    val tekrar = ne.repeatCount > 0
                    when (ne.keyCode) {
                        // ⏪/⏩ tek basış 30 sn; basılı tutmada kademeli motor.
                        NKey.KEYCODE_MEDIA_FAST_FORWARD ->
                            if (tekrar) seekHold(+1, ne.eventTime - ne.downTime) else seekBy(30_000)
                        NKey.KEYCODE_MEDIA_REWIND ->
                            if (tekrar) seekHold(-1, ne.eventTime - ne.downTime) else seekBy(-30_000)
                        // Diğer medya tuşları tekrarda ikinci kez tetiklenmez.
                        else -> if (!tekrar) when (ne.keyCode) {
                            // Dizide bölüm atlar; canlıda tampon başı / canlı; filmde ±1 dk.
                            NKey.KEYCODE_MEDIA_NEXT -> core.nextEpIndex?.let { core.goToEpisode(it) }
                                ?: if (core.canliYayin) { core.canliyaDon(); ui.flashControls() } else seekBy(60_000)
                            NKey.KEYCODE_MEDIA_PREVIOUS -> core.prevEpIndex?.let { core.goToEpisode(it) }
                                ?: if (core.canliYayin) { core.tamponBasina(); ui.flashControls() } else seekBy(-60_000)
                            NKey.KEYCODE_MEDIA_PLAY -> { core.exo.play(); ui.flashControls() }
                            NKey.KEYCODE_MEDIA_PAUSE -> { core.exo.pause(); ui.flashControls() }
                            NKey.KEYCODE_MEDIA_STOP -> onExit()
                            else -> dispatch(RemoteAction.PLAY_PAUSE)
                        }
                    }
                }
                true
            }
            // Açılış çalarken SAĞ = açılışı atla; pencere dışında SAĞ hâlâ ileri sarma.
            sag && core.acilisAtlanabilir && !panelAcik -> {
                if (ne.action == NKey.ACTION_DOWN) core.skipIntro()
                true
            }
            // Geri sayım sürerken SAĞ = beklemeden geç.
            sag && core.geriSayim != null -> {
                if (ne.action == NKey.ACTION_DOWN) core.nextEpIndex?.let { core.goToEpisode(it) }
                true
            }
            // Teklif penceresinde SAĞ = sonraki bölüm.
            sag && core.sonrakiTeklif && !panelAcik -> {
                if (ne.action == NKey.ACTION_DOWN) core.nextEpIndex?.let { core.goToEpisode(it) }
                true
            }
            ui.scrubMode -> handleScrubKey(ne)
            // Uzun basışla açılan panelin bırakma olayı controller'a aittir.
            controller.consumesPendingUp(ne) -> true
            // Modal açıkken tuşlar panele kalır.
            ui.showSettings || ui.showSeek || core.showStartPanel -> false
            else -> controller.process(ne)
        }
    }
}

/** RemoteInputController + KeyPairGate + RemoteBus toplayıcısı + NmBackHandler kurulumu. */
@Composable
fun rememberPlayerKeys(
    core: PlayerCore,
    ui: PlayerUiState,
    bindings: KeyBindings,
    kanallar: List<MediaItem>,
    onKanal: (MediaItem) -> Unit,
    onExit: () -> Unit,
): PlayerKeys {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val guncelKanallar = rememberUpdatedState(kanallar)
    val guncelOnKanal = rememberUpdatedState(onKanal)
    val guncelOnExit = rememberUpdatedState(onExit)

    val keys = remember(core, ui) {
        // Tuş göstergesi tercihi buton eşlemesiyle aynı dosyada (oynatıcıdan çıkınca kaybolmasın).
        ui.showKeys = context.getSharedPreferences("netmovies_keymap", Context.MODE_PRIVATE)
            .getBoolean("show_keys", true)
        PlayerKeys(
            core = core,
            ui = ui,
            onExit = { guncelOnExit.value() },
            kanalAtla = { yon ->
                val liste = guncelKanallar.value
                val simdiki = liste.indexOfFirst { it.url == core.item.url }
                if (simdiki >= 0) {
                    guncelOnKanal.value(liste[(simdiki + yon + liste.size) % liste.size])
                    ui.flashControls()   // yeni kanalın adı kontrol çubuğunda görünsün
                }
            },
        )
    }
    keys.bindings = bindings
    keys.kanalSayisi = kanallar.size
    remember(keys, bindings) {
        keys.controller = RemoteInputController(bindings, scope, onAction = keys::dispatch, onHold = keys::onHold)
    }

    // Telefon kumandası: tuş karşılığı olmayan eylemler (serbest saniyeyle sarma, dur-çık).
    LaunchedEffect(keys) {
        RemoteBus.komutlar.collect { cmd ->
            if (cmd.type == "transport") keys.onTransport(cmd.action, cmd.value)
        }
    }

    NmBackHandler(enabled = true) { ui.onBack(core, keys.onExit) }

    // Kontrol overlay otomatik gizleme.
    LaunchedEffect(ui.controlsTick, ui.showControls) {
        if (ui.showControls) { delay(3500); ui.showControls = false }
    }
    // Sarma göstergesi otomatik gizleme.
    LaunchedEffect(core.hintTick) {
        if (core.seekHint != null) { delay(900); core.seekHint = null }
    }
    // Tuş göstergesi otomatik gizleme.
    LaunchedEffect(ui.keyTick) {
        if (ui.keyHint != null) { delay(2500); ui.keyHint = null }
    }
    return keys
}
