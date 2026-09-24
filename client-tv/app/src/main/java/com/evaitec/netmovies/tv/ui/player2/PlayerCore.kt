package com.evaitec.netmovies.tv.ui.player2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import com.evaitec.netmovies.tv.data.EpisodeItem
import com.evaitec.netmovies.tv.data.ItemDetails
import com.evaitec.netmovies.tv.data.Library
import com.evaitec.netmovies.tv.data.Markers
import com.evaitec.netmovies.tv.data.MediaItem
import com.evaitec.netmovies.tv.data.StreamLink

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

    // ---- içerik / bölüm
    var details by mutableStateOf<ItemDetails?>(null)
    var episodes by mutableStateOf<List<EpisodeItem>>(emptyList())
    var currentEpIndex by mutableIntStateOf(0)
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

    val canliYayin: Boolean get() = TODO("core")
    val nextEpIndex: Int? get() = TODO("core")
    val prevEpIndex: Int? get() = TODO("core")
    /** Oynatıcı üstünde "S2 B5 — Başlık" gibi etiket. */
    val simdikiEtiket: String? get() = TODO("core")
    val introRange: Pair<Float, Float>? get() = TODO("core")
    val creditsStart: Float? get() = TODO("core")
    val acilisAtlanabilir: Boolean get() = TODO("core")
    /** Son 70 sn / jenerik penceresi: SAĞ = sonraki bölüm. */
    val sonrakiTeklif: Boolean get() = TODO("core")

    // ---- eylemler
    fun playPause(): Unit = TODO("core")
    /** Birikimli seek (350 ms debounce, tek seekTo). */
    fun seekBy(deltaMs: Long): Unit = TODO("core")
    /** Basılı tutma: yon=-1/+1, adım 10→30→60 sn. */
    fun seekHold(yon: Int, heldMs: Long): Unit = TODO("core")
    fun commitSeek(): Unit = TODO("core")
    fun seekTo(ms: Long): Unit = TODO("core")
    fun seekToFraction(f: Float): Unit = TODO("core")
    fun skipIntro(): Unit = TODO("core")
    fun tamponBasina(): Unit = TODO("core")
    fun canliyaDon(): Unit = TODO("core")
    fun goToEpisode(idx: Int): Unit = TODO("core")
    /** Başlangıç panelindeki OYNAT: kayıtlı bölüm varsa ona geçip oynatır. */
    fun panelOynat(): Unit = TODO("core")
    fun selectLink(idx: Int): Unit = TODO("core")
    fun selectAudio(group: Tracks.Group, index: Int): Unit = TODO("core")
    fun selectSubtitle(group: Tracks.Group?, index: Int): Unit = TODO("core")
    fun selectQuality(group: Tracks.Group?, index: Int): Unit = TODO("core")
    fun selectSpeed(s: Float): Unit = TODO("core")
    /** Bölümler sekmesi açıldı: en zengin bölüm listesini bir kez çek (episodesBest). */
    fun requestBestEpisodes(): Unit = TODO("core")
}

/**
 * Tüm yan etkiler (LaunchedEffect/DisposableEffect) burada kurulur; dönen çekirdek
 * `item.url` değişince yenilenir. Dispose: canlı değilse saveProgress → sync → release.
 * [onExit]: KAYNAK_YOK ve hiç oynamadıysa 2,5 sn sonra çağrılır.
 */
@Composable
fun rememberPlayerCore(item: MediaItem, library: Library, onExit: () -> Unit): PlayerCore = TODO("core")
