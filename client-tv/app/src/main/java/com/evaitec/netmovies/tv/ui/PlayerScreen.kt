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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Shape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.evaitec.netmovies.tv.data.Library
import com.evaitec.netmovies.tv.data.episodeIndexOf
import com.evaitec.netmovies.tv.data.basliktanBolum
import com.evaitec.netmovies.tv.data.episodeRef
import com.evaitec.netmovies.tv.data.MediaItem
import com.evaitec.netmovies.tv.data.Network
import com.evaitec.netmovies.tv.data.OynatmaAyari
import com.evaitec.netmovies.tv.data.PlaybackLog
import com.evaitec.netmovies.tv.data.languageLabel
import com.evaitec.netmovies.tv.data.loggedOrNull
import com.evaitec.netmovies.tv.data.SourceEvent
import com.evaitec.netmovies.tv.data.StreamLink
import com.evaitec.netmovies.tv.data.guessSubtitleLang
import com.evaitec.netmovies.tv.input.KeyBindings
import com.evaitec.netmovies.tv.input.PressType
import com.evaitec.netmovies.tv.input.RemoteAction
import com.evaitec.netmovies.tv.input.RemoteInputController
import com.evaitec.netmovies.tv.input.RemoteKey
import com.evaitec.netmovies.tv.ui.player2.jenerikteMi
import com.evaitec.netmovies.tv.ui.theme.NmColor
import com.evaitec.netmovies.tv.ui.theme.NmDim
import com.evaitec.netmovies.tv.ui.theme.NmType
import com.evaitec.netmovies.tv.ui.theme.nmFocusRing
import com.evaitec.netmovies.tv.ui.theme.nmPlayerScrim
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items

// Oynatma hızı seçenekleri (çark → Hız).
internal val SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

// Kumandanın oynatma tuşları — D-pad'den ayrı, eşlemeye girmez, oynatıcıda sabit.
internal val MEDIA_KEYS = setOf(
    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
    KeyEvent.KEYCODE_MEDIA_REWIND,
    KeyEvent.KEYCODE_MEDIA_NEXT,
    KeyEvent.KEYCODE_MEDIA_PREVIOUS,
    KeyEvent.KEYCODE_MEDIA_PLAY,
    KeyEvent.KEYCODE_MEDIA_PAUSE,
    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
    KeyEvent.KEYCODE_MEDIA_STOP,
    KeyEvent.KEYCODE_HEADSETHOOK,
)

internal val PAD_DISI = setOf(
    KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
    KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_TV_POWER, KeyEvent.KEYCODE_SLEEP,
    KeyEvent.KEYCODE_WAKEUP, KeyEvent.KEYCODE_SOFT_SLEEP,
    KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_APP_SWITCH,
    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU,
    KeyEvent.KEYCODE_UNKNOWN,
)

/** Başka bir işe bağlı OLMAYAN tuş süre bilgisini gösterir: kumandada boşta
 *  duran tuşa basınca hiçbir şey olmaması "uygulama kilitlendi" gibi okunur. */
internal fun bilgiGosterirMi(code: Int): Boolean =
    RemoteKey.from(code) == null && code !in MEDIA_KEYS && code !in PAD_DISI


// Basılan tuşun ekranda görünen karşılığı: "MEDIA_FAST_FORWARD (90) → +30 sn".
// Kod numarası da yazar — kumandanın ürettiği tuş bilinmeyen bir şeyse eşleme
// ekranında aranacak değer budur.
internal fun keyLabel(code: Int, bindings: KeyBindings): String {
    val ad = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
    val karsilik = when {
        RemoteKey.from(code) != null -> {
            val tek  = bindings.get(code, PressType.SINGLE)
            val cift = bindings.get(code, PressType.DOUBLE)
            val uzun = bindings.get(code, PressType.LONG)
            listOfNotNull(
                tek.takeIf { it != RemoteAction.NONE }?.let { "tek: ${it.label}" },
                cift.takeIf { it != RemoteAction.NONE }?.let { "çift: ${it.label}" },
                uzun.takeIf { it != RemoteAction.NONE }?.let { "basılı: ${it.label}" },
            ).joinToString(" · ").ifBlank { "eşlenmemiş" }
        }
        code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "+30 sn"
        code == KeyEvent.KEYCODE_MEDIA_REWIND       -> "−30 sn"
        code == KeyEvent.KEYCODE_MEDIA_NEXT         -> "sonraki bölüm (filmde +1 dk)"
        code == KeyEvent.KEYCODE_MEDIA_PREVIOUS     -> "önceki bölüm (filmde −1 dk)"
        code == KeyEvent.KEYCODE_MEDIA_PLAY         -> "oynat"
        code == KeyEvent.KEYCODE_MEDIA_PAUSE        -> "duraklat"
        code == KeyEvent.KEYCODE_MEDIA_STOP         -> "çık"
        code in MEDIA_KEYS                          -> "oynat / duraklat"
        code == KeyEvent.KEYCODE_MENU               -> "tek: ayarlar · basılı: sistem"
        code == KeyEvent.KEYCODE_BACK               -> "geri"
        code == KeyEvent.KEYCODE_HOME               -> "sistem (uygulama yakalayamaz)"
        bilgiGosterirMi(code)                       -> "süre bilgisi"
        else                                        -> "bağlı değil"
    }
    return "$ad ($code) → $karsilik"
}

// Sol üstte küçük şerit. TV güvenli alanı içinde, video akışını kapatmayacak kadar dar.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun KeyHintChip(text: String) {
    Box(Modifier.fillMaxSize().padding(horizontal = NmDim.SafeH, vertical = NmDim.SafeV)) {
        Box(
            Modifier
                .align(Alignment.TopStart)
                .clip(RoundedCornerShape(NmDim.PillRadius))
                .background(NmColor.Scrim)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Text(
                text = "⌨  $text",
                fontSize = NmType.Caption,
                fontWeight = FontWeight.Medium,
                color = NmColor.OnSurface,
            )
        }
    }
}

/**
 * Segment hatasında sabır. Sunucu laptop ↔ ZimaOS geçişi 30-60 sn sürüyor; oynatma
 * 5 dk tampondan devam eder, yeter ki segment isteği vazgeçmesin. Bağlantı ve 5xx
 * hatasında 6 deneme, artan bekleme (1,2,4,8,8,8 sn). Manifest ve 4xx varsayılanda
 * (3 deneme): ölü kaynak / tek kullanımlık 403 hızlı düşsün, sıradakine geçilsin.
 */
@UnstableApi
internal class SunucuGecisPolitikasi : DefaultLoadErrorHandlingPolicy(3) {
    override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long =
        if (gecici(info)) BEKLEME_MS[(info.errorCount - 1).coerceIn(0, BEKLEME_MS.lastIndex)]
        else super.getRetryDelayMsFor(info)

    override fun getMinimumLoadableRetryCount(dataType: Int): Int =
        if (dataType == C.DATA_TYPE_MEDIA) BEKLEME_MS.size else super.getMinimumLoadableRetryCount(dataType)

