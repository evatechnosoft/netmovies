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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
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
private val SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

// Kumandanın oynatma tuşları — D-pad'den ayrı, eşlemeye girmez, oynatıcıda sabit.
private val MEDIA_KEYS = setOf(
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

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    item: MediaItem,
    bindings: KeyBindings,
    library: Library,
    onBack: () -> Unit,
    /** Uygulamanın ana ekranına dön (sistemin HOME tuşu değil — o uygulamaya gelmez). */
    onHome: () -> Unit = onBack,
    /** Canlı TV'de gezilecek kanal listesi; boşsa kanal geçişi kapalı. */
    kanallar: List<MediaItem> = emptyList(),
    /** Kanal değişimi — ekranı kapatmadan aynı oynatıcıda yeni kanala geçilir. */
    onKanal: (MediaItem) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Cihaza özel oynatıcı tercihleri. Sunucuda değil burada: tunneling desteği
    // televizyonun kendi kod çözücüsünün meselesi, başka cihazı ilgilendirmez.
    val oynaticiPrefs = remember { context.getSharedPreferences("player", android.content.Context.MODE_PRIVATE) }
    // Tunneling: Android TV'de ses ve görüntü aynı donanım hattından gider,
    // senkron kayması ve ses tamponu boşalması belirgin azalır. Destek cihaza
    // göre değişir — açılmıyorsa ilk hatada kalıcı kapanır (aşağıda).
    var tunneling by remember { mutableStateOf(oynaticiPrefs.getBoolean("tunneling", true)) }
    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setTunnelingEnabled(tunneling)
                    // Aynı akışta kod çözücü değişimi gerekiyorsa engelleme:
                    // kısıt yüzünden hiç video seçilmemesi daha kötü.
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
            // Varsayılan tampon ev ağı için kısaydı: segmentler ev upload'ından
            // geçtiği için tek yavaş segment doğrudan sese yansıyordu. Televizyonda
            // bellek bol, hat dar — tamponu büyütmek doğru takas.
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(30_000, 90_000, 3_000, 6_000)
                    .setBackBuffer(20_000, true)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()
            )
            .build()
    }
    // Preview (scrub önizleme) oynatıcısı: aynı kaynak, düşük kalite, duraklatılmış,
    // hızlı seek (CLOSEST_SYNC). Küçük bir surface'e render edilip thumbnail gibi gösterilir.
    //
    // TEMBEL: eskiden ekran açılır açılmaz kuruluyor ve aynı HLS akışını paralel
    // hazırlıyordu — ikinci kod çözücü, ikinci indirme zinciri, scrub yapılmasa
    // bile. Mi Box sınıfı cihazda ses tamponunu boşaltan en ağır yüktü. Artık
    // yalnız scrub başlayınca kurulur, scrub bitince bırakılır.
    var previewExo by remember { mutableStateOf<ExoPlayer?>(null) }

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

    // Tuş göstergesi: basılan tuşun ADI ve oynatıcıdaki karşılığı köşede görünür.
    // Kumandada hangi tuşun ne ürettiği (özellikle ⏪⏩ gibi medya tuşları) başka
    // türlü bilinmiyordu — logcat'e bakmadan, televizyonun kendisinde.
    // Tercih buton eşlemesiyle aynı dosyada tutulur; oynatıcıdan çıkınca kaybolmasın.
    val keyPrefs = remember {
        context.getSharedPreferences("netmovies_keymap", android.content.Context.MODE_PRIVATE)
    }
    var showKeys by remember { mutableStateOf(keyPrefs.getBoolean("show_keys", true)) }
    var keyHint by remember { mutableStateOf<String?>(null) }
    var keyTick by remember { mutableIntStateOf(0) }

    // Hızlı pad: sağ altta açılan kumanda kutusu. Çift basış / basılı tutma
    // öğrenmek yerine her şey tek OK ile bir düğmede (Dean: "4'lü pad açılır,
    // ileri geri sarma, bölüm seçme, hepsi orada"). Kumandada boşta duran bir
    // tuş (Netflix/Prime) açar — hangi tuş olduğunu uygulama bilmek zorunda değil,
    // BAŞKA BİR İŞE BAĞLI OLMAYAN her tuş açar.
    var showPad by remember { mutableStateOf(false) }
    // Pad'de seçili düğme İNDEKSLE tutulur, Compose odağıyla değil: odak sistemi
    // TV'de şeridin ilk düğmesini yakalayamayınca SOL/SAĞ kök kutuya düşüp sarma
    // yapıyordu (Dean, 18 Eylül: "tuşlar üzerinde dolaşmıyor, sadece sarma
    // yapıyor"). İndeks deterministik: şerit her zaman gezilir.
    var padSecim by remember { mutableIntStateOf(3) }   // 3 = Oynat/Duraklat
    // Pad'i açan tuşun BIRAKILMA olayı pad'e ait değil: yoksa parmak kalkarken
    // odaktaki düğmeye basmış oluyor (aynı tuzak uzun basışta yaşanmıştı).
    var padKey by remember { mutableIntStateOf(-1) }
    // MENÜ basılı mı tutuldu: tek basış ayarları açar, basılı tutma sisteme kalır.
    var menuUzun by remember { mutableStateOf(false) }

    // Bölüm durumu: onDispose içindeki ilerleme kaydı da okuduğu için oynatıcı
    // kurulumundan ÖNCE tanımlı olmalı.
    var episodes by remember { mutableStateOf<List<com.evaitec.netmovies.tv.data.EpisodeItem>>(emptyList()) }
    // Telefon bölüm seçtiyse oradan başlar; yoksa 0.
    var currentEpIndex by remember(item.url) { mutableIntStateOf(item.episode.coerceAtLeast(0)) }
    // Başlangıç paneli bilgi alanı (özet, yıl, tür, puan) — load_item'dan, tek istek.
    var details by remember(item.url) { mutableStateOf<com.evaitec.netmovies.tv.data.ItemDetails?>(null) }
    // AKTİF sağlayıcı: kart hangi siteden geldiyse oradan başlar, ama bölüm listesi
    // daha zengin bir sağlayıcıda bulunursa oynatma da oraya geçer. Kartın eksik
    // listesi yüzünden yeni bölüm görünmüyordu (18 Eylül ölçümü: Dead City'de
    // HDFilmCehennemi 7 bölüm, DiziMom 20).
    var aktifPlugin by remember(item.url) { mutableStateOf(item.plugin) }
    var aktifUrl by remember(item.url) { mutableStateOf(item.url) }
    // Listenin hangi sağlayıcıdan geldiği panelde yazsın.
    var listeKaynagi by remember(item.url) { mutableStateOf<String?>(null) }
    // Başlangıç paneli: içerik açılır açılmaz gelir ve çözümleme bitene kadar
    // ekranda kalır. Odak OYNAT'ta; bölüm ve kaynak/dil aynı panelde. Kullanıcı
    // OYNAT'a basmadan akış başlamaz — yanlış içeriğe girip izlemeye başlamak yok.
    var showStartPanel by remember(item.url) { mutableStateOf(!item.autoplay) }
    // Aynı panel iki işi görür: içerik açılırken "başlangıç", oynarken "bölüm listesi".
    // Ayrımı GERİ belirler — başlangıçta içerikten çıkar, listede yalnız paneli kapatır.
    var panelAsList by remember(item.url) { mutableStateOf(false) }
    // Ayar paneli Bölümler sekmesinde mi açılsın: "Bölümler" girişleri artık tam
    // ekran liste yerine paneli kullanıyor (Dean, 18 Eylül: "yine koca bir liste").
    var ayarBolumler by remember(item.url) { mutableStateOf(false) }
    // Bölüm seçici sayfası: null = sezon sayfası. Durum burada tutulur çünkü
    // GERİ tuşu bu ekranda değil, oynatıcının tuş işleyicisinde yakalanıyor.
    var secilenSezon by remember(item.url) { mutableStateOf<Int?>(null) }
    // Ayarlar başlangıç panelinin ÜSTÜNE açılıyordu: iki modal üst üste kalınca
    // odak ikisi arasında gidip geliyor ve hiçbir satır seçilemiyordu (Dean:
    // "2 popup açık olunca seçmiyor"). Ayarlar açılırken panel kapanır, ayarlar
    // kapanınca geri gelir.
    var panelGeriGelsin by remember(item.url) { mutableStateOf(false) }
    // OYNAT'a panel açıkken basıldıysa: kaynak henüz yokken de kabul edilir,
    // hazır olduğu anda başlar.
    var playRequested by remember(item.url) { mutableStateOf(item.autoplay) }
    // Panelin OYNAT satırı için "nereden devam" bilgisi. Kayıt sunucuda; panel
    // çözümlemeyi beklemeden gösterilebilsin diye ayrıca burada okunuyor.
    var resumeLabel by remember(item.url) { mutableStateOf<String?>(null) }
    // Kaydın HANGİ bölüme ait olduğu. İzleme kaydı dizi başına tutuluyor (anahtar
    // tür-agnostik); bölüm bilgisi kaydın içinde. Bu olmadan 5. bölümü açarken
    // "7. bölüm 9. dk" yazıyor ve o konuma atlıyordu.
    var resumeEpisode by remember(item.url) { mutableStateOf<Int?>(null) }

    // Bölüm işaretleri: açılış şarkısı ve jenerik başlangıcı (sunucu altyazıdan
    // çıkarır — /api/v1/markers). Bulunamazsa null kalır ve hiçbir şey gösterilmez;
    // o zaman eski davranış (son 90 sn teklifi) sürer.
    // Anahtarda BÖLÜM de var: sonraki bölüme geçildiğinde item.url değişmez, ama
    // işaretler ve iptal kararı o bölüme aittir — taşınırsa yeni bölümün jeneriği
    // eski bölümün dakikasında aranır.
    var markers by remember(item.url, currentEpIndex) { mutableStateOf<com.evaitec.netmovies.tv.data.Markers?>(null) }
    // Jenerikte başlayan otomatik geçiş geri sayımı. null = sayım yok.
    var geriSayim by remember(item.url, currentEpIndex) { mutableStateOf<Int?>(null) }
    // Kullanıcı GERİ ile sayımı durdurdu: bu bölümde bir daha başlamaz — jeneriği
    // izlemek isteyen kişiyi her saniye yeniden uyarmanın anlamı yok.
    var otoGecisIptal by remember(item.url, currentEpIndex) { mutableStateOf(false) }
    // Akış STATE_ENDED'e ulaştı: jenerik işareti olmayan bölümde sayım buradan başlar.
    var akisBitti by remember(item.url, currentEpIndex) { mutableStateOf(false) }
    // Bu bölüm için TÜM kaynaklar "çok kısa" çıktı (bkz. MIN_GECERLI_SURE_MS):
    // sağlayıcı kaldırılmış bölümün yerine birkaç saniyelik uyarı/tutundurma klibi
    // koymuş olabilir. True olunca sonraki bölüme otomatik geçiş tamamen kilitlenir —
    // STATE_ENDED gelse bile akisBitti/sonrakiTeklif bunu "izlendi" saymaz.
    var akisGecersiz by remember(item.url, currentEpIndex) { mutableStateOf(false) }

    LaunchedEffect(item.url) {
        details = runCatching { Network.api.loadItem(aktifPlugin, aktifUrl).result }.getOrNull()
        // Bölüm listesi zincirden ÖNCE gelir: load_item tek istek, resolve_sources
        // ise sağlayıcı taraması. Panel böylece bölümleri anında gösterir ve
        // sıralama da uyumlu — sunucu bölümü aynı listeden indeksliyor
        // (resolve_sources: `info.episodes[episode_index]`).
        val bolumler = details?.episodes.orEmpty()
        if (bolumler.isNotEmpty() && episodes.isEmpty()) episodes = bolumler

        // Kart BÖLÜM sayfası olabilir (DiziMom "Son Bölümler" ve araması böyle
        // veriyor): sunucu o adresten dizinin tamamını döndürür, ama listede
        // kaçıncı bölüme tıklandığı yalnız adresten anlaşılır. Eşleşmezse zincir
        // 1. bölümü açardı — tıklanan S3B7 değil.
        if (item.episode < 0 && bolumler.isNotEmpty()) {
            val acilan = com.evaitec.netmovies.tv.data.rawUrl(item.url).trimEnd('/')
            var sira = bolumler.indexOfFirst { it.url.trimEnd('/') == acilan }
            // Adres eşleşmesi tutmayabiliyor: katalog kartının adresi bölüm
            // listesindekinden farklı biçimde gelebiliyor (kodlama, ek parametre).
            // O zaman BAŞLIK söyler: "… 3.Sezon 8.Bölüm" (Dean, 18 Eylül: kart
            // 3. sezon 8. bölümü açıyordu, panel "S1B1 baştan" öneriyordu).
            if (sira < 0) {
                sira = basliktanBolum(item.title, bolumler)
            }
            if (sira >= 0) {
                currentEpIndex = sira
                // Bölüm zaten belli: başlangıç paneli "hangi bölüm" diye sormasın,
                // doğrudan o bölüm açılsın.
                showStartPanel = false
                playRequested = true
                exo.playWhenReady = true
            }
        }

        // Kullanıcı bölüm seçmediyse (favori kartı, arama sonucu) kayıttaki bölümden
        // devam edilir: `episode = -1` sözleşmesi zaten "kayıttan/baştan" diyor, ama
        // indeks 0'a kırpılıp her açılışta 1. bölüm oynuyordu (Dean, 17 Eylül:
        // "favoriden açtım bir bölüm başladı, 1. Bölüm yazıyor").
        if (item.episode < 0 && bolumler.isNotEmpty() && currentEpIndex == 0) {
            val kayit = library.loadProgress(item.title.orEmpty(), isSerie = true)
            com.evaitec.netmovies.tv.data.episodeIndexOf(kayit?.episode.orEmpty(), bolumler)
                ?.let { currentEpIndex = it }
        }

        // Telefondan gönderilen DİZİ doğrudan 1. bölümden başlıyordu. Telefon
        // belirli bir bölüm seçmediyse (episode < 0) karar TV'de verilir: panel
        // açılır, kullanıcı son bölümü ya da istediğini seçer.
        if (bolumler.isNotEmpty() && item.autoplay && item.episode < 0 && exo.currentPosition <= 0L) {
            playRequested = false
            exo.playWhenReady = false
            showStartPanel = true
        }
    }


    // Scrub / önizleme modu.
    var scrubMode by remember { mutableStateOf(false) }
    var scrubPos by remember { mutableLongStateOf(0L) }
    var scrubTick by remember { mutableIntStateOf(0) }

    val rootFocus = remember { FocusRequester() }
    val panelFocus = remember { FocusRequester() }

    // ---- Aksiyon dağıtıcı: eşlenen tuş → oynatıcı davranışı ----
    fun flashControls() { showControls = true; controlsTick++ }

    // ---- Birikimli sarma ----
    // Eskiden her basış/tekrar ANINDA exo.seekTo çağırıyordu: basılı tutmada
    // saniyede ~20 seek isteği HLS'de üst üste biniyor, tuş bırakıldıktan sonra
    // da oynatıcı sıraya girmiş seek'leri işlemeye devam ediyordu (Dean: "8 10 30
    // atlama durmamakta"). Şimdi basışlar bir HEDEF konumda birikir, ekranda
    // hedef/ofset gösterilir, son basıştan 350 ms sonra TEK seek yapılır.
    // OK'e basmak hedefi hemen uygular ("orda durabileyim").
    var seekTarget by remember { mutableStateOf<Long?>(null) }
    var seekJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var lastHoldAt by remember { mutableLongStateOf(0L) }
    fun commitSeek() {
        val t = seekTarget ?: return
        seekTarget = null
        exo.seekTo(t)
        position = t
    }
    fun seekBy(deltaMs: Long) {
        val dur = exo.duration
        val base = seekTarget ?: exo.currentPosition
        val target = (base + deltaMs).let {
            if (dur > 0) it.coerceIn(0, dur) else it.coerceAtLeast(0)
        }
        seekTarget = target
        position = target
        val ofset = target - exo.currentPosition
        seekHint = (if (ofset >= 0) "+" else "−") + fmtDelta(ofset) + "  →  " + fmtTime(target)
        hintTick++
        flashControls()
        seekJob?.cancel()
        seekJob = scope.launch { delay(350); commitSeek() }
    }
    // Basılı tutma: tekrarlar 120 ms'de bire indirilir, adım tutma süresiyle
    // büyür — ilk 1.5 sn 10 sn'lik, 4 sn'ye kadar 30 sn'lik, sonrası 1 dk'lık.
    // Filmde 2 saatlik içerikte hedefe dakikalarca değil saniyelerce basarak varılır.
    fun seekHold(yon: Int, heldMs: Long) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastHoldAt < 120) return
        lastHoldAt = now
        val adim = when {
            heldMs < 1_500 -> 10_000L
            heldMs < 4_000 -> 30_000L
            else -> 60_000L
        }
        seekBy(yon * adim)
    }
    // CANLI yayın: kanal akışı geriye doğru bir tampon (DVR penceresi) taşıyor —
    // Show TV'de ~59 dk. 10-30 sn'lik adımlarla oraya inmek işkenceydi
    // (Dean: "geri almak yavaş yavaş sorun, tampon başına gidebilir").
    // Tamponun başı = pencerenin en eski noktası, yayının "baştan" izlenebilecek yeri.
    val canliYayin = exo.isCurrentMediaItemLive
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
    // Sıradaki bölüm: dizide ve son bölümde değilsek var.
    // Sol üst şerit: dizide "Ad · S1B3", filmde yalnız ad. Bölüm numarası listedeki
    // SIRA değil sağlayıcının verdiği numara — sıra yazmak web'de S4E8 olan bölümü
    // "123. bölüm" diye gösteriyordu.
    val simdikiEtiket = remember(item.title, episodes, currentEpIndex) {
        val ad = item.title.orEmpty()
        val ep = episodes.getOrNull(currentEpIndex)
        if (ep == null) ad
        else listOf(ad, "S${ep.season}B${ep.episode ?: (currentEpIndex + 1)}")
            .filter { it.isNotBlank() }.joinToString("  ·  ")
    }

    val nextEpIndex = (currentEpIndex + 1).takeIf { episodes.isNotEmpty() && it <= episodes.lastIndex }
    val prevEpIndex = (currentEpIndex - 1).takeIf { episodes.isNotEmpty() && it >= 0 }

    fun goToEpisode(idx: Int) {
        carryOverMs = 0L
        currentEpIndex = idx        // çözümleme efektinin anahtarı → yeni kaynak zinciri
        playRequested = true
        showStartPanel = false
        panelAsList = false
        showControls = false
        exo.playWhenReady = true
    }

    // Kanal geçişi yalnız canlı yayında ve liste doluyken açık.
    val kanalGecisiVar = canliYayin && kanallar.size > 1

    fun kanalAtla(yon: Int) {
        val simdiki = kanallar.indexOfFirst { it.url == item.url }
        if (simdiki < 0) return
        val hedef = kanallar[(simdiki + yon + kanallar.size) % kanallar.size]
        onKanal(hedef)
        flashControls()   // yeni kanalın adı kontrol çubuğunda görünsün
    }

    fun dispatch(a: RemoteAction) {
        when (a) {
            RemoteAction.NONE -> Unit
            // Sarma bekliyorsa OK = "burada dur": hedef hemen uygulanır, oynatma
            // sürer; duraklatma ancak ikinci OK'te.
            RemoteAction.PLAY_PAUSE ->
                if (seekTarget != null) { seekJob?.cancel(); commitSeek(); flashControls() }
                else { if (exo.isPlaying) exo.pause() else exo.play(); flashControls() }
            RemoteAction.SEEK_FWD_10 -> seekBy(10_000)
            RemoteAction.SEEK_BACK_10 -> seekBy(-10_000)
            RemoteAction.SEEK_FWD_60 -> seekBy(60_000)
            RemoteAction.SEEK_BACK_60 -> seekBy(-60_000)
            // Eşleme ekranından tek basışa atanmışsa da çalışsın: tutma süresi yok, 10 sn.
            RemoteAction.SEEK_HOLD_FWD -> seekBy(10_000)
            RemoteAction.SEEK_HOLD_BACK -> seekBy(-10_000)
            RemoteAction.OPEN_SETTINGS -> if (kanalGecisiVar) kanalAtla(-1) else showSettings = true
            // Filmde bölüm listesi yok: tuş boşa basılmasın, ayarlar açılır.
            RemoteAction.OPEN_EPISODES -> {
                ayarBolumler = episodes.isNotEmpty()
                showSettings = true
                showControls = false
            }
            RemoteAction.SHOW_CONTROLS -> flashControls()
            RemoteAction.OPEN_BAR -> { padSecim = 3; showPad = true; showControls = false }
            // Canlı yayında YUKARI/AŞAĞI klasik TV davranışı: kanal değiştirir.
            // Akışın "kaldığın yeri" yok, scrub anlamsız (Dean: "kanaldan
            // çıkmadan kanallarda gezelim"). Dizi/filmde eski davranış duruyor.
            RemoteAction.TOGGLE_SCRUB -> if (kanalGecisiVar) kanalAtla(+1) else enterScrub()
            RemoteAction.BACK -> onBack()
        }
    }
    // Pad şeridinin düğme sırası TEK yerde: burada ve QuickPad'in çiziminde aynı.
    // Sıra değişirse ikisi birden değişir (indeks tabanlı seçim buna bağlı).
    fun padCalistir(i: Int) {
        when (i) {
            0 -> { prevEpIndex?.let { goToEpisode(it) }; showPad = false }
            1 -> seekBy(-300_000)
            2 -> seekBy(-30_000)
            3 -> dispatch(RemoteAction.PLAY_PAUSE)
            4 -> seekBy(30_000)
            5 -> seekBy(300_000)
            6 -> { nextEpIndex?.let { goToEpisode(it) }; showPad = false }
            7 -> { showPad = false; ayarBolumler = episodes.isNotEmpty(); showSettings = true }
            8 -> { showPad = false; showSeek = true }          // çubuk üzerinde sarma
            9 -> { showPad = false; showSettings = true }
            10 -> { showPad = false; onHome() }
            else -> showPad = false
        }
    }

    val controller = remember {
        RemoteInputController(
            bindings, scope,
            onAction = { dispatch(it) },
            onHold = { a, heldMs ->
                when (a) {
                    RemoteAction.SEEK_HOLD_FWD -> seekHold(+1, heldMs)
                    RemoteAction.SEEK_HOLD_BACK -> seekHold(-1, heldMs)
                    else -> dispatch(a)
                }
            },
        )
    }

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
            // Geri sayım sürerken GERİ = "geçme, jeneriği izliyorum". Bölümden
            // çıkarmaz; bu bölümde sayım bir daha başlamaz.
            geriSayim != null -> otoGecisIptal = true
            scrubMode -> scrubMode = false
            showPad -> showPad = false
            // Başlangıç panelinde GERİ = içerikten çık: panel oynatmanın önündeki
            // ilk adım, kapatıp boş ekranda kalmanın anlamı yok. Oynarken açılan
            // bölüm listesinde ise arkada film var — GERİ yalnız listeyi kapatır.
            showStartPanel -> if (panelAsList) { showStartPanel = false; panelAsList = false } else onBack()
            showSettings -> {
                showSettings = false
                if (panelGeriGelsin) { panelGeriGelsin = false; showStartPanel = true }
            }
            showControls -> showControls = false
            else -> onBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose { previewExo?.release(); previewExo = null }
    }

    // Yönetim panelindeki kalite tavanı. Süreç ömrü boyunca bir kez çekilir ve
    // KAYNAK GEÇİŞİNDE KORUNUR — kullanıcının elle seçtiği kalite geçişte
    // sıfırlanıyordu, tavan sıfırlanmamalı.
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

    // Kaynak sonucunu sunucuya bildir: tarama sırası buna göre kurulur.
    // Aynı kaynak için tek kez — bir bölümde onlarca STATE_READY olur.
    val bildirilen = remember(item.url) { mutableSetOf<String>() }
    fun kaynakBildir(link: StreamLink?, oynadi: Boolean) {
        val plugin = link?.plugin?.takeIf { it.isNotBlank() } ?: return
        val anahtar = "$plugin:$oynadi"
        if (!bildirilen.add(anahtar)) return
        scope.launch {
            runCatching { Network.api.sourceEvent(SourceEvent(plugin = plugin, ok = oynadi)) }
        }
    }

    // Tunneling'i kalıcı kapat: bir kez desteklemeyen cihaz her açılışta yeniden
    // denenmemeli.
    fun tunnelingKapat(neden: String) {
        tunneling = false
        oynaticiPrefs.edit().putBoolean("tunneling", false).apply()
        trackSelector.setParameters(trackSelector.buildUponParameters().setTunnelingEnabled(false))
        PlaybackLog.warn("oynatma", "tunneling kapatıldı · $neden")
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                // Kaynak gerçekten açılınca bant kalkar; yoksa "sıradaki deneniyor"
                // yazısı film oynarken ekranda asılı kalıyordu.
                if (state == Player.STATE_READY) {
                    // Kısa akış tespiti: sağlayıcı kaldırılmış bölümün yerine on-yirmi
                    // saniyelik "İÇERİK KALDIRILDI / DMCA" ya da "DUR! GİTME!" uyarı
                    // klibi koyabiliyor. Oynatıcı bunu geçerli bölüm sanıp bitince
                    // sıradaki bölüme atlıyordu — zincirleme kayma (Dean: "1'den açtı
                    // 17'ye kadar her bölüm birkaç sn") buradan başlıyordu. Canlı
                    // yayında süre zaten anlamsız (DVR penceresi), muaf tutulur.
                    val sure = exo.duration
                    val cokKisa = !exo.isCurrentMediaItemLive && sure in 1 until MIN_GECERLI_SURE_MS
                    if (cokKisa) {
                        val kaynak = links.getOrNull(currentLinkIndex)
                        PlaybackLog.warn(
                            "oynatma",
                            "${kaynak?.let { languageLabel(it) } ?: "kaynak"} çok kısa (${sure}ms) · " +
                                "kaldırılmış bölüm klibi olabilir",
                        )
                        kaynakBildir(kaynak, false)
                        if (links.size > currentLinkIndex + 1) {
                            // Kısa klibin konumu bir sonraki kaynağa TAŞINMAZ — farklı
                            // (muhtemelen kaldırılmamış) bir akış, sıfırdan başlar.
                            carryOverMs = 0L
                            currentLinkIndex++
                            val next = links[currentLinkIndex]
                            status = "Kaynak çok kısa, sıradaki deneniyor (${currentLinkIndex + 1}/${links.size}) · ${languageLabel(next)}"
                            ready = false
                        } else {
                            // Kuyrukta başka kaynak yok: DUR. Otomatik sonraki bölüme
                            // GEÇME — kullanıcı bunun neden durduğunu görsün.
                            akisGecersiz = true
                            ready = false
                            status = BOLUM_YOK
                        }
                    } else {
                        ready = true; status = null
                        kaynakBildir(links.getOrNull(currentLinkIndex), true)
                    }
                }
                // Bölüm bitti. ESKİDEN buradan ANINDA sıradakine geçiliyordu ve son
                // sahneyi kaçıran kullanıcı kendini yeni bölümde buluyordu. Artık
                // yalnız işaret konur: geri sayım kartı (aşağıdaki efekt) devreye
                // girer, GERİ ile durdurulabilir. Jenerik işareti bulunan bölümde
                // sayım zaten daha önce, jenerik başlarken başlamıştır.
                // `akisGecersiz` iken sayılmaz: bu akış zaten kısa klip olduğu için
                // durduruldu, "bitti" değil "geçersiz" — sıradaki bölüme atlamamalı.
                if (state == Player.STATE_ENDED && !akisGecersiz) akisBitti = true
            }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlayerError(e: PlaybackException) {
                // Kod çözücü/ses hattı hatası: suçlu kaynak değil, tunneling olabilir.
                // Cihaz desteklemiyorsa kaynağı harcamadan tunneling'i kalıcı kapat
                // ve AYNI kaynağı yeniden dene — konum korunur.
                val kodCozucuHatasi = e.errorCode in setOf(
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FAILED,
                    PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
                    PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
                )
                if (tunneling && kodCozucuHatasi) {
                    tunnelingKapat("kod çözücü hatası: ${e.errorCodeName}")
                    carryOverMs = exo.currentPosition.coerceAtLeast(0L)
                    retryKey++
                    return
                }
                // Otomatik kaynak geçişi: çalmayan link kullanıcıyı ekrandan atmaz,
                // sessizce sıradaki denenir. Kuyruk bittiyse arama sürüyorsa beklenir.
                val failed = links.getOrNull(currentLinkIndex)
                PlaybackLog.fail(
                    "oynatma",
                    "${failed?.let { languageLabel(it) } ?: "kaynak"} açılmadı · ${e.errorCodeName}: ${e.message ?: "-"}",
                )
                kaynakBildir(failed, false)
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
                    status = KAYNAK_YOK
                }
            }
            override fun onTracksChanged(t: Tracks) { tracks = t }
        }
        // Ses kesilmesi teşhisi. Video akarken sesin kısa kısa gitmesi üç ayrı
        // şeyden olur ve ancak burada ayrışır: ses tamponu boşalması (underrun),
        // ses çıkışı hatası (sink) ve akış ortasında ses biçiminin değişmesi
        // (kod çözücü yeniden kurulur, arada boşluk duyulur). Üçü de Ayarlar →
        // "Kaynak raporu"na düşer; oynatmaya dokunmaz.
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

            override fun onAudioSinkError(
                eventTime: AnalyticsListener.EventTime,
                audioSinkError: Exception,
            ) {
                PlaybackLog.fail("ses", "çıkış hatası", audioSinkError)
                // Ses çıkışı tunneling ile kurulamıyorsa kalıcı kapat: bir sonraki
                // hazırlamada normal hattan gider.
                if (tunneling) tunnelingKapat("ses çıkışı hatası")
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
            // Konumu release'den ÖNCE al: sonrasında currentPosition sıfırlanır.
            // CANLI yayın kaydedilmez: "kaldığın yer" diye bir şey yok, kanal akıp
            // gidiyor. Devam Et rafını dolduruyor ve orada yanlış ad/poster
            // gösteriyordu (Show TV kaydı "Catfish" film afişiyle çıkıyordu) —
            // kanalın kendi posteri yok, raf başlığa göre eşleştirme yapıyor.
            if (!exo.isCurrentMediaItemLive) {
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
            // Devam Et rafı ve ilerleme çubuğu ancak sunucudan tazelenince güncellenir;
            // yoksa ana ekran izlemeden önceki hâlini gösteriyordu.
            library.sync()
            exo.removeListener(listener)
            exo.removeAnalyticsListener(sesDinleyici)
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

    // Oynatma günlüğü SUNUCUYA. Rapor televizyonda satır satır gezilemediği için
    // (kumanda listenin başına/sonuna atlıyor) teşhis telefondan/PC'den okunur:
    // http://<sunucu>:3310/api/v1/client_log — düz metin, en yeni üstte.
    LaunchedEffect(item.url) {
        var sonKayit: String? = null
        suspend fun gonder() {
            val satirlar = PlaybackLog.snapshot().map { it.format() }
            // Tampon dolunca satır sayısı sabit kalır; değişimi EN YENİ kayıt söyler.
            if (satirlar.isNotEmpty() && satirlar.first() != sonKayit) {
                sonKayit = satirlar.first()
                runCatching { Network.api.clientLog(mapOf("lines" to satirlar)) }
            }
        }
        // İlk gönderim 6 sn: zincir 30 sn dolmadan kesilirse (içerik değişti, kullanıcı
        // çıktı) günlük hiç gitmiyordu — teşhis edilecek olay tam da o olaydı.
        try {
            delay(6_000)
            while (true) {
                gonder()
                delay(30_000)
            }
        } finally {
            // Ekran kapanırken son hâli: iptal edilmiş coroutine suspend çağrı
            // yapamaz, bu yüzden NonCancellable.
            withContext(kotlinx.coroutines.NonCancellable) { gonder() }
        }
    }

    // Devam bilgisi paneli beklemez: çözümleme sürerken okunur, 30sn–%92 aralığı
    // oynatıcıdaki devam kuralıyla aynı — panelde "devam" yazıp sonra baştan
    // başlaması olmasın.
    LaunchedEffect(item.url, episodes) {
        // content_key tür-agnostik (watch_store.py): tip bilinmeden de kayıt bulunur.
        val row = library.loadProgress(item.title.orEmpty()) ?: return@LaunchedEffect
        val savedMs = (row.positionSeconds * 1000).toLong()
        val durMs   = (row.durationSeconds * 1000).toLong()
        if (savedMs > 30_000 && (durMs <= 0 || savedMs < durMs * 0.92)) {
            // Liste henüz gelmemişse indeks hesaplanamaz; bu efekt `episodes`
            // dolunca tekrar koşar. Eşleşme yoksa etiket bölümsüz kalır —
            // eski indeks kaydı "123. bölüm" diye ekrana basılmaz.
            resumeEpisode = episodeIndexOf(row.episode, episodes)
            val bolum = resumeEpisode
                ?.let { episodes.getOrNull(it) }
                ?.let { episodeLabel(it, resumeEpisode ?: 0) + " · " }
                ?: ""
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
    LaunchedEffect(aktifUrl, currentEpIndex) { autoRefresh = 0 }

    // Kaynak kuyruğu — zincir SUNUCUDA (/api/v1/resolve_sources).
    // İstemci yalnız iki çağrı yapar: önce fast (seçili sağlayıcı, hemen oynasın),
    // sonra full (alternatif sağlayıcılar, arka planda kuyruğa eklenir).
    // Arama/eşleştirme/dil sıralaması burada TEKRARLANMAZ — TV, telefon ve web
    // aynı listeyi aynı sırada görür.
    LaunchedEffect(aktifUrl, aktifPlugin, currentEpIndex, retryKey) {
        error = null
        ready = false
        links = emptyList()
        currentLinkIndex = 0
        searching = true
        status = "Kaynak aranıyor…"
        // Açılış sebebi günlüğe: `autoplay` yalnız telefon/saat komutuyla ya da
        // kart onayıyla gelir. "Kendiliğinden başka içerik açıldı" şikâyeti
        // (Dean, 16 Eylül) ancak bu ayrım kayıtlıysa kök nedene bağlanabilir.
        PlaybackLog.startSession(item.title, item.plugin)
        PlaybackLog.info("açılış", if (item.autoplay) "uzak komut / onay (autoplay)" else "kullanıcı seçimi")

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

        // Bölüm seçiliyse o bölümün KENDİ adresi gönderilir. Eskiden dizi sayfası +
        // `episode` İNDEKSİ gidiyordu; indeks, engine'in kendi bölüm listesindeki
        // sıraya göre çözülüyor ve listeler ayrıştığında sessizce başka bölüm
        // açılıyordu (Dean: "3 bölüm seçiyorum 1'i oynatıyor"). Adres tekil,
        // tahmin yok. Bölüm yoksa (film) eski yol.
        val bolumUrl = episodes.getOrNull(currentEpIndex)?.url?.takeIf { it.isNotBlank() }
        val cozumUrl = bolumUrl ?: aktifUrl

        // 1) Hızlı yol — seçili sağlayıcı.
        status = "$aktifPlugin deneniyor…"
        val fast = loggedOrNull("çözümleme", "resolve_sources · fast") {
            Network.api.resolveSources(
                plugin = aktifPlugin,
                encodedUrl = cozumUrl,
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
                plugin = aktifPlugin,
                encodedUrl = cozumUrl,
                title = item.title,
                episode = currentEpIndex,
                mode = "full",
            ).result
        }
        absorb(full, "full")

        searching = false
        if (links.isEmpty()) {
            PlaybackLog.fail("sonuç", "hiçbir sağlayıcı oynatılabilir kaynak vermedi")
            status = KAYNAK_YOK
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
    // Oynayan kaynağın DataSource fabrikası: önizleme oynatıcısı scrub anında
    // kurulurken aynı başlıklarla (Referer/UA) bağlanmalı.
    var aktifFactory by remember { mutableStateOf<DefaultHttpDataSource.Factory?>(null) }
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
        // Tavan akışa değil oynatıcıya ait: geçişte de geçerli kalır.
        OynatmaAyari.tavanBoyutu()?.let { (g, y) ->
            trackSelector.setParameters(trackSelector.buildUponParameters().setMaxVideoSize(g, y))
        }
        try {
            ready = false; error = null
            val headers = buildMap { if (link.referer.isNotBlank()) put("Referer", link.referer) }
            val ua = link.userAgent.ifBlank { "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_5)" }
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(ua)
                .setDefaultRequestProperties(headers)
                .setAllowCrossProtocolRedirects(true)
            aktifFactory = dataSourceFactory

            val hls = HlsMediaSource.Factory(dataSourceFactory)
                // Tek segment hatası kaynağı düşürmesin: geçici 5xx/kopmada üç
                // deneme yapılır. Eskiden ilk hata doğrudan onPlayerError'a gidip
                // çalışan kaynağı bırakıyordu.
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
            if (carryOverMs > 0) {
                exo.seekTo(carryOverMs)
                position = carryOverMs
                carryOverMs = 0L
            }
            // Panel açıkken hazırlanır ama oynamaz: kullanıcı OYNAT'a bastığında
            // (playRequested) akış zaten buffer'lanmış olur, bekleme kısalır.
            exo.playWhenReady = playRequested
            exo.setPlaybackSpeed(speed)
            // Türkçe DUBLAJ kaynakta altyazı kapalı başlar. Cihaz dili Türkçe olduğu
            // için ExoPlayer "tr" altyazıyı kendiliğinden seçiyor ve dublajlı filmin
            // üstünde altyazı akıyordu (DiziMom). Altyazılı kaynakta dokunulmaz;
            // her iki yönde de Ayarlar → Altyazı son sözü söyler.
            exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, com.evaitec.netmovies.tv.data.isDubbed(link))
                .build()

            // Önizleme oynatıcısı burada KURULMAZ — scrub başlayınca kurulur.
            // Kaynak değişti: elde kalan önizleme eski akışı gösterir, bırakılır.
            previewExo?.release()
            previewExo = null
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

    // Önizleme oynatıcısı scrub ile doğar, scrub ile ölür.
    LaunchedEffect(scrubMode) {
        if (scrubMode) {
            val link = links.getOrNull(currentLinkIndex)
            val factory = aktifFactory
            if (previewExo == null && link != null && factory != null) {
                previewExo = runCatching {
                    ExoPlayer.Builder(context).build().apply {
                        volume = 0f
                        playWhenReady = false
                        setSeekParameters(SeekParameters.CLOSEST_SYNC)
                        trackSelectionParameters = trackSelectionParameters.buildUpon()
                            .setMaxVideoSize(426, 240)
                            .setForceLowestBitrate(true)
                            .build()
                        setMediaSource(
                            HlsMediaSource.Factory(factory).createMediaSource(ExoMediaItem.fromUri(link.url))
                        )
                        prepare()
                        seekTo(scrubPos)
                    }
                }.getOrNull()
            }
        } else {
            previewExo?.release()
            previewExo = null
        }
    }
    // Scrub imleci değişince preview'ı seek et (debounce ~120ms).
    LaunchedEffect(scrubTick) {
        if (scrubMode) { delay(120); runCatching { previewExo?.seekTo(scrubPos) } }
    }
    // Scrub modunda 6sn hareketsizlikte çık.
    LaunchedEffect(scrubTick, scrubMode) {
        if (scrubMode) { delay(6000); scrubMode = false }
    }

    // Konum takibi.
    LaunchedEffect(ready) {
        while (true) {
            // Sarma bekliyorken konum HEDEFİ gösterir; gerçek konumla ezilirse
            // ilerleme çubuğu bir ileri bir geri seker.
            if (seekTarget == null) position = exo.currentPosition
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
        // Kayıt başka bir bölüme aitse konuma ATLAMA: 5. bölümü açarken 7. bölümün
        // dakikasına gitmek içeriği ortadan başlatır.
        val kayitIdx = episodeIndexOf(row.episode, episodes)
        if (episodes.isNotEmpty() && kayitIdx != currentEpIndex) return@LaunchedEffect
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
            if (exo.isCurrentMediaItemLive) continue    // canlı yayının "kaldığı yer" olmaz
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

    // Bölümler sekmesi açılınca EN ZENGİN liste aranır. Oynatıcı açılışında
    // DEĞİL: tüm sağlayıcıları taramak ~7 sn sürüyor, ilk oynatma beklemesin.
    // Daha uzun liste bulunursa oynatma da o sağlayıcıya geçer; şu anki bölüm
    // İNDEKSLE değil SEZON+BÖLÜM numarasıyla yeniden eşlenir (sağlayıcılar
    // farklı bölümden başlıyor).
    var bolumAramasiYapildi by remember(item.url) { mutableStateOf(false) }
    LaunchedEffect(ayarBolumler) {
        if (!ayarBolumler || bolumAramasiYapildi || episodes.isEmpty()) return@LaunchedEffect
        bolumAramasiYapildi = true
        val yanit = runCatching {
            Network.api.episodesBest(
                title = item.title.orEmpty(),
                plugin = aktifPlugin,
                encodedUrl = aktifUrl,
            ).result
        }.getOrNull() ?: return@LaunchedEffect
        if (yanit.episodes.size <= episodes.size) return@LaunchedEffect

        val simdiki = episodes.getOrNull(currentEpIndex)
        episodes = yanit.episodes
        listeKaynagi = yanit.plugin
        if (yanit.plugin != aktifPlugin && yanit.encodedUrl.isNotBlank()) {
            aktifPlugin = yanit.plugin
            aktifUrl = com.evaitec.netmovies.tv.data.encodedUrl(yanit.encodedUrl)
        }
        // Oynayan bölümü kaybetme: numarasıyla yeni listede bul.
        if (simdiki?.episode != null) {
            val yeni = yanit.episodes.indexOfFirst {
                it.season == simdiki.season && it.episode == simdiki.episode
            }
            if (yeni >= 0) currentEpIndex = yeni
        }
    }

    // Panel kapanınca Bölümler isteği düşer: sonraki "Ayarlar" girişi yine
    // Kitaplık sekmesinde açılsın.
    LaunchedEffect(showSettings) { if (!showSettings) ayarBolumler = false }

    // Kontrol overlay otomatik gizleme.
    LaunchedEffect(controlsTick, showControls) {
        // Orijinal modda kontroller arasında D-pad ile geziliyor: 3,5 sn gezinirken
        // ekranı kapatıyordu.
        if (showControls) { delay(3500); showControls = false }
    }
    // Sarma göstergesi otomatik gizleme.
    LaunchedEffect(hintTick) {
        if (seekHint != null) { delay(900); seekHint = null }
    }
    // Tuş göstergesi otomatik gizleme.
    LaunchedEffect(keyTick) {
        if (keyHint != null) { delay(2500); keyHint = null }
    }

    // Odak sahipliği: kök kutu odaklı değilse D-pad tuşları controller'a HİÇ gelmez —
    // ilk basış odağı taşımakla harcanıyor, kullanıcı "iki kere basınca giriyor" diyordu.
    // Tek `requestFocus()` ilk karede henüz yerleşmemiş düğümde sessizce başarısız
    // oluyordu (ModalCard'da aynı sorun kare kare denemeyle çözülmüştü).
    // `showStartPanel` de anahtar: tam ekran bölüm listesi kapanınca odağı kimse
    // geri istemiyordu, kök kutu odaksız kalıyor ve D-pad sarma tuşları hiçbir
    // yere gitmiyordu (Dean, 18 Eylül: "sağ sol sar ama olmuyor").
    LaunchedEffect(showSettings, showSeek, showPad, showStartPanel, scrubMode, ready) {
        if (showSeek || showPad || showStartPanel) return@LaunchedEffect   // bu ekranlar odağı kendi alır
        repeat(10) {
            val target = if (showSettings) panelFocus else rootFocus
            if (runCatching { target.requestFocus() }.isSuccess) return@LaunchedEffect
            withFrameNanos { }
        }
    }

    // Kaynak bulunamadı → mesajı okuyacak kadar bekle ve çık. Kullanıcı boş ekranda
    // tutulmaz. Bu arada kaynak gelirse (geç dönen sağlayıcı) çıkış iptal olur:
    // efektin anahtarı status, status null olunca yeniden kurulur ve çıkış düşer.
    LaunchedEffect(status) {
        if (status != KAYNAK_YOK) return@LaunchedEffect
        // OYNAMAYA BAŞLAMIŞ içerikte kapatma YOK. Segment ararken CDN'e
        // bağlanılamayınca (proxy: ConnectTimeout) zincir yeniden kuruluyor ve
        // bulamazsa bu mesaj düşüyordu — izlenen bölüm kendiliğinden kapanıyordu
        // (Dean, 18 Eylül: "çalışan dizi niye kapansın ki"). Açılışta hiç kaynak
        // bulunamadıysa kapanmak doğru: ekranda yapacak bir şey yok.
        if (position > 0L) return@LaunchedEffect
        delay(KAYNAK_YOK_CIKIS_MS)
        onBack()
    }

    // İşaretleri çek: süre öğrenilir öğrenilmez, kaynağın altyazısından. Anahtarda
    // `duration > 0` var — süre 500ms'de bir tazelenir, her değerinde istek atmasın.
    LaunchedEffect(currentLinkUrl, currentEpIndex, duration > 0) {
        if (duration <= 0) return@LaunchedEffect
        val altyazi = links.getOrNull(currentLinkIndex)?.subtitles
            ?.firstOrNull { it.url.isNotBlank() } ?: return@LaunchedEffect
        markers = runCatching {
            Network.api.markers(altyazi.url, duration / 1000.0).result
        }.onFailure {
            PlaybackLog.warn("isaret", "işaret alınamadı: ${it.message ?: "-"}")
        }.getOrNull()
        markers?.let {
            PlaybackLog.info(
                "isaret",
                "açılış=${it.introStart?.toInt() ?: "-"}–${it.introEnd?.toInt() ?: "-"} " +
                    "jenerik=${it.creditsStart?.toInt() ?: "-"} (${it.source ?: "-"})",
            )
        }
    }

    // Açılış aralığı ve jenerik başlangıcı — milisaniye, oynatıcı biriminde.
    val introBas = markers?.introStart?.let { (it * 1000).toLong() }
    val introBit = markers?.introEnd?.let { (it * 1000).toLong() }
    val jenerikBas = markers?.creditsStart?.let { (it * 1000).toLong() }

    val panelAcik = showStartPanel || showSettings || showSeek || scrubMode

    // "Açılışı atla": yalnız açılış şarkısı çalarken görünür. Bittiği yere atlar.
    val acilisAtlanabilir = introBas != null && introBit != null &&
        position in introBas..introBit && !panelAcik

    // Geri sayım: jenerik başladığında (işaret varsa) ya da akış bittiğinde
    // (işaret yoksa). İptal edilmişse bir daha başlamaz.
    val jenerikte = jenerikBas != null && duration > 0 && position >= jenerikBas
    // `!akisGecersiz`: kısa klip STATE_ENDED'e ulaşsa bile bu gerçek izleme değil,
    // otomatik geçişin ikinci savunması — kök neden STATE_READY'de engellense de
    // burada da kapalı tutulur.
    val sayimBaslasin = nextEpIndex != null && !otoGecisIptal && !akisGecersiz && (jenerikte || akisBitti)

    LaunchedEffect(sayimBaslasin, nextEpIndex) {
        if (!sayimBaslasin || nextEpIndex == null) { geriSayim = null; return@LaunchedEffect }
        for (kalan in NEXT_COUNTDOWN_SEC downTo 1) {
            geriSayim = kalan
            delay(1000)
        }
        geriSayim = null
        goToEpisode(nextEpIndex)
    }

    // Bölüm sonu teklifi: bitmeye az kala köşede "sonraki bölüm" kartı çıkar ve
    // SAĞ ok onu açar. Pencere dışında SAĞ hâlâ ileri sarmadır — buton eşlemesi
    // bozulmaz, kullanıcı yeni bir tuş öğrenmez.
    // Jenerik işareti VARSA bu kart hiç çıkmaz: sabit 90 sn penceresi jeneriğin
    // nerede başladığını bilmiyordu, işaret biliyor — ikisi üst üste binmesin.
    // `duration >= MIN_GECERLI_SURE_MS`: süre çok kısaysa (kısa klip) her konum
    // "bitmeye az kaldı" penceresine girer — teklif kartı içerik açılır açılmaz
    // çıkardı. `!akisGecersiz` STATE_READY'deki kök-neden engelinin ikinci savunması.
    val sonrakiTeklif = nextEpIndex != null && duration >= MIN_GECERLI_SURE_MS && jenerikBas == null &&
        geriSayim == null && !akisGecersiz &&
        (duration - position) in 0..NEXT_EPISODE_WINDOW_MS && !panelAcik

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
                // Tuş göstergesi buradan beslenir: kökten aşağı İLK yol, yani
                // hangi panel açık olursa olsun her tuş buraya uğrar.
                if (showKeys &&
                    ke.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    ke.nativeKeyEvent.repeatCount == 0
                ) {
                    keyHint = keyLabel(ke.nativeKeyEvent.keyCode, bindings)
                    keyTick++
                }
                // Boşta duran tuş → hızlı pad (aç/kapa). Tuşun bırakılması da
                // buraya ait: pad'deki düğmeye kazara basılmasın.
                if (padAcarMi(ke.nativeKeyEvent.keyCode)) {
                    // Başka bir panel açıkken pad açılmaz: iki modal üst üste
                    // gelince odak ikisi arasında kayboluyor.
                    val baskaPanel = showSettings || showSeek || showStartPanel || scrubMode
                    when (ke.nativeKeyEvent.action) {
                        KeyEvent.ACTION_DOWN -> if (ke.nativeKeyEvent.repeatCount == 0 && !baskaPanel) {
                            showPad = !showPad
                            padKey = ke.nativeKeyEvent.keyCode
                        }
                        KeyEvent.ACTION_UP -> if (padKey == ke.nativeKeyEvent.keyCode) padKey = -1
                    }
                    return@onPreviewKeyEvent true
                }
                if (!showStartPanel || ke.nativeKeyEvent.keyCode != KeyEvent.KEYCODE_BACK) {
                    return@onPreviewKeyEvent false
                }
                if (ke.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                    val cokSezon = episodes.map { it.season }.distinct().size > 1
                    when {
                        // Bölüm sayfasından önce sezon sayfasına dönülür: sayfa
                        // geçişinin geri adımı da tek seviye olsun.
                        panelAsList && secilenSezon != null && cokSezon -> secilenSezon = null
                        panelAsList -> { showStartPanel = false; panelAsList = false }
                        else -> onBack()
                    }
                }
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
                    // MENÜ: TEK basış uygulamanın işini yapar (oynatıcı ayarları),
                    // BASILI TUTMA uygulamaya ait değildir — olay tüketilmez, tuşun
                    // kendi/sistem işlevi neyse o çalışır.
                    ke.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU -> when {
                        ke.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                            ke.nativeKeyEvent.repeatCount == 0 -> { menuUzun = false; true }
                        ke.nativeKeyEvent.action == KeyEvent.ACTION_DOWN -> { menuUzun = true; false }
                        ke.nativeKeyEvent.action == KeyEvent.ACTION_UP && !menuUzun -> {
                            showSettings = true
                            true
                        }
                        else -> false
                    }
                    // Kumandanın oynatma tuşları (⏪ ⏩ ⏮ ⏭ ⏯). Bunlar D-pad değil,
                    // buton eşlemesine girmiyorlar ve hiçbir yere bağlı DEĞİLDİLER:
                    // basınca hiçbir şey olmuyordu (useController=false → ExoPlayer de
                    // dinlemiyor). Sarma ve bölüm değiştirme doğrudan burada.
                    ke.nativeKeyEvent.keyCode in MEDIA_KEYS -> {
                        if (ke.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                            val ne = ke.nativeKeyEvent
                            val tekrar = ne.repeatCount > 0
                            when (ne.keyCode) {
                                // ⏪/⏩ tek basış 30 sn; basılı tutmada D-pad ile aynı
                                // kademeli motor (tekrar başına seek YOK).
                                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD ->
                                    if (tekrar) seekHold(+1, ne.eventTime - ne.downTime) else seekBy(30_000)
                                KeyEvent.KEYCODE_MEDIA_REWIND ->
                                    if (tekrar) seekHold(-1, ne.eventTime - ne.downTime) else seekBy(-30_000)
                                // Diğer medya tuşları tekrarda ikinci kez tetiklenmez.
                                else -> if (!tekrar) when (ne.keyCode) {
                                    // Dizide bölüm atlar; CANLI yayında tamponun başına /
                                    // canlıya gider; filmde ±1 dk sarar.
                                    KeyEvent.KEYCODE_MEDIA_NEXT ->
                                        nextEpIndex?.let { goToEpisode(it) }
                                            ?: if (canliYayin) canliyaDon() else seekBy(60_000)
                                    KeyEvent.KEYCODE_MEDIA_PREVIOUS ->
                                        prevEpIndex?.let { goToEpisode(it) }
                                            ?: if (canliYayin) tamponBasina() else seekBy(-60_000)
                                    KeyEvent.KEYCODE_MEDIA_PLAY     -> { exo.play(); flashControls() }
                                    KeyEvent.KEYCODE_MEDIA_PAUSE    -> { exo.pause(); flashControls() }
                                    KeyEvent.KEYCODE_MEDIA_STOP     -> onBack()
                                    else                            -> dispatch(RemoteAction.PLAY_PAUSE)
                                }
                            }
                        }
                        true
                    }
                    // Açılış şarkısı çalarken SAĞ ok = açılışı atla. Pencere dışında
                    // SAĞ hâlâ ileri sarmadır; teklif kartındaki desenin aynısı, yeni
                    // tuş öğrenilmiyor.
                    acilisAtlanabilir && ke.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (ke.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && introBit != null) {
                            exo.seekTo(introBit)
                            position = introBit
                            seekHint = "⏭ Açılış atlandı"
                            hintTick++
                        }
                        true
                    }
                    // Geri sayım sürerken SAĞ ok = beklemeden geç.
                    geriSayim != null && ke.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (ke.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) nextEpIndex?.let { goToEpisode(it) }
                        true
                    }
                    // Teklif penceresinde SAĞ ok = sonraki bölüm.
                    sonrakiTeklif && ke.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (ke.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) nextEpIndex?.let { goToEpisode(it) }
                        true
                    }
                    // Kontroller açıkken AŞAĞI ok = odağı çubuktaki düğmelere indir
                    // (Compose'un kendi odak gezinmesi). Kapalıyken eski davranış:
                    // AŞAĞI alt kumanda barını (QuickPad) açar.
                    showControls && !showPad && !scrubMode &&
                        ke.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> false
                    scrubMode -> handleScrubKey(ke.nativeKeyEvent)
                    // Uzun basışla açılan panelin, tuş bırakılırken kendi kendini
                    // kapatmasını engeller — bırakma olayı controller'a aittir.
                    controller.consumesPendingUp(ke.nativeKeyEvent) -> true
                    // Bölüm seçici de bir modal: tuşlar yutulunca liste hiç hareket
                    // etmiyordu (Dean: "bölüm seçimi açılıyor, hareket etmiyor").
                    // Pad açıkken SOL/SAĞ şeritte gezer, OK uygular, GERİ kapatır.
                    // Compose odak gezinmesine bırakılmıyor: pad'in ilk odağı
                    // yerleşmediğinde tuşlar kök kutuya düşüp sarma yapıyordu.
                    showPad -> {
                        val ne = ke.nativeKeyEvent
                        if (ne.action != KeyEvent.ACTION_DOWN) return@onKeyEvent true
                        when (ne.keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT  -> padSecim = (padSecim - 1 + padAdet).mod(padAdet)
                            KeyEvent.KEYCODE_DPAD_RIGHT -> padSecim = (padSecim + 1).mod(padAdet)
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> padCalistir(padSecim)
                            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_BACK -> showPad = false
                            else -> return@onKeyEvent false
                        }
                        true
                    }
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
                    useController = false            // tüm kontrol bizde (buton-eşleme)
                    keepScreenOn = true
                    // Odağı ASLA almaz: aldığında Compose odak ağacından kopuyor,
                    // üstteki panele imleç gitmiyor ve tuşlar oynatmayı arkada
                    // başlatıyordu (Dean, 19 Eylül).
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Sarma göstergesi — sağ altta, ama kontrol çubuğu açikken onun ÜSTÜNDE:
        // ikisi de BottomEnd olunca sayaç toplam süre yazısının üzerine biniyordu
        // (Dean, 17 Eylül: "sayaç saatin üstünde duruyor").
        seekHint?.let {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(NmDim.SafeArea)
                    .padding(bottom = if (showControls) 92.dp else 0.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(NmDim.PillRadius)).background(NmColor.Scrim)
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                ) {
                    Text(
                        text = it,
                        fontWeight = FontWeight.Bold,
                        fontSize = NmType.Body,
                        color = NmColor.OnSurface,
                    )
                }
            }
        }

        // Kontrol overlay: dokunmatikte etkileşimli butonlar; D-pad'de görsel bilgi.
        // Alt bar (QuickPad) açıkken çizilmez: ikisi de ekranın altına oturuyor,
        // üst üste gelince süre çubuğu düğmelerin ardında kalıyordu.
        if (showControls && !scrubMode && !showPad) {
            ControlsOverlay(
                isPlaying = isPlaying,
                position = position,
                duration = duration,
                onPlayPause = { dispatch(RemoteAction.PLAY_PAUSE) },
                onSeekBack = { seekBy(-10_000) },
                onSeekFwd = { seekBy(10_000) },
                onOpenSettings = { showSettings = true },
                onScrub = { enterScrub() },
                onSeekToFraction = { seekToFraction(it) },
                nowLabel = simdikiEtiket,
                onPrevEpisode = prevEpIndex?.let { i -> { goToEpisode(i) } },
                onNextEpisode = nextEpIndex?.let { i -> { goToEpisode(i) } },
                // Dizide bölüm listesi tek tuş uzakta olsun: kontrol çubuğundaki
                // "Bölümler" aynı sezon/bölüm panelini oynatmayı kesmeden açar.
                onOpenList = if (episodes.isEmpty()) null else {
                    { ayarBolumler = true; showSettings = true; showControls = false }
                },
                introRange = if (introBas != null && introBit != null) introBas to introBit else null,
                creditsStart = jenerikBas,
            )
        }

        // Açılışı atla — yalnız açılış şarkısı çalarken, sağ altta.
        if (acilisAtlanabilir && introBit != null && !showControls) {
            SkipIntroCard(
                onSkip = {
                    exo.seekTo(introBit)
                    position = introBit
                    seekHint = "⏭ Açılış atlandı"
                    hintTick++
                },
            )
        }

        // Jenerikte (ya da bölüm bitince) geri sayımlı geçiş kartı.
        if (geriSayim != null && nextEpIndex != null) {
            NextEpisodeCard(
                label = episodeLabel(episodes[nextEpIndex], nextEpIndex),
                countdown = geriSayim,
                onPlay = { goToEpisode(nextEpIndex) },
            )
        } else if (sonrakiTeklif && nextEpIndex != null) {
            // İşaretsiz bölümde eski davranış: son 90 sn'de teklif, otomatik geçiş yok.
            NextEpisodeCard(
                label = episodeLabel(episodes[nextEpIndex], nextEpIndex),
                countdown = null,
                onPlay = { goToEpisode(nextEpIndex) },
            )
        }

        // Scrub / önizleme overlay'i (thumbnail = preview oynatıcı karesi).
        if (scrubMode) {
            previewExo?.let { ScrubOverlay(previewExo = it, scrubPos = scrubPos, duration = duration) }
        }

        if (showPad) {
            QuickPad(
                secili = padSecim,
                isPlaying = isPlaying,
                position = position,
                duration = duration,
                prevEpisodeLabel = prevEpIndex?.let { episodeLabel(episodes[it], it) },
                nextEpisodeLabel = nextEpIndex?.let { episodeLabel(episodes[it], it) },
                hasEpisodes = episodes.isNotEmpty(),
                onSeekBy = { seekBy(it) },
                onPlayPause = { dispatch(RemoteAction.PLAY_PAUSE) },
                onPrevEpisode = { prevEpIndex?.let { goToEpisode(it) }; showPad = false },
                onNextEpisode = { nextEpIndex?.let { goToEpisode(it) }; showPad = false },
                onOpenEpisodes = { showPad = false; ayarBolumler = true; showSettings = true },
                onOpenSeek = { showPad = false; showSeek = true },
                onOpenSettings = { showPad = false; showSettings = true },
                onHome = { showPad = false; onHome() },
                onClose = { showPad = false },
            )
        }

        if (showSeek) {
            SeekScreen(
                position = position,
                duration = duration,
                prevEpisodeLabel = prevEpIndex?.let { episodeLabel(episodes[it], it) },
                nextEpisodeLabel = nextEpIndex?.let { episodeLabel(episodes[it], it) },
                onSeekTo = { target -> exo.seekTo(target); position = target },
                onPrevEpisode = { prevEpIndex?.let { goToEpisode(it) } },
                onNextEpisode = { nextEpIndex?.let { goToEpisode(it) } },
                onOpenEpisodes = if (episodes.isEmpty()) null else {
                    { ayarBolumler = true; showSettings = true }
                },
                canliYayin = canliYayin,
                onTamponBasina = { tamponBasina() },
                onCanliyaDon = { canliyaDon() },
                onClose = { showSeek = false },
            )
        }

        if (showStartPanel && panelAsList && episodes.isNotEmpty()) {
            BolumSecici(
                title = item.title.orEmpty(),
                poster = item.poster,
                aciklama = details?.description,
                episodes = episodes,
                currentEpIndex = currentEpIndex,
                secilenSezon = secilenSezon,
                onSezon = { secilenSezon = it },
                onSelect = { idx ->
                    if (resumeEpisode != null && resumeEpisode != idx) resumeLabel = null
                    currentEpIndex = idx
                    playRequested = true
                    exo.playWhenReady = true
                },
                onClose = { showStartPanel = false; panelAsList = false },
            )
        } else if (showStartPanel) {
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
                    // Bölüm seçildi: kayıt başka bölüme aitse "devam et" etiketi
                    // artık yanıltıcı, düşer.
                    if (resumeEpisode != null && resumeEpisode != idx) resumeLabel = null
                    currentEpIndex = idx
                    playRequested = true
                    showStartPanel = false
                    panelAsList = false
                    exo.playWhenReady = true
                },
                onSelectLink = { idx -> currentLinkIndex = idx },
                onPlay = {
                    // "Devam et" kayıtlı bölümü kastediyor: farklı bölümdeysek önce
                    // ona geçilir, devam etme o zaman uygulanır.
                    val kayit = resumeEpisode
                    if (resumeLabel != null && kayit != null && kayit != currentEpIndex &&
                        kayit in episodes.indices
                    ) {
                        currentEpIndex = kayit
                    }
                    playRequested = true
                    showStartPanel = false
                    panelAsList = false
                    exo.playWhenReady = true
                },
                onOpenSettings = { showStartPanel = false; panelGeriGelsin = true; showSettings = true },
                onOpenEpisodes = { panelAsList = true; secilenSezon = null },
            )
        }

        if (showSettings) {
            SettingsPanel(
                links = links,
                currentLinkIndex = currentLinkIndex,
                episodes = episodes,
                acilisBolumler = ayarBolumler,
                listeKaynagi = listeKaynagi,
                currentEpIndex = currentEpIndex,
                tracks = tracks,
                speed = speed,
                panelFocus = panelFocus,
                library = library,
                item = item,
                onSelectSource = { idx ->
                    currentLinkIndex = idx
                    showSettings = false
                    if (panelGeriGelsin) { panelGeriGelsin = false; showStartPanel = true }
                },
                onOpenEpisodes = {
                    showSettings = false
                    panelGeriGelsin = false
                    panelAsList = true
                    secilenSezon = null
                    showStartPanel = true
                },
                onSelectEpisode = { idx ->
                    showSettings = false
                    panelGeriGelsin = false
                    goToEpisode(idx)
                },
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
                onOpenSeek = { showSettings = false; panelGeriGelsin = false; showSeek = true },
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
                onHariciOynat = {
                    val link = links.getOrNull(currentLinkIndex)
                    if (link == null) {
                        PlaybackLog.warn("harici", "kaynak yok — devredilemedi")
                    } else {
                        // Kaldığın yer önce SUNUCUYA yazılır: harici oynatıcıdan
                        // dönünce Devam Et doğru dakikayı göstersin.
                        library.saveProgress(
                            item = item,
                            positionSeconds = position / 1000.0,
                            durationSeconds = duration / 1000.0,
                            episodeRef = episodes.getOrNull(currentEpIndex)
                                ?.let { "S${it.season}B${it.episode ?: (currentEpIndex + 1)}" }
                                .orEmpty(),
                            isSerie = episodes.isNotEmpty(),
                        )
                        val niyet = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            setDataAndType(android.net.Uri.parse(link.url), "video/*")
                            putExtra("title", item.title.orEmpty())
                            // VLC ve MX kaldığın yeri bu ekstradan okur (ms).
                            putExtra("position", position)
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching {
                            context.startActivity(
                                android.content.Intent.createChooser(niyet, "Oynatıcı seç"),
                            )
                        }.onFailure {
                            PlaybackLog.warn("harici", "oynatıcı açılamadı: ${it.message ?: "-"}")
                        }
                    }
                    showSettings = false
                },
                showReport = showReport,
                onToggleReport = { showReport = !showReport },
                showKeys = showKeys,
                onToggleKeys = {
                    showKeys = !showKeys
                    keyPrefs.edit().putBoolean("show_keys", showKeys).apply()
                },
                onClose = {
                    showSettings = false
                    if (panelGeriGelsin) { panelGeriGelsin = false; showStartPanel = true }
                },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        // Hata kutusu yok: kullanıcı ekranda kalır, ne olduğunu okur, çıkmak
        // isterse GERİ tuşuna kendisi basar. "Tekrar dene" düğmesi gerekmiyor —
        // sıradaki kaynağa geçiş kendiliğinden yapılıyor.
        // Kaynak bulunamadıysa dönen halka yanlış bilgi verir: arama BİTTİ, dönecek
        // bir şey yok. O durumda halka yerine ✕.
        when {
            !ready && !showSettings -> CornerStatus(status ?: "Yükleniyor…", loader = status != KAYNAK_YOK && status != BOLUM_YOK)
            status != null && !showSettings -> CornerStatus(status!!, loader = status != KAYNAK_YOK && status != BOLUM_YOK)
        }

        // Tuş göstergesi EN ÜSTTE çizilir: paneller açıkken de görünsün, çünkü
        // asıl merak edilen "bu tuş bir şey yapıyor mu" sorusu orada da geçerli.
        keyHint?.let { KeyHintChip(it) }
    }
}

// Hızlı pad'i AÇMAYACAK tuşlar: sistemin kendi işleri ve zaten bir işi olanlar.
private val PAD_DISI = setOf(
    KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
    KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_TV_POWER, KeyEvent.KEYCODE_SLEEP,
    KeyEvent.KEYCODE_WAKEUP, KeyEvent.KEYCODE_SOFT_SLEEP,
    KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_APP_SWITCH,
    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU,
    KeyEvent.KEYCODE_UNKNOWN,
)

/** Başka bir işe bağlı OLMAYAN her tuş hızlı pad'i açar (kumandadaki Netflix/Prime gibi). */
private fun padAcarMi(code: Int): Boolean =
    RemoteKey.from(code) == null && code !in MEDIA_KEYS && code !in PAD_DISI

// ALT BAR: oynat, sarma, bölüm geçme ve panel girişleri tek şeritte, küçük
// ikonlarla. Önce sağ altta dikey bir kutuydu; görüntünün köşesini kapatıyordu ve
// oynatıcının kendi alt çubuğuyla iki ayrı "kontrol yeri" oluyordu. Tek basış
// mantığı aynı: D-pad düğmeler arasında gezer, OK uygular, GERİ kapatır.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QuickPad(
    /** Seçili düğme indeksi — odak DEĞİL: TV'de odak şeride yerleşmeyince
     *  tuşlar kök kutuya düşüp sarma yapıyordu. Sıra `padCalistir` ile aynı. */
    secili: Int,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    prevEpisodeLabel: String?,
    nextEpisodeLabel: String?,
    hasEpisodes: Boolean,
    onSeekBy: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onPrevEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onOpenEpisodes: () -> Unit,
    onOpenSeek: () -> Unit,
    onOpenSettings: () -> Unit,
    onHome: () -> Unit,
    onClose: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(nmPlayerScrim)
                .padding(horizontal = NmDim.SafeH, vertical = NmDim.SafeV),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        // Alt bar açıkken kontrol overlay'i çizilmiyor: süre ve ilerleme burada
        // olmazsa nereye sarıldığı görünmez kalır.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = fmtTime(position) + "  ·  −" + fmtTime((duration - position).coerceAtLeast(0)),
                color = NmColor.OnSurfaceMuted,
                fontSize = NmType.Caption,
            )
            Text(fmtTime(duration), color = NmColor.OnSurfaceMuted, fontSize = NmType.Caption)
        }
        Box(
            Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(NmColor.TrackIdle),
        ) {
            val oran = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
            Box(
                Modifier.fillMaxWidth(oran).height(4.dp).clip(RoundedCornerShape(2.dp))
                    .background(NmColor.Primary),
            )
        }
        // Şerit 11 düğme taşıyor; sabit genişlikte hepsi 640dp'lik TV ekranına
        // SIĞMIYORDU: taşanlar hiç çizilmiyor, odak oraya gidince düğme görünmeden
        // seçili oluyordu (Dean, 18 Eylül: "ileri sarma tuşuna geçemiyoruz").
        // Yatay kaydırma + dar düğme: odak sağa gidince şerit kendiliğinden kayar.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Bölüm geçme uçlarda: sarma tuşlarıyla karışmasın, en dış konum
            // kumandada tek hamlede yakalanır.
            PadBtn(Icons.Filled.SkipPrevious, "Önceki", secili == 0, enabled = prevEpisodeLabel != null) { onPrevEpisode() }
            PadBtn(Icons.Filled.FastRewind, "−5 dk", secili == 1) { onSeekBy(-300_000) }
            PadBtn(Icons.Filled.Replay30, "−30 sn", secili == 2) { onSeekBy(-30_000) }
            PadBtn(
                icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                label = if (isPlaying) "Duraklat" else "Oynat",
                secili = secili == 3,
                accent = true,
            ) { onPlayPause() }
            PadBtn(Icons.Filled.Forward30, "+30 sn", secili == 4) { onSeekBy(30_000) }
            PadBtn(Icons.Filled.FastForward, "+5 dk", secili == 5) { onSeekBy(300_000) }
            PadBtn(Icons.Filled.SkipNext, "Sonraki", secili == 6, enabled = nextEpisodeLabel != null) { onNextEpisode() }

            PadBtn(Icons.Filled.FormatListBulleted, "Bölümler", secili == 7, enabled = hasEpisodes) { onOpenEpisodes() }
            PadBtn(Icons.Filled.Dialpad, "Dakika", secili == 8) { onOpenSeek() }
            PadBtn(Icons.Filled.Settings, "Ayarlar", secili == 9) { onOpenSettings() }
            // Sistemin HOME tuşu uygulamaya gelmiyor; "ana sayfa" burada bir düğme.
            PadBtn(Icons.Filled.Home, "Ana sayfa", secili == 10) { onHome() }
            PadBtn(Icons.Filled.Close, "Kapat", secili == 11) { onClose() }
        }
        }
    }
}