    private fun gecici(info: LoadErrorHandlingPolicy.LoadErrorInfo): Boolean =
        info.mediaLoadData.dataType == C.DATA_TYPE_MEDIA && when (val e = info.exception) {
            is HttpDataSource.InvalidResponseCodeException -> e.responseCode >= 500
            is HttpDataSource.HttpDataSourceException -> true
            else -> false
        }

    private companion object {
        val BEKLEME_MS = longArrayOf(1_000, 2_000, 4_000, 8_000, 8_000, 8_000)
    }
}

// .vtt / .srt uzantısından MIME tahmini (bilinmiyorsa VTT).
/**
 * Kaynağın medya kaynağı: çoğu HLS, ama Özel Koleksiyon'da (HQPorner) düz mp4 var.
 * Hepsi HlsMediaSource ile açılınca mp4 anında hata verip sonraki kaynağa
 * geçiliyordu ("çok hızlı deneyip geçiyor"). Proxy adresinde asıl URL `url=`
 * parametresinde kodlu durur; çözülmüş hâline bakılır.
 */
internal fun videoSource(
    factory: androidx.media3.datasource.DataSource.Factory,
    url: String,
): androidx.media3.exoplayer.source.MediaSource {
    val policy = SunucuGecisPolitikasi()
    val item = ExoMediaItem.fromUri(url)
    val plain = Uri.decode(url).lowercase()
    return if (".mp4" in plain && ".m3u8" !in plain)
        androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(factory)
            .setLoadErrorHandlingPolicy(policy).createMediaSource(item)
    else HlsMediaSource.Factory(factory).setLoadErrorHandlingPolicy(policy).createMediaSource(item)
}

internal fun guessSubtitleMime(url: String): String {
    val u = url.lowercase()
    return when {
        u.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
        u.endsWith(".ass") || u.endsWith(".ssa") -> MimeTypes.TEXT_SSA
        else -> MimeTypes.TEXT_VTT
    }
}

internal fun fmtTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

// Sarma ofseti: "45sn", "2dk 30sn", "1sa 5dk". İşaret çağıranda.
internal fun fmtDelta(ms: Long): String {
    val total = kotlin.math.abs(ms) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return when {
        h > 0 -> if (m > 0) "${h}sa ${m}dk" else "${h}sa"
        m > 0 -> if (s > 0) "${m}dk ${s}sn" else "${m}dk"
        else -> "${s}sn"
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ControlsOverlay(
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekFwd: () -> Unit,
    onSeekToFraction: (Float) -> Unit,
    /** Sol üstte duran şerit: "Dizi adı · S1B3". Dizide bölüm, filmde yalnız ad. */
    nowLabel: String = "",
    /** null = o yöne bölüm yok (ilk/son bölüm ya da film). */
    onPrevEpisode: (() -> Unit)? = null,
    onNextEpisode: (() -> Unit)? = null,
    /** null = film (bölüm listesi yok). */
    onOpenList: (() -> Unit)? = null,
    /** Açılış şarkısı aralığı (ms) — çubukta soluk blok. null = işaret yok. */
    introRange: Pair<Long, Long>? = null,
    /** Jenerik başlangıcı (ms) — çubukta ince çizgi. null = işaret yok. */
    creditsStart: Long? = null,
) {
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Box(Modifier.fillMaxSize()) {
        // Sol üst: ne oynadığı. Dizide hangi bölümde olduğunu ekranda gösteren
        // hiçbir yer yoktu — favoriden açınca hangi bölümün başladığı bile
        // bilinmiyordu (Dean, 17 Eylül).
        if (nowLabel.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = NmDim.SafeH, vertical = NmDim.SafeV)
                    .clip(RoundedCornerShape(NmDim.PillRadius))
                    .background(NmColor.Scrim)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    text = nowLabel,
                    fontSize = NmType.Caption,
                    fontWeight = FontWeight.SemiBold,
                    color = NmColor.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
                    // Belirteçler DOLGUNUN ALTINDA çizilir: açılış bloğu soluk, jenerik
                    // çizgisi ince. İzlenen kısım üstlerinden geçer, konum okunur kalır.
                    if (duration > 0) {
                        introRange?.let { (bas, bit) ->
                            MarkerBand(
                                start = (bas.toFloat() / duration).coerceIn(0f, 1f),
                                end   = (bit.toFloat() / duration).coerceIn(0f, 1f),
                            )
                        }
                        creditsStart?.let {
                            val f = (it.toFloat() / duration).coerceIn(0f, 1f)
                            MarkerBand(start = f, end = (f + 0.004f).coerceAtMost(1f))
                        }
                    }
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
                    // Bölüm geçişi yalnız QuickPad'deydi; çubukta yoktu, kullanıcı
                    // sıradaki bölüme gitmek için panel açmak zorundaydı.
                    onPrevEpisode?.let { IconBtn(Icons.Filled.SkipPrevious, 40.dp, 24.dp, it) }
                    IconBtn(Icons.Filled.Replay10, 40.dp, 26.dp, onSeekBack)
                    IconBtn(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        52.dp, 32.dp, onPlayPause, accent = true,
                    )
                    IconBtn(Icons.Filled.Forward10, 40.dp, 26.dp, onSeekFwd)
                    onNextEpisode?.let { IconBtn(Icons.Filled.SkipNext, 40.dp, 24.dp, it) }
                }
                Text(fmtTime(duration), color = NmColor.OnSurfaceMuted, fontSize = NmType.Caption)
            }
        }
    }
}

// İlerleme çubuğunda bir belirteç: [start, end] oranı arası soluk bant.
// Konumlandırma weight'li Spacer ile — yüzde offset Compose'da ölçüm gerektirir,
// üç Spacer'lık Row aynı işi ölçümsüz görür.
@Composable
private fun MarkerBand(start: Float, end: Float) {
    val genislik = (end - start).coerceAtLeast(0.003f)
    Row(Modifier.fillMaxWidth().height(5.dp)) {
        if (start > 0.001f) Spacer(Modifier.weight(start))
        Spacer(Modifier.weight(genislik).fillMaxHeight().background(NmColor.OnSurfaceMuted))
        val kalan = 1f - start - genislik
        if (kalan > 0.001f) Spacer(Modifier.weight(kalan))
    }
}

// Küçük yuvarlak ikon buton (vektör; renk tint → emoji/sarı yok). pointerInput tap →
// D-pad focus'unu bozmaz. accent=true → dolu mor (oynat/duraklat).
@Composable
private fun IconBtn(icon: ImageVector, box: androidx.compose.ui.unit.Dp, ic: androidx.compose.ui.unit.Dp, onTap: () -> Unit, accent: Boolean = false) {
    // Düğmeler ODAK ALMAZ — bu bilinçli. Denendi ve cihazda çalışmadı: kök kutu
    // `.focusable()` olduğu için Compose'un odak araması buraya inmiyordu, AŞAĞI ok
    // ölü kalıyordu (Dean, 19 Eylül: "sarma ileri geri sonraki bölüm dolaşmıyor,
    // odak yok butonlarda"). Gezilebilir takım TEK yerde: QuickPad — index'le
    // seçilir, Compose odak ağacına güvenmez. AŞAĞI ok onu açar.
    Box(
        modifier = Modifier
            .size(box)
            .clip(CircleShape)
            .background(
                if (accent) NmColor.Primary else NmColor.ScrimSoft
            )
            .pointerInput(Unit) { detectTapGestures { onTap() } },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            icon,
            contentDescription = null,
            modifier = Modifier.size(ic),
            colorFilter = ColorFilter.tint(NmColor.OnPrimary),
        )
    }
}

// Kaynak aramasının SONUÇSUZ bittiğini söyleyen tek mesaj. Sabit olmasının sebebi
// görünüm: bu durumda dönen halka değil ✕ gösterilir ve ekran kendiliğinden kapanır.
internal const val KAYNAK_YOK = "Çalışan kaynak bulunamadı"

// Mesaj okunacak kadar durur, sonra içerikten çıkılır. Kullanıcıyı boş ekranda
// GERİ'ye basmayı beklemek anlamsız: yapacak bir şey yok (Dean: "geri kendi atsın,
// bulamadığında bekletme").
internal const val KAYNAK_YOK_CIKIS_MS = 2500L

// Bir bölüm/film için "gerçek içerik" sayılacak en kısa süre. Sağlayıcılar
// kaldırılmış bölümün yerine on-yirmi saniyelik tutundurma/uyarı klibi koyabiliyor
// (ekranda "İÇERİK KALDIRILDI", "DUR! GİTME!" gibi metinler — bunlar bu koddan
// GELMEZ, kaynağın kendi videosudur). 90 sn eşiği en kısa gerçek bölüm/fragmandan
// bile kısa tutundurma klipleri ayırt etmeye yeter; gerçek içerik bundan kısa
// olmaz. Aynı büyüklükte olması tesadüf: NEXT_EPISODE_WINDOW_MS ayrı bir amaca
// (bitiş penceresi) hizmet eder, kasıtlı olarak burada tekrar tanımlanır.
internal const val MIN_GECERLI_SURE_MS = 90_000L

// Kuyruktaki hiçbir kaynak 90 sn eşiğini geçemedi: bölüm sağlayıcıda gerçekten yok.
internal const val BOLUM_YOK = "Bu bölüm sağlayıcıda yok"

// Jenerik işareti BULUNAMAYAN bölümde teklif penceresi: bitmeye bu kadar kala.
// 90 sn erken çıkıyordu — kart hâlâ sahnenin ortasındayken beliriyor, jenerik
// ancak ~20 sn sonra başlıyordu (Dean, 18 Eylül, iki fotoğraf: kart 90 sn kala,
// jenerik 70 sn kala). Sessizlik/konuşma temelli tespit denenmedi: dizi ve filmde
// sahne içinde de uzun sessizlik oluyor, yanlış yerde tetiklerdi. Jenerik işareti
// varsa bu pencere zaten hiç kullanılmaz.
// Dean, 4 Kasım: "son 1 dk'ya koy" — kart da sayım da yalnız son dakikada.
internal const val NEXT_EPISODE_WINDOW_MS = 60_000L

// Jenerik başlayınca sonraki bölüme geçmeden önce beklenen süre. Son sahneyi
// kaçırmamak için var: GERİ basan kişi jeneriği sonuna kadar izler. 10 sn azdı,
// erken bulunan jenerik işaretiyle bölüm bitmeden geçiyordu (Resident Alien, 4 Kasım).
internal const val NEXT_COUNTDOWN_SEC = 30

// Bölüm sonu kartı — sağ altta, oynatmayı kesmeden. Kumandada SAĞ ok kabul eder
// (tuş işleme oynatıcıda; kart odak almaz ki D-pad sarma/kontrol akışı bozulmasın),
// dokunmatikte karta dokunmak yeter.
// countdown != null → geri sayım sürüyor, süre dolunca kendiliğinden geçilir;
// GERİ sayımı durdurur. countdown == null → yalnız teklif, kendiliğinden geçiş yok.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun NextEpisodeCard(label: String, countdown: Int?, onPlay: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(NmDim.SafeArea), contentAlignment = Alignment.BottomEnd) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(NmDim.PanelRadius))
                .background(NmColor.SurfaceDialog)
                .pointerInput(Unit) { detectTapGestures { onPlay() } }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = if (countdown != null) "Sıradaki bölüm · $countdown" else "Sıradaki bölüm",
                color = NmColor.OnSurfaceMuted,
                fontSize = NmType.Caption,
            )
            Text(
                text = label,
                color = NmColor.OnSurface,
                fontSize = NmType.Label,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (countdown != null) "▶  SAĞ ok geç  ·  GERİ kal" else "▶  SAĞ ok ile geç",
                color = NmColor.Primary,
                fontSize = NmType.Caption,
            )
        }
    }
}

// "Açılışı atla" — açılış şarkısı çalarken sağ altta. Aynı desen: kart odak almaz,
// kumandada SAĞ ok, dokunmatikte dokunuş.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun SkipIntroCard(onSkip: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(NmDim.SafeArea), contentAlignment = Alignment.BottomEnd) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(NmDim.PillRadius))
                .background(NmColor.SurfaceDialog)
                .pointerInput(Unit) { detectTapGestures { onSkip() } }
                .padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Açılışı Atla", color = NmColor.OnSurface, fontSize = NmType.Label, fontWeight = FontWeight.SemiBold)
            Text("SAĞ ok", color = NmColor.Primary, fontSize = NmType.Caption)
        }
    }
}

// Kuyruk tükendiğinde kaç kez otomatik yeniden çözümleme yapılır. Ölü kaynakta
// sonsuz döngüye girmemek için sınırlı.
internal const val MAX_AUTO_REFRESH = 2



/** Gruba dışarıdan gelen odak [hedef]e iner (geometrik en yakına değil). */
@kotlin.OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
private fun Modifier.girisOdagi(hedef: () -> FocusRequester): Modifier =
    focusProperties { enter = { hedef() } }