/**
 * Alt bar düğmesi: küçük ikon + altında adı. `enabled=false` → soluk ve odak
 * almaz; düğme kaldırılmıyor ki şeridin düzeni bölümden bölüme kaymasın
 * (kumandayla öğrenilen "üçüncü tuş oynat" bilgisi bozulur).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PadBtn(
    icon: ImageVector,
    label: String,
    secili: Boolean = false,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    // Vurgu seçimden gelir; dokunmatik için tıklama hâlâ çalışır.
    val odakli = secili
    val shape = RoundedCornerShape(NmDim.RowRadius)
    Column(
        modifier = modifier.width(54.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier
                .size(if (accent) 46.dp else 40.dp)
                .clip(shape)
                .background(
                    when {
                        odakli -> NmColor.Primary
                        accent -> NmColor.PrimarySelected
                        else   -> NmColor.ScrimSoft
                    }
                )
                .nmFocusRing(odakli, shape)
                .clickable(enabled = enabled) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(if (accent) 26.dp else 22.dp),
                colorFilter = ColorFilter.tint(
                    when {
                        odakli  -> NmColor.OnPrimary
                        enabled -> NmColor.OnSurface
                        else    -> NmColor.OnSurfaceFaint
                    }
                ),
            )
        }
        Text(
            text = label,
            fontSize = NmType.Caption,
            fontWeight = if (odakli) FontWeight.Bold else FontWeight.Medium,
            color = when {
                odakli  -> NmColor.Primary
                enabled -> NmColor.OnSurfaceMuted
                else    -> NmColor.OnSurfaceFaint
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// Basılan tuşun ekranda görünen karşılığı: "MEDIA_FAST_FORWARD (90) → +30 sn".
// Kod numarası da yazar — kumandanın ürettiği tuş bilinmeyen bir şeyse eşleme
// ekranında aranacak değer budur.
private fun keyLabel(code: Int, bindings: KeyBindings): String {
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
        padAcarMi(code)                             -> "hızlı pad (aç/kapa)"
        else                                        -> "bağlı değil"
    }
    return "$ad ($code) → $karsilik"
}

// Sol üstte küçük şerit. TV güvenli alanı içinde, video akışını kapatmayacak kadar dar.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun KeyHintChip(text: String) {
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

        // Sağ üst: mod butonları (önizleme / ayarlar) — TV güvenli alan içinde.
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(horizontal = NmDim.SafeH, vertical = NmDim.SafeV),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            onOpenList?.let { TextPill("Bölümler", it) }
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
            // Cubuktaki uc ikon odak ALMAZ (D-pad sol/sag sarmadir). Gezilebilir
            // buton takimi QuickPad'de ve ASAGI ok ile aciliyor — yazmayinca
            // bulunmuyordu (Dean, 17 Eylul: "sarma butonu playerda olacak, cursor
            // gezebilir olsun").
            Text(
                text = "▼  Butonlar  ·  ◀ ▶ 10 sn sar  (basılı tut: hızlı)",
                color = NmColor.OnSurfaceMuted,
                fontSize = NmType.Caption,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
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
    // Düğmeler ODAK ALIR. Eskiden almıyordu (D-pad sol/sağ yalnız sarmaydı) ve
    // gezilebilir takım ayrı bir şeritteydi — Dean, 19 Eylül: "d-pad gezmiyor,
    // player üstünde odak yok". Kontroller açıkken AŞAĞI ok odağı buraya indirir.
    var odakli by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(box)
            .clip(CircleShape)
            .background(
                when {
                    odakli -> NmColor.OnSurface
                    accent -> NmColor.Primary
                    else   -> NmColor.ScrimSoft
                }
            )
            .onFocusChanged { odakli = it.isFocused }
            .focusable()
            .clickable { onTap() }
            .pointerInput(Unit) { detectTapGestures { onTap() } },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            icon,
            contentDescription = null,
            modifier = Modifier.size(ic),
            colorFilter = ColorFilter.tint(if (odakli) NmColor.Primary else NmColor.OnPrimary),
        )
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

// Kaynak aramasının SONUÇSUZ bittiğini söyleyen tek mesaj. Sabit olmasının sebebi
// görünüm: bu durumda dönen halka değil ✕ gösterilir ve ekran kendiliğinden kapanır.
private const val KAYNAK_YOK = "Çalışan kaynak bulunamadı — kapanıyor…"

// Mesaj okunacak kadar durur, sonra içerikten çıkılır. Kullanıcıyı boş ekranda
// GERİ'ye basmayı beklemek anlamsız: yapacak bir şey yok (Dean: "geri kendi atsın,
// bulamadığında bekletme").
private const val KAYNAK_YOK_CIKIS_MS = 2500L

// Bir bölüm/film için "gerçek içerik" sayılacak en kısa süre. Sağlayıcılar
// kaldırılmış bölümün yerine on-yirmi saniyelik tutundurma/uyarı klibi koyabiliyor
// (ekranda "İÇERİK KALDIRILDI", "DUR! GİTME!" gibi metinler — bunlar bu koddan
// GELMEZ, kaynağın kendi videosudur). 90 sn eşiği en kısa gerçek bölüm/fragmandan
// bile kısa tutundurma klipleri ayırt etmeye yeter; gerçek içerik bundan kısa
// olmaz. Aynı büyüklükte olması tesadüf: NEXT_EPISODE_WINDOW_MS ayrı bir amaca
// (bitiş penceresi) hizmet eder, kasıtlı olarak burada tekrar tanımlanır.
private const val MIN_GECERLI_SURE_MS = 90_000L

// Kuyruktaki hiçbir kaynak 90 sn eşiğini geçemedi: bölüm sağlayıcıda gerçekten yok.
private const val BOLUM_YOK = "Bu bölüm sağlayıcıda yok"

// Pad şeridindeki düğme sayısı — `padCalistir` ve QuickPad çizimi ile AYNI olmalı.
private const val padAdet = 12

// Jenerik işareti BULUNAMAYAN bölümde teklif penceresi: bitmeye bu kadar kala.
// 90 sn erken çıkıyordu — kart hâlâ sahnenin ortasındayken beliriyor, jenerik
// ancak ~20 sn sonra başlıyordu (Dean, 18 Eylül, iki fotoğraf: kart 90 sn kala,
// jenerik 70 sn kala). Sessizlik/konuşma temelli tespit denenmedi: dizi ve filmde
// sahne içinde de uzun sessizlik oluyor, yanlış yerde tetiklerdi. Jenerik işareti
// varsa bu pencere zaten hiç kullanılmaz.
private const val NEXT_EPISODE_WINDOW_MS = 70_000L

// Jenerik başlayınca sonraki bölüme geçmeden önce beklenen süre. Son sahneyi
// kaçırmamak için var: GERİ basan kişi jeneriği sonuna kadar izler.
private const val NEXT_COUNTDOWN_SEC = 10

// Bölüm sonu kartı — sağ altta, oynatmayı kesmeden. Kumandada SAĞ ok kabul eder
// (tuş işleme oynatıcıda; kart odak almaz ki D-pad sarma/kontrol akışı bozulmasın),
// dokunmatikte karta dokunmak yeter.
// countdown != null → geri sayım sürüyor, süre dolunca kendiliğinden geçilir;
// GERİ sayımı durdurur. countdown == null → yalnız teklif, kendiliğinden geçiş yok.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NextEpisodeCard(label: String, countdown: Int?, onPlay: () -> Unit) {
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
private fun SkipIntroCard(onSkip: () -> Unit) {
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
    /** Kitaplık sekmesi listeleri buradan okur/yazar (ana ekrandaki menüyle aynı). */
    library: com.evaitec.netmovies.tv.data.Library,
    item: com.evaitec.netmovies.tv.data.MediaItem,
    onSelectSource: (Int) -> Unit,
    onOpenEpisodes: () -> Unit = {},
    /** Panel doğrudan Bölümler sekmesinde açılsın (kumandadaki "Bölümler" girişi). */
    acilisBolumler: Boolean = false,
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
    /** Akışı cihazdaki başka bir oynatıcıya (VLC, Nova, MX) devreder. */
    onHariciOynat: () -> Unit = {},
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

    // Kaynak listesi (11 satıra kadar çıkıyor) ana akıştan ayrı bir alt sayfaya
    // taşındı: ana panelde eskiden 11 satır geçmeden "Bölümler"e ulaşılamıyordu
    // (Dean: "çok yoğun"). Seçim özelliği aynen duruyor, yalnız yeri değişti.
    var kaynakListesiAcik by remember { mutableStateOf(false) }
    // Ayarlar tek uzun listeydi: favoriye ulasmak icin sonuna kadar inmek gerekiyordu.
    // Artik ustte ikon seridi var, icerik yalniz secili ikonunki (Dean, 17 Eylul:
    // "acilir secenek sadece ikon olsun, buton icinde gezinir seceriz").
    // Sekme sırası sabit: 0 Kitaplık, 1 Bölümler (dizide). "Bölümler" girişleri
    // paneli doğrudan orada açar — tam ekran liste izlenen sahneyi kapatıyordu.
    var sekme by remember { mutableStateOf(if (acilisBolumler && episodes.isNotEmpty()) 1 else 0) }
    // Bölüm/sezon listesi panelin İÇİNDE: tam ekran modal koca bir liste açıp
    // izlenen sahneyi kapatıyordu (Dean, 17 Eylül: "o da koca ekranda olmasın").
    var panelSezon by remember { mutableStateOf<Int?>(null) }
    val kaynakListFocus = remember { FocusRequester() }
    val kaynakOzet = links.getOrNull(currentLinkIndex)?.let { languageLabel(it) } ?: "—"

    // GERİ tuşu: alt sayfa açıkken önce onu kapatır — yığın en son kaydolanı
    // (burayı) önce görür, ana panelin kendi GERİ işleyicisine hiç düşmez.
    NmBackHandler(enabled = kaynakListesiAcik) { kaynakListesiAcik = false }

    // Odak nöbeti: alt sayfa açılıp kapanınca odak doğru gruba taşınmalı, aksi
    // hâlde kumanda önceki karede kalan (artık görünmeyen) satırda takılı kalır.
    LaunchedEffect(kaynakListesiAcik) {
        val hedef = if (kaynakListesiAcik) kaynakListFocus else panelFocus
        repeat(6) {
            withFrameNanos {}
            if (runCatching { hedef.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

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
        if (kaynakListesiAcik) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(kaynakListFocus)
                    .focusGroup()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(NmDim.ItemGap),
            ) {
                // Sıra kuralı sabit: Türkçe dublaj → Türkçe altyazı → dil bilinmiyor.
                // Etiket her satırda yazar, hangi dilin oynadığı tahmine bırakılmaz.
                SectionTitle("📺 Sağlayıcı & Kaynak")
                if (links.isEmpty()) MutedRow("—")
                links.forEachIndexed { idx, link ->
                    SettingRow(languageLabel(link), idx == currentLinkIndex) { onSelectSource(idx) }
                }
                androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                SettingRow("◀ Ayarlara dön", false) { kaynakListesiAcik = false }
            }
        } else {
            val sekmeler = buildList {
                add("⭐" to "Kitaplık")
                if (episodes.isNotEmpty()) add("📑" to "Bölümler")
                add("📺" to "Kaynak")
                add("🔊" to "Ses & Altyazı")
                add("⚡" to "Hız & Kalite")
                add("🛠" to "Araçlar")
            }
            val secili = sekmeler.getOrNull(sekme) ?: sekmeler.first()

            Column(verticalArrangement = Arrangement.spacedBy(NmDim.ItemGap)) {
                // İkon şeridi. Ad yalnız seçili olanın altında yazar — altı etiket
                // yan yana dar panele sığmıyor, ikon tanınıyor.
                Row(
                    modifier = Modifier.fillMaxWidth().focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    sekmeler.forEachIndexed { i, (ikon, _) ->
                        IkonSekme(ikon, i == sekme, Modifier.weight(1f)) { sekme = i }
                    }
                }
                SectionTitle(
                    secili.first + "  " + secili.second +
                        if (secili.second == "Bölümler" && listeKaynagi != null) "  ·  $listeKaynagi" else "",
                )

                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(NmDim.ItemGap),
                ) {
                    when (secili.second) {
                        "Kitaplık" -> {
                            val L = com.evaitec.netmovies.tv.data.Library
                            SettingRow(
                                if (library.inIzlenecek(item)) "☆ İzleneceklerde ✓ — çıkar"
                                else "☆ İzleneceklere ekle",
                                library.inIzlenecek(item),
                            ) { library.toggleListe(item, L.LISTE_IZLENECEK) }
                            SettingRow(
                                if (library.inTakip(item)) "📋 Takipte ✓ — bırak" else "📋 Takip et",
                                library.inTakip(item),
                            ) { library.toggleListe(item, L.LISTE_TAKIP) }
                            SettingRow(
                                if (library.isFavorite(item)) "★ Beğendiklerimde ✓ — çıkar"
                                else "★ Beğendiklerime ekle",
                                library.isFavorite(item),
                            ) { library.toggleFavorite(item) }
                        }

                        "Bölümler" -> {
                            val sezonlar = remember(episodes) {
                                episodes.map { it.season }.distinct().sorted()
                            }
                            val simdikiSezon = episodes.getOrNull(currentEpIndex)?.season
                            val acikSezon = panelSezon ?: simdikiSezon ?: sezonlar.firstOrNull()

                            // Tek sezonluk dizide sezon satırı fazlalık; çok sezonluda
                            // sezonlar tek satıra sığan kısa bir şerit olur.
                            if (sezonlar.size > 1) {
                                sezonlar.forEach { sz ->
                                    SettingRow("Sezon " + sz, sz == acikSezon) { panelSezon = sz }
                                }
                            }

                            val liste = episodes.withIndex().filter { it.value.season == acikSezon }
                            if (liste.isEmpty()) MutedRow("Bu sezonda bölüm yok")
                            liste.forEach { (idx, ep) ->
                                SettingRow(episodeLabel(ep, idx), idx == currentEpIndex) {
                                    onSelectEpisode(idx)
                                }
                            }

                            // Tam ekran liste hâlâ duruyor: uzun dizide poster/özet
                            // görmek isteyen oraya geçer.
                            SettingRow("⛶ Tam ekran bölüm listesi", false) { onOpenEpisodes() }
                        }

                        // Kaynak listesinin tamamı ayrı sayfada: burada yalnız hangisi
                        // oynuyor yazar.
                        "Kaynak" -> SettingRow(kaynakOzet + " — kaynak değiştir", false) {
                            kaynakListesiAcik = true
                        }

                        "Ses & Altyazı" -> {
                            if (audioGroups.isEmpty() && textGroups.isEmpty()) {
                                MutedRow("Bu kaynakta seçenek yok")
                            }
                            audioGroups.forEach { group ->
                                for (i in 0 until group.length) {
                                    val fmt = group.getTrackFormat(i)
                                    SettingRow(
                                        fmt.label ?: fmt.language ?: ("Ses " + (i + 1)),
                                        group.isTrackSelected(i),
                                    ) { onSelectAudio(group, i) }
                                }
                            }
                            if (textGroups.isNotEmpty()) {
                                SettingRow("Altyazı kapalı", textDisabled) { onSelectSubtitle(null, 0) }
                                textGroups.forEach { group ->
                                    for (i in 0 until group.length) {
                                        val fmt = group.getTrackFormat(i)
                                        SettingRow(
                                            fmt.label ?: fmt.language ?: ("Altyazı " + (i + 1)),
                                            group.isTrackSelected(i),
                                        ) { onSelectSubtitle(group, i) }
                                    }
                                }
                            }
                        }

                        "Hız & Kalite" -> {
                            SPEEDS.forEach { h ->
                                SettingRow(if (h == 1.0f) "Normal hız" else (h.toString() + "x"), h == speed) {
                                    onSelectSpeed(h)
                                }
                            }
                            if (videoTrackCount > 0) {
                                SettingRow("Kalite: otomatik", qualityAuto) { onSelectQuality(null, 0) }
                                videoGroups.forEach { group ->
                                    for (i in 0 until group.length) {
                                        val fmt = group.getTrackFormat(i)
                                        val etiket = when {
                                            fmt.height > 0 -> fmt.height.toString() + "p"
                                            fmt.bitrate > 0 -> (fmt.bitrate / 1000).toString() + " kbps"
                                            else -> "Kalite " + (i + 1)
                                        }
                                        SettingRow(etiket, !qualityAuto && group.isTrackSelected(i)) {
                                            onSelectQuality(group, i)
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            // Harici oynatıcı: akış cihazdaki VLC/Nova/MX'e devredilir.
                            // Orada bölüm geçişi, kaynak değiştirme ve kaldığın yerin
                            // kaydı YOK — uygulamadan çıkılıyor, bunu satır söylüyor.
                            SettingRow("📤 Harici oynatıcıda aç — VLC · Nova · MX", false) {
                                onHariciOynat()
                            }
                            SettingRow("Sarma · dakikaya git · bölüm", false) { onOpenSeek() }
                            SettingRow(
                                if (showKeys) "Tuş göstergesi açık" else "Tuş göstergesi kapalı",
                                showKeys,
                                onToggleKeys,
                            )
                            SettingRow(
                                if (showReport) "Kaynak raporu ▾" else "Kaynak raporu ▸",
                                showReport,
                                onToggleReport,
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

                androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                SettingRow("✕ Kapat", false, onClose)
            }
        }
    }
}

/** Ayar şeridindeki tek ikon. Metin yok: altı ad yan yana dar panele sığmıyor. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun IkonSekme(ikon: String, secili: Boolean, modifier: Modifier = Modifier, onSec: () -> Unit) {
    var odakli by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(NmDim.RowRadius)
    Box(
        modifier = modifier
            .height(44.dp)
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
                if (it.isFocused) onSec()
            }
            .focusable()
            .clickable { onSec() },
        contentAlignment = Alignment.Center,
    ) {
        Text(ikon, fontSize = NmType.Body)
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
    onOpenEpisodes: () -> Unit,
) {
    val bilgi = listOfNotNull(
        details?.yearText?.takeIf { it.isNotBlank() },
        details?.tagsText?.takeIf { it.isNotBlank() },
        (details?.ratingText?.takeIf { it.isNotBlank() } ?: rating?.let { "%.1f".format(it) })?.let { "★ $it" },
    ).joinToString("  ·  ")
    val playFocus = remember { FocusRequester() }
    // Bölüm listesi olarak açıldığında odak OYNAT'ta değil, OYNAYAN BÖLÜMDE olmalı:
    // aksi hâlde listeye inmek ve o bölümü bulmak kaydırmakla geçiyordu
    // (Dean: "direkt bölümlere girmiyor").
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
    LaunchedEffect(panelOdakli, episodes.size) {
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
            // Tam perde yerine yumuşak gölge: panel yandayken arkası seçilsin.
            .background(NmColor.ScrimSoft)
            .onFocusChanged { panelOdakli = it.hasFocus }
            .focusGroup(),
        // Ortadaki geniş panel ekranı kapatıp "dolu" gösteriyordu (Dean): panel
        // sağ kenara alındı, arkadaki afiş/video görünür kalıyor.
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.92f)
                .width(NmDim.PanelWidth)
                .clip(RoundedCornerShape(NmDim.PanelRadius))
                .background(NmColor.SurfaceDialog)
                // Açıklama gelince bölüm listesine yer kalmıyordu: panelin iç
                // boşlukları ve satır araları yarıya indirildi (Dean).
                .padding(horizontal = 22.dp, vertical = NmDim.SafeV / 2),
            verticalArrangement = Arrangement.spacedBy(NmDim.ItemGap / 2),
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
                // Sezon rafı (yatay) + bölüm listesi (dikey) BURADAYDI: aynı panelde
                // iki ayrı yön, üstüne panel zaten oynatıcının üstünde bir katmandı
                // (Dean: "çok karışık, iç içe hep geçiyor"). Bölüm seçimi artık ayrı
                // bir sayfa — `BolumSecici`, kutucuk ızgarası, sezon → bölüm.
                SettingRow("📑  Bölümler (${episodes.size}) — sezon ve bölüm seç", false) {
                    onOpenEpisodes()
                }
                val sonIdx = episodes.lastIndex
                if (episodes.size > 1) {
                    SettingRow(
                        "⏭  Son bölüm — ${episodeLabel(episodes[sonIdx], sonIdx)}",
                        sonIdx == currentEpIndex,
                    ) { onSelect(sonIdx) }
                }
                Spacer(Modifier.weight(1f))
            } else {
                // Bölüm listesi `load_item` ile geliyor ve saniyeler sürebiliyor.
                // O ana kadar panelde yalnız "Devam et" duruyordu: kullanıcı bölüm
                // satırlarının GELECEĞİNİ bilmeden kayıttaki bölümü açıyordu
                // (Dean, 18 Eylül: "beklemesem göremeyeceğim bölümler yazısını").
                MutedRow("📑  Bölümler yükleniyor…")
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
        modifier = Modifier.padding(top = 6.dp, bottom = 1.dp),
    )
}

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
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = (if (selected) "●  " else "     ") + label,
            fontSize = NmType.Label,
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
private fun CornerStatus(message: String, loader: Boolean) {
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