@OptIn(UnstableApi::class, ExperimentalTvMaterial3Api::class)
@Composable
internal fun SettingsPanel(
    links: List<StreamLink>,
    currentLinkIndex: Int,
    episodes: List<com.evaitec.netmovies.tv.data.EpisodeItem> = emptyList(),
    currentEpIndex: Int = 0,
    tracks: Tracks?,
    speed: Float,
    panelFocus: FocusRequester,
    /** Kitaplık sekmesi listeleri buradan okur/yazar (ana ekrandaki menüyle aynı). */
    library: com.evaitec.netmovies.tv.data.Library,
    item: com.evaitec.netmovies.tv.data.MediaItem,
    onSelectSource: (Int) -> Unit,
    /** Panel doğrudan Bölümler sekmesinde, LİSTE AÇIK gelsin (kumandadaki "Bölümler" girişi). */
    acilisBolumler: Boolean = false,
    /** İçerik açılırken aynı panel başlangıç panelidir: başlık üstte, odak OYNAT'ta.
     *  Eskiden ayrı bir StartPanel vardı; Dean (3 Ekim): "direkt bu iki sayfa çıksın". */
    baslangic: Boolean = false,
    title: String = "",
    /** Yıl · tür · puan satırı (yalnız başlangıçta çizilir). */
    bilgi: String = "",
    /** OYNAT satırı: devam / baştan / oynuyor. Boşsa satır çizilmez. */
    oynatEtiketi: String = "",
    /** Kaynak bulundu mu — başlangıçta "aranıyor" notu için. */
    hazir: Boolean = true,
    onPlay: () -> Unit = {},
    /** Bölüm listesi başka bir sağlayıcıdan geldiyse adı — kaç bölüm görüldüğü
     *  sağlayıcıya bağlı, kullanıcı hangisine baktığını bilsin. */
    listeKaynagi: String? = null,
    /** Panel içinden bölüm seçildi (tam ekran listeye gitmeden). */
    onSelectEpisode: (Int) -> Unit = {},
    onSelectAudio: (Tracks.Group, Int) -> Unit,
    onSelectSubtitle: (Tracks.Group?, Int) -> Unit,
    qualityAuto: Boolean,
    onOpenSeek: () -> Unit,
    onSelectQuality: (Tracks.Group?, Int) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    showReport: Boolean,
    onToggleReport: () -> Unit,
    showKeys: Boolean,
    onToggleKeys: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** Canlı yayın: hız satırı gizlenir; [kanallar] doluysa ilk sekme Kanallar olur. */
    canli: Boolean = false,
    kanallar: List<MediaItem> = emptyList(),
    onKanal: (MediaItem) -> Unit = {},
) {
    val videoGroups = tracks?.groups?.filter { it.type == C.TRACK_TYPE_VIDEO && it.length > 0 } ?: emptyList()
    // Bölüm tek varyantlı akışta da çizilir: gizlendiğinde "kalite ayarı yok" sanılıyordu.
    // Kaynakların çoğu tek çözünürlük veriyor (fastplay master.txt'te tek EXT-X-STREAM-INF);
    // o zaman liste tek satır olur ama hangi kalitede oynadığı görünür.
    val videoTrackCount = videoGroups.sumOf { it.length }
    val audioGroups = tracks?.groups?.filter { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 } ?: emptyList()
    val textGroups = tracks?.groups?.filter { it.type == C.TRACK_TYPE_TEXT && it.length > 0 } ?: emptyList()
    val textDisabled = textGroups.none { g -> (0 until g.length).any { g.isTrackSelected(it) } }

    // Üç SABİT sekme, her içerikte aynı üst sıra (Dean, 28 Eylül çizimi):
    // 0 Bölümler & Listeler (canlıda Kanallar) · 1 Kaynak · Ses · Hız · 2 ⚙ seyrek ayarlar.
    // Panelin eni ve boyu sabit; uzun içerik panelin içinde kayar, kısa içerikte
    // panel küçülmez — sekme/içerik değişince zıplamıyor.
    var sekme by remember { mutableStateOf(0) }
    // Bölüm/sezon listesi panelin İÇİNDE: tam ekran modal koca bir liste açıp
    // izlenen sahneyi kapatıyordu (Dean, 17 Eylül: "o da koca ekranda olmasın").
    var panelSezon by remember { mutableStateOf<Int?>(null) }
    val kanalModu = canli && kanallar.isNotEmpty()
    val sekmeFocus = remember { List(3) { FocusRequester() } }
    val bolumFocus = remember { FocusRequester() }
    val kanalFocus = remember { FocusRequester() }
    val listeFocus = remember { FocusRequester() }
    val oynatFocus = remember { FocusRequester() }
    // Bölüm listesi panelde KAPALI başlar; ☰ açar. Dizide sekiz satır panelin
    // tamamını alıyordu, OYNAT/son bölüm aşağı kayıyordu (Dean, 3 Ekim:
    // "bölümler kapalı, liste istersem açarım"). Kumandadan "Bölümler" ile gelince açık.
    var listeAcik by remember { mutableStateOf(acilisBolumler) }
    // GERİ sırası: açık chip listesi (SecimChip kendi kapatır) → odak üst segment
    // satırına → panel kapanır. Listenin derininden sekmeye tek tek çıkmak gerekiyordu.
    var usttekiSira by remember { mutableStateOf(false) }
    NmBackHandler {
        if (usttekiSira || runCatching { sekmeFocus[sekme].requestFocus() }.isFailure) onClose()
    }
    // Aynı anda tek chip açık: açık olanın etiketi.
    var acikSecim by remember { mutableStateOf<String?>(null) }
    @Composable
    fun Secim(etiket: String, secenekler: List<String>, secili: Int, onSec: (Int) -> Unit) = SecimChip(
        etiket = etiket,
        secenekler = secenekler,
        secili = secili,
        acik = acikSecim == etiket,
        onAc = { ac -> if (ac) acikSecim = etiket else if (acikSecim == etiket) acikSecim = null },
        onSec = onSec,
    )

    // Açılış odağı: oynayan bölüm (liste açıksa) → oynayan kanal → OYNAT satırı →
    // ilk liste hapı. Bağlı olmayan istek fırlatır; sıradaki denenir.
    LaunchedEffect(Unit) {
        val hedefler = listOf(bolumFocus, kanalFocus, oynatFocus, listeFocus, sekmeFocus[0], panelFocus)
        repeat(6) {
            withFrameNanos {}
            if (hedefler.any { runCatching { it.requestFocus() }.isSuccess }) return@LaunchedEffect
        }
    }

    val panelSekli = RoundedCornerShape(NmDim.PanelRadius)
    Column(
        modifier = modifier
            // Overscan içinde yüzen kart: kenara yapışık tam boy panel yazıyı
            // kenar payına itiyor, alt yarısı boş kalıp videoyu kapatıyordu.
            .padding(end = NmDim.SafeH, top = NmDim.SafeV, bottom = NmDim.SafeV)
            .size(NmDim.SidePanelWidth, NmDim.SidePanelHeight)
            .clip(panelSekli)
            .background(NmColor.SurfaceDialog)
            .border(width = NmDim.IdleRingWidth, color = NmColor.PrimaryHairline, shape = panelSekli)
            .focusRequester(panelFocus)
            .focusGroup()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(NmDim.ItemGap),
    ) {
        if (baslangic && title.isNotBlank()) {
            Text(
                text = title,
                fontSize = NmType.RowTitle,
                fontWeight = FontWeight.Bold,
                color = NmColor.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (bilgi.isNotBlank()) Text(
                text = bilgi,
                fontSize = NmType.Caption,
                color = NmColor.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Üst sıra: odak gelince sekme değişir ("buton içinde gezinir seçeriz").
        // YUKARI ile gelen odak geometrik en yakına değil seçili sekmeye iner —
        // yoksa içerikten çıkarken yanlışlıkla komşu sekme açılıyordu.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { usttekiSira = it.hasFocus }
                .girisOdagi { sekmeFocus[sekme] }
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(NmDim.SegmentGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Yazısız ikonlu bitişik segment (Dean, 28 Eylül: "yazısız ikonla yap — 3 tuş");
            // seçili sekmenin adı yanında küçük etiket — odak gezinirken sekme de değişir.
            val adlar = listOf(if (kanalModu) "Kanallar" else "Bölümler & Listeler", "Kaynak · Ses · Hız", "Ayarlar")
            val ikonlar = listOf(
                if (kanalModu) Icons.Filled.LiveTv else Icons.Filled.VideoLibrary,
                Icons.Filled.Tune,
                Icons.Filled.Settings,
            )
            adlar.forEachIndexed { i, ad ->
                IkonSekme(
                    ikon = ad,
                    vektor = ikonlar[i],
                    secili = i == sekme,
                    sekil = segmentSekli(i, adlar.size),
                    modifier = Modifier.width(NmDim.SegmentWidth).focusRequester(sekmeFocus[i]),
                ) { sekme = i }
            }
            Text(
                adlar[sekme],
                fontSize = NmType.Label,
                color = NmColor.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = NmDim.ChipGap),
            )
        }

        when (sekme) {
            0 -> {
                val L = com.evaitec.netmovies.tv.data.Library
                // Listeler tek sıra hap: etiket durumu değil İŞİ söyler, listede olduğu
                // seçili zeminden okunur. "☰ Bölümler" tam ekran listeyi açar (poster/özet).
                // Yazısız kare ikonlar: "✓ İzlenecek" yazısı hapa sığmayıp kesiliyordu.
                // Favori · Takip · İzlenecek tek seçimli ✓'li segment: içerik (dizi/film,
                // bölüm değil) tek listede durur; basılan seçilir, diğerlerinden çıkar,
                // seçiliye tekrar basmak listeden çıkarır.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (episodes.isNotEmpty()) {
                        IkonHap("Bölümler", Icons.Filled.FormatListBulleted, listeAcik) { listeAcik = !listeAcik }
                        Spacer(Modifier.width(NmDim.ItemGap))
                    }
                    val suan = listOf(library.isFavorite(item), library.inTakip(item), library.inIzlenecek(item))
                    val toggle = listOf(
                        { library.toggleFavorite(item) },
                        { library.toggleListe(item, L.LISTE_TAKIP) },
                        { library.toggleListe(item, L.LISTE_IZLENECEK) },
                    )
                    val listeAdlari = listOf("Favori", "Takip", "İzlenecek")
                    val listeIkonlari = listOf(Icons.Filled.Star, Icons.Filled.Visibility, Icons.Filled.Bookmark)
                    Row(
                        modifier = Modifier.focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(NmDim.SegmentGap),
                    ) {
                        listeAdlari.forEachIndexed { i, ad ->
                            IkonSekme(
                                ikon = ad,
                                vektor = listeIkonlari[i],
                                secili = suan[i],
                                tik = suan[i],
                                sekil = segmentSekli(i, listeAdlari.size),
                                odaktaSec = false,
                                modifier = Modifier.width(NmDim.SegmentWidth)
                                    .then(if (i == 0) Modifier.focusRequester(listeFocus) else Modifier),
                            ) {
                                val istenen = tekListe(suan, i)
                                istenen.forEachIndexed { j, olsun -> if (olsun != suan[j]) toggle[j]() }
                            }
                        }
                    }
                    // ● Kayıt: oynayan bölümü sunucu diskine kaydettir; kayıtlıysa sil.
                    // Liste segmentinden ayrı: içerik değil bölüm düzeyinde.
                    Spacer(Modifier.width(NmDim.ItemGap))
                    val kayitKapsam = rememberCoroutineScope()
                    val bolum = episodes.getOrNull(currentEpIndex)
                    val ref = if (bolum != null) com.evaitec.netmovies.tv.data.episodeRef(bolum.season, bolum.episode, currentEpIndex) else ""
                    val mevcutKayit = library.kayitBul(item.title, ref)
                    IkonSekme(
                        ikon = "Kayıt",
                        vektor = Icons.Filled.FiberManualRecord,
                        secili = mevcutKayit != null,
                        tik = mevcutKayit != null,
                        ikonRengi = NmColor.Rec,
                        odaktaSec = false,
                        modifier = Modifier.width(NmDim.PanelRowHeight),
                    ) {
                        kayitKapsam.launch {
                            if (mevcutKayit != null) library.kayitSil(mevcutKayit)
                            else library.kaydet(item, episodes, if (episodes.isEmpty()) null else currentEpIndex)
                        }
                    }
                }
                // Kaldığım bölüm ve son bölüm liste KAPALIYKEN burada: tek tıkla
                // devam / sona atla (Dean, 3 Ekim: "ikonlu olan yerde olsun").
                // Liste açıkken ikisi de listede var; üstten listeye inmek 4 basış
                // sürüyordu (Dean, 28 Eki).
                val listeGorunur = listeAcik && episodes.isNotEmpty()
                if (!kanalModu && !listeGorunur && oynatEtiketi.isNotBlank()) {
                    Box(Modifier.focusRequester(oynatFocus)) { SettingRow(oynatEtiketi, false) { onPlay() } }
                }
                if (baslangic && !hazir) MutedRow("Kaynak aranıyor… OYNAT'a basabilirsin, hazır olunca başlar.")
                if (episodes.size > 1 && !listeGorunur) {
                    val sonIdx = episodes.lastIndex
                    SettingRow(
                        "⏭  Son bölüm — ${episodeLabel(episodes[sonIdx], sonIdx)}",
                        sonIdx == currentEpIndex,
                    ) { onSelectEpisode(sonIdx) }
                }
                when {
                    listeGorunur -> {
                        val sezonlar = remember(episodes) { episodes.map { it.season }.distinct().sorted() }
                        val simdikiSezon = episodes.getOrNull(currentEpIndex)?.season
                        val acikSezon = panelSezon ?: simdikiSezon ?: sezonlar.firstOrNull()
                        // Sezon çipi odakta seçer; çipte yalnız numara, "Sezon" bir kez yazar.
                        if (sezonlar.size > 1) HapSatiri("Sezon") {
                            sezonlar.forEach { sz ->
                                IkonSekme("$sz", sz == acikSezon, Modifier.width(NmDim.SeasonChipWidth)) {
                                    panelSezon = sz
                                }
                            }
                        }
                        // Listenin sayısı ve sağlayıcısı: kaç bölüm görüldüğü ona bağlı.
                        Text(
                            "${episodes.size} bölüm" + (listeKaynagi?.let { " · $it" } ?: ""),
                            fontSize = NmType.Caption,
                            color = NmColor.OnSurfaceMuted,
                        )
                        val liste = episodes.withIndex().filter { it.value.season == acikSezon }
                        // Uzun sezonun sonunda çipe geri kaymadan SOL/SAĞ sezon değiştirir (Dean, 23 Eki);
                        // odak yeni sezonun ilk satırına iner, yoksa listede kaybolur.
                        val ilkSatirFocus = remember { FocusRequester() }
                        var tuslaDegisti by remember { mutableStateOf(false) }
                        LaunchedEffect(acikSezon) {
                            if (tuslaDegisti) { tuslaDegisti = false; runCatching { ilkSatirFocus.requestFocus() } }
                        }
                        // Yukarıdan inen odak oynayan bölüme gelir, geometrik en yakına değil.
                        val oynayanBurada = liste.any { it.index == currentEpIndex }
                        KayanListe(Modifier.girisOdagi { if (oynayanBurada) bolumFocus else FocusRequester.Default }.onPreviewKeyEvent { ev ->
                            if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val yon = when (ev.key) { Key.DirectionLeft -> -1; Key.DirectionRight -> +1; else -> return@onPreviewKeyEvent false }
                            val hedef = komsuSezon(sezonlar, acikSezon, yon) ?: return@onPreviewKeyEvent false
                            panelSezon = hedef; tuslaDegisti = true; true
                        }) {
                            if (liste.isEmpty()) MutedRow("Bu sezonda bölüm yok")
                            liste.forEachIndexed { sira, (idx, ep) ->
                                val oynuyor = idx == currentEpIndex
                                BolumSatiri(
                                    ep = ep,
                                    index = idx,
                                    oynuyor = oynuyor,
                                    // Kayıt tek "nereye kadar" tutar: öncekiler izlenmiş sayılır.
                                    izlendi = idx < currentEpIndex,
                                    kayitli = library.kayitBul(item.title, episodeRef(ep.season, ep.episode, idx)) != null,
                                    modifier = (if (sira == 0) Modifier.focusRequester(ilkSatirFocus) else Modifier)
                                        .then(if (oynuyor) Modifier.focusRequester(bolumFocus) else Modifier),
                                ) { onSelectEpisode(idx) }
                            }
                        }
                    }
                    kanalModu -> KayanListe {
                        kanallar.forEach { k ->
                            val oynuyor = k.url == item.url
                            SettingRow(
                                k.title ?: "Kanal",
                                oynuyor,
                                if (oynuyor) Modifier.focusRequester(kanalFocus) else Modifier,
                            ) { onKanal(k) }
                        }
                    }
                    episodes.isEmpty() -> MutedRow("OK ile listeye ekle / çıkar")
                }
            }

            1 -> KayanListe {
                // Satır başına TEK chip, seçili değeri gösterir; OK altında seçenekleri
                // dikey açar (Dean, 28 Eylül: "basınca açılan yapıda, kompakt").
                // Sıra kuralı sabit (Türkçe dublaj önce).
                Secim("Kaynak", links.map(::languageLabel), currentLinkIndex, onSelectSource)
                if (audioGroups.isEmpty() && textGroups.isEmpty()) HapSatiri("Ses") { MutedRow("Seçenek yok") }
                if (audioGroups.isNotEmpty()) {
                    val sesler = audioGroups.flatMap { g -> (0 until g.length).map { g to it } }
                    Secim(
                        "Ses",
                        sesler.mapIndexed { n, (g, i) ->
                            g.getTrackFormat(i).let {
                                val ad = it.label ?: it.language ?: "Ses ${n + 1}"
                                val bicim = com.evaitec.netmovies.tv.data.sesBicimi(it.sampleMimeType, it.channelCount)
                                if (bicim.isEmpty()) ad else "$ad · $bicim"
                            }
                        },
                        sesler.indexOfFirst { (g, i) -> g.isTrackSelected(i) },
                    ) { n -> sesler[n].let { (g, i) -> onSelectAudio(g, i) } }
                }
                if (textGroups.isNotEmpty()) {
                    val yazilar = textGroups.flatMap { g -> (0 until g.length).map { g to it } }
                    Secim(
                        "Altyazı",
                        listOf("Kapalı") + yazilar.mapIndexed { n, (g, i) ->
                            g.getTrackFormat(i).let { it.label ?: it.language ?: "Altyazı ${n + 1}" }
                        },
                        if (textDisabled) 0 else 1 + yazilar.indexOfFirst { (g, i) -> g.isTrackSelected(i) },
                    ) { n -> if (n == 0) onSelectSubtitle(null, 0) else yazilar[n - 1].let { (g, i) -> onSelectSubtitle(g, i) } }
                }
                // Yalnız Bluetooth çıkışında: hoparlör gecikmesi cihazdan cihaza değişir, kulakla ayarlanır.
                if (com.evaitec.netmovies.tv.data.SesOfseti.bluetooth) {
                    val ofsetContext = LocalContext.current
                    var ofset by remember { mutableStateOf(com.evaitec.netmovies.tv.data.SesOfseti.ms) }
                    Secim(
                        "Ses gecikmesi (Bluetooth)",
                        com.evaitec.netmovies.tv.data.SesOfseti.secenekler.map { if (it > 0) "+$it ms" else "$it ms" },
                        com.evaitec.netmovies.tv.data.SesOfseti.secenekler.indexOf(ofset),
                    ) { n ->
                        ofset = com.evaitec.netmovies.tv.data.SesOfseti.secenekler[n]
                        com.evaitec.netmovies.tv.data.SesOfseti.ayarla(ofsetContext, ofset)
                    }
                }
                // Canlı yayında hız anlamsız (canlının önüne geçilemez): satır gizli.
                if (!canli) Secim(
                    "Hız",
                    SPEEDS.map { h -> if (h % 1f == 0f) "${h.toInt()}x" else "${h}x" },
                    SPEEDS.indexOf(speed),
                ) { n -> onSelectSpeed(SPEEDS[n]) }
            }

            else -> KayanListe {
                // Kalite: kaynakların çoğu tek çözünürlük verir; o zaman liste tek
                // seçenek olur ama hangi kalitede oynadığı görünür.
                if (videoTrackCount > 0) {
                    val kaliteler = videoGroups.flatMap { g -> (0 until g.length).map { g to it } }
                    Secim(
                        "Kalite",
                        listOf("Oto") + kaliteler.mapIndexed { n, (g, i) ->
                            val fmt = g.getTrackFormat(i)
                            when {
                                fmt.height > 0 -> "${fmt.height}p"
                                fmt.bitrate > 0 -> "${fmt.bitrate / 1000} kbps"
                                else -> "Kalite ${n + 1}"
                            }
                        },
                        if (qualityAuto) 0 else 1 + kaliteler.indexOfFirst { (g, i) -> g.isTrackSelected(i) },
                    ) { n -> if (n == 0) onSelectQuality(null, 0) else kaliteler[n - 1].let { (g, i) -> onSelectQuality(g, i) } }
                }
                SettingRow("Sarma · dakikaya git · bölüm", false) { onOpenSeek() }
                SettingRow(
                    if (showKeys) "Tuş göstergesi açık" else "Tuş göstergesi kapalı",
                    showKeys,
                    onClick = onToggleKeys,
                )
                SettingRow(
                    if (showReport) "Kaynak raporu ▾" else "Kaynak raporu ▸",
                    showReport,
                    onClick = onToggleReport,
                )
                if (showReport) {
                    // Satırlar ODAK ALIR: metin olarak çizilince kumanda
                    // aradan atlıyor, ortadaki kayıtlar hiç okunmuyordu.
                    MutedRow("Telefondan/PC'den: <sunucu>:3310/api/v1/client_log")
                    val rapor = PlaybackLog.snapshot()
                    if (rapor.isEmpty()) MutedRow("Kayıt yok")
                    rapor.take(40).forEach { kayit -> SettingRow(kayit.format(), false) {} }
                }
            }
        }
    }
}

/** Sıralı sezon listesinde bir önceki/sonraki sezon; uçta ya da tek sezonda null (SOL/SAĞ boşa gitmez). */
internal fun komsuSezon(sezonlar: List<Int>, acik: Int?, yon: Int): Int? {
    if (sezonlar.size < 2) return null
    val i = sezonlar.indexOf(acik)
    if (i < 0) return sezonlar.first()
    return sezonlar.getOrNull(i + yon)
}

/** Panelin kalan boyunu dolduran dikey kayan alan: içerik ne kadar olursa olsun panel boyu değişmez. */
@Composable
private fun ColumnScope.KayanListe(modifier: Modifier = Modifier, icerik: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.weight(1f).fillMaxWidth().focusGroup().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(NmDim.ChipGap),
        content = icerik,
    )
}

/** Yatay kayan hap sırası (SOL/SAĞ içinde gezilir). */
@Composable
private fun HapSira(modifier: Modifier = Modifier, icerik: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.focusGroup().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(NmDim.ChipGap),
        verticalAlignment = Alignment.CenterVertically,
        content = icerik,
    )
}

/** Solda sabit sütunda etiket, sağında yatay haplar — sezon seçicinin deseni. */
@Composable
private fun HapSatiri(etiket: String, icerik: @Composable RowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            etiket,
            fontSize = NmType.Label,
            color = NmColor.OnSurfaceMuted,
            maxLines = 1,
            modifier = Modifier.width(NmDim.PanelLabelWidth),
        )
        HapSira(icerik = icerik)
    }
}

/** Bitişik segmentin [i]. dilimi: uçlar hap gibi yuvarlak, iç kenarlar düz. */
private fun segmentSekli(i: Int, n: Int): Shape {
    val uc = CornerSize(50)
    val ic = CornerSize(NmDim.SegmentGap)
    val bas = if (i == 0) uc else ic
    val son = if (i == n - 1) uc else ic
    return RoundedCornerShape(topStart = bas, topEnd = son, bottomEnd = son, bottomStart = bas)
}

/**
 * Tek seçimli liste segmenti: [i]'ye basıldıktan sonra her listede olup olmayacağı.
 * Tek başına seçili olana basmak onu da çıkarır; eskiden birden çok listedeyse basılan kalır.
 */
internal fun tekListe(suan: List<Boolean>, i: Int): List<Boolean> {
    val cikar = suan[i] && suan.count { it } == 1
    return suan.indices.map { j -> j == i && !cikar }
}

/** Yazısız kare liste düğmesi: sekmeden farkı odakta SEÇMEZ, OK ister. */
@Composable
private fun IkonHap(ad: String, vektor: ImageVector, secili: Boolean, modifier: Modifier = Modifier, onSec: () -> Unit) =
    IkonSekme(
        ikon = ad,
        vektor = vektor,
        secili = secili,
        modifier = modifier.width(NmDim.PanelRowHeight),
        odaktaSec = false,
        onSec = onSec,
    )

/**
 * Satır başına tek chip: kapalıyken seçili değeri gösterir, OK altında seçenekleri
 * dikey açar. YUKARI/AŞAĞI gezilir, OK seçer ve kapanır, GERİ yalnız listeyi kapatır.
 * Seçim yalnız OK ile (kaynak odakta değişirse akış kopar). Odak gruptan çıkınca kapanır.
 */
@Composable
private fun SecimChip(
    etiket: String,
    secenekler: List<String>,
    secili: Int,
    acik: Boolean,
    onAc: (Boolean) -> Unit,
    onSec: (Int) -> Unit,
) {
    val chipFocus = remember { FocusRequester() }
    val secenekFocus = remember { FocusRequester() }
    NmBackHandler(enabled = acik) {
        runCatching { chipFocus.requestFocus() }
        onAc(false)
    }
    if (acik) LaunchedEffect(Unit) {
        repeat(6) {
            withFrameNanos {}
            if (runCatching { secenekFocus.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (acik && !it.hasFocus) onAc(false) }
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(NmDim.ChipGap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                etiket,
                fontSize = NmType.Label,
                color = NmColor.OnSurfaceMuted,
                maxLines = 1,
                modifier = Modifier.width(NmDim.PanelLabelWidth),
            )
            IkonSekme(
                ikon = (secenekler.getOrNull(secili) ?: "—") + if (acik) "  ▴" else "  ▾",
                secili = acik,
                modifier = Modifier.weight(1f).focusRequester(chipFocus),
                odaktaSec = false,
                yatayPay = 14.dp,
                yaziBoyu = NmType.RowTitle,
            ) { if (secenekler.isNotEmpty()) onAc(!acik) }
        }
        if (acik) secenekler.forEachIndexed { i, s ->
            SettingRow(
                s,
                i == secili,
                Modifier
                    .padding(start = NmDim.PanelLabelWidth)
                    .then(if (i == secili.coerceAtLeast(0)) Modifier.focusRequester(secenekFocus) else Modifier),
            ) {
                runCatching { chipFocus.requestFocus() }
                onAc(false)
                onSec(i)
            }
        }
    }
}

/** Ayar şeridindeki tek sekme: ikon; [etiket] verilirse (seçili sekme) yanında adı. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun IkonSekme(
    ikon: String,
    secili: Boolean,
    modifier: Modifier = Modifier,
    etiket: String? = null,
    /** Verilirse yazı yerine ikon çizilir; [ikon] erişilebilirlik adı olur. */
    vektor: ImageVector? = null,
    /** Liste segmentinde "bu listede" işareti: ikonun yanında küçük ✓. */
    tik: Boolean = false,
    sekil: Shape = RoundedCornerShape(NmDim.RowRadius),
    /** Sekme/sezon odakta seçer; hap (kaynak, ses, liste) yalnız OK ile. */
    odaktaSec: Boolean = true,
    yatayPay: Dp = 0.dp,
    /** Odakta değilken ikon rengi (Kayıt: kırmızı). */
    ikonRengi: Color? = null,
    yaziBoyu: TextUnit = if (etiket != null) NmType.RowTitle else NmType.Body,
    onSec: () -> Unit,
) {
    var odakli by remember { mutableStateOf(false) }
    val shape = sekil
    Box(
        modifier = modifier
            .height(NmDim.PanelRowHeight)
            .clip(shape)
            .background(
                when {
                    odakli -> NmColor.Primary
                    secili -> NmColor.PrimarySelected
                    else -> NmColor.Surface
                },
            )
            .nmFocusRing(odakli, shape)
            .onFocusChanged {
                odakli = it.isFocused
                // Odak gezinirken içerik de değişir: ayrıca OK'a basmak gerekmiyor —
                // "buton içinde gezinir seçeriz" isteği bu.
                if (it.isFocused && odaktaSec) onSec()
            }
            // Ayrı focusable() yok: clickable zaten odak hedefi, ikisi birlikte çift
            // durak yapıyordu (hafıza: tv-focus-and-install-traps).
            .clickable { onSec() }
            .padding(horizontal = yatayPay),
        contentAlignment = Alignment.Center,
    ) {
        if (vektor != null) Row(verticalAlignment = Alignment.CenterVertically) {
            val renk = ColorFilter.tint(if (odakli) NmColor.OnPrimary else ikonRengi ?: NmColor.OnSurface)
            Image(vektor, contentDescription = ikon, modifier = Modifier.size(NmDim.PanelIconSize), colorFilter = renk)
            if (tik) Image(Icons.Filled.Check, contentDescription = "listede", modifier = Modifier.size(NmDim.PanelIconSize * 0.7f), colorFilter = renk)
        } else Text(
            if (etiket != null) "$ikon $etiket" else ikon,
            fontSize = yaziBoyu,
            fontWeight = if (odakli || secili) FontWeight.Bold else FontWeight.Normal,
            color = if (odakli) NmColor.OnPrimary else NmColor.OnSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Bölüm satırı: "S1 B3" sabit sütunda, ad tek satır; oynayan ▶, izlenmiş ✓ ve soluk.
 *  Seçim indeksle geri döner ama indeks bu listeden gelir, numara yalnız etikettir. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun BolumSatiri(
    ep: com.evaitec.netmovies.tv.data.EpisodeItem,
    index: Int,
    oynuyor: Boolean,
    izlendi: Boolean = false,
    // Odak yerine tuşla seçim (kart pad'i): aynı satır, aynı görünüm.
    secili: Boolean = false,
    // Sunucu diskinde hazır kaydı var: kırmızı ● (internetsiz oynar).
    kayitli: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var odakli by remember { mutableStateOf(false) }
    if (secili) odakli = true
    val shape = RoundedCornerShape(NmDim.RowRadius)
    val yazi = when {
        odakli -> NmColor.OnPrimary
        izlendi -> NmColor.OnSurfaceFaint
        else -> NmColor.OnSurface
    }
    val no = ep.episode ?: (index + 1)
    // "1x3" gibi başlık kodu tekrarlar; boşsa ya da yalnız kodsa "Bölüm N".
    val ad = ep.title?.trim()?.takeIf { it.isNotBlank() && !Regex("""\d+x\d+""").matches(it) } ?: "Bölüm $no"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    odakli -> NmColor.Primary
                    oynuyor -> NmColor.PrimarySelected
                    else -> NmColor.Surface
                },
            )
            .nmFocusRing(odakli, shape)
            .onFocusChanged { if (!secili) odakli = it.isFocused }
            .clickable { onClick() }
            // SettingRow ile aynı iç boşluk: Kitaplık ve Bölümler aynı satır boyunda.
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "S${ep.season} B$no",
            fontSize = NmType.Body,
            fontWeight = FontWeight.Bold,
            color = when {
                odakli -> NmColor.OnPrimary
                izlendi -> NmColor.OnSurfaceFaint
                else -> NmColor.Primary
            },
            maxLines = 1,
            modifier = Modifier.width(NmDim.EpisodeNumWidth),
        )
        Text(
            text = ad,
            fontSize = NmType.Body,
            fontWeight = if (odakli || oynuyor) FontWeight.SemiBold else FontWeight.Normal,
            color = yazi,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (kayitli) Text(com.evaitec.netmovies.tv.data.Library.REC, fontSize = NmType.Body, color = NmColor.Rec)
        when {
            oynuyor -> Text("▶", fontSize = NmType.Body, color = if (odakli) NmColor.OnPrimary else NmColor.Primary)
            izlendi -> Text("✓", fontSize = NmType.Body, color = yazi)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
// Bölüm etiketi: kaynak başlığı bölüm numarasını taşımıyor ("Red Flags"), sezon/bölüm
// bilgisi ayrı alanlarda geliyor. İkisi birleşmezse listede hangi bölüm olduğu okunmuyor.
internal fun episodeLabel(ep: com.evaitec.netmovies.tv.data.EpisodeItem, index: Int): String {
    val numara = ep.episode?.let { "S${ep.season}B$it" } ?: "Bölüm ${index + 1}"
    val ad     = ep.title?.takeIf { it.isNotBlank() }
    return if (ad != null) "$numara · $ad" else numara
}

// İçerik açılınca gelen başlangıç paneli. Odak doğrudan OYNAT'ta: kullanıcı
// çözümleme sürerken paneli okuyup tek OK ile başlatır, "play'i arama" yok.
// Bölüm listesi ve kaynak/dil aynı panelde — ayrı istek yok, liste zaten
// resolve_sources yanıtından geliyor.
/** Panelin OYNAT satırı tek cümlede ne yapacağını söyler: yarım kalan varsa devam,
 *  yeni diziye giriliyorsa 1. bölüm, filmde düz oynat; oynarken hangi bölümün çaldığı. */
internal fun oynatEtiketi(
    resumeLabel: String?,
    episodes: List<com.evaitec.netmovies.tv.data.EpisodeItem>,
    currentEpIndex: Int,
    oynuyor: Boolean,
): String {
    val bolum = episodes.getOrNull(currentEpIndex)?.let { episodeLabel(it, currentEpIndex) }
    return when {
        oynuyor && bolum != null -> "▶  $bolum — oynuyor"
        oynuyor                  -> "▶  Oynuyor"
        resumeLabel != null      -> "▶  Devam et — $resumeLabel"
        bolum != null            -> "▶  $bolum — baştan"
        else                     -> "▶  Oynat"
    }
}

/** Yıl · tür · ★ puan — başlangıç panelinin başlık altı satırı. */
internal fun icerikBilgisi(details: com.evaitec.netmovies.tv.data.ItemDetails?, rating: Double?): String = listOfNotNull(
    details?.yearText?.takeIf { it.isNotBlank() },
    details?.tagsText?.takeIf { it.isNotBlank() },
    (details?.ratingText?.takeIf { it.isNotBlank() } ?: rating?.let { "%.1f".format(it) })?.let { "★ $it" },
).joinToString("  ·  ")

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MutedRow(text: String) {
    Text(
        text = text,
        color = NmColor.OnSurfaceFaint,
        fontSize = NmType.Body,
        modifier = Modifier.padding(vertical = 3.dp),
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingRow(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(NmDim.RowRadius)
    Box(
        modifier = modifier
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
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = (if (selected) "●  " else "     ") + label,
            fontSize = NmType.RowTitle,
            color = if (isFocused) NmColor.OnPrimary else if (selected) NmColor.OnSurface else NmColor.OnSurfaceMuted,
            fontWeight = if (isFocused || selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// Durum yazıları (yükleniyor / kaynak aranıyor / tazeleniyor) EKRANIN ORTASINDA
// duruyordu — film üstünde kocaman bir kutu (Dean: "ortada çok çirkin"). Hepsi
// tek biçimde bir köşe kutusunda: gösterge + kısa metin.
//
// Köşe SOL alt: sağ altta başlangıç paneliyle ve bölüm sonu kartlarıyla üst üste
// biniyordu (Dean: "menünün üstüne geliyor, sola dayayalım").
// loader=false → arama bitmiş ve sonuç yok; dönen halka yerine ✕.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun CornerStatus(message: String, loader: Boolean) {
    Box(Modifier.fillMaxSize().padding(NmDim.SafeArea), contentAlignment = Alignment.BottomStart) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(NmDim.PanelRadius))
                .background(NmColor.ScrimSoft)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loader) {
                NmLoader(size = 26.dp)
            } else {
                Text("✕", fontSize = NmType.Label, fontWeight = FontWeight.Bold, color = NmColor.Primary)
            }
            Text(
                text = message,
                fontSize = NmType.Label,
                color = NmColor.OnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 420.dp),
            )
        }
    }
}
