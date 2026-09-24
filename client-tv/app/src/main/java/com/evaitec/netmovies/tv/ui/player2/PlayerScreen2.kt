package com.evaitec.netmovies.tv.ui.player2

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.evaitec.netmovies.tv.data.Library
import com.evaitec.netmovies.tv.data.MediaItem
import com.evaitec.netmovies.tv.input.KeyBindings
import com.evaitec.netmovies.tv.ui.BOLUM_YOK
import com.evaitec.netmovies.tv.ui.BolumSecici
import com.evaitec.netmovies.tv.ui.ControlsOverlay
import com.evaitec.netmovies.tv.ui.CornerStatus
import com.evaitec.netmovies.tv.ui.KAYNAK_YOK
import com.evaitec.netmovies.tv.ui.KeyHintChip
import com.evaitec.netmovies.tv.ui.KaynakYokEkrani
import com.evaitec.netmovies.tv.ui.PlayerLoadingScreen
import com.evaitec.netmovies.tv.ui.NextEpisodeCard
import com.evaitec.netmovies.tv.ui.ScrubOverlay
import com.evaitec.netmovies.tv.ui.SeekScreen
import com.evaitec.netmovies.tv.ui.SettingsPanel
import com.evaitec.netmovies.tv.ui.SkipIntroCard
import com.evaitec.netmovies.tv.ui.StartPanel
import com.evaitec.netmovies.tv.ui.episodeLabel
import com.evaitec.netmovies.tv.ui.theme.NmColor
import com.evaitec.netmovies.tv.ui.theme.NmDim
import com.evaitec.netmovies.tv.ui.theme.NmType

/**
 * Oynatıcı 2 — ekran. Eski `PlayerScreen` ile AYNI imza: MainActivity anahtarla
 * birini seçer, eskisi yedek kalır. SAHİBİ: ui ajanı. Mevcut alt composable'lar
 * (ControlsOverlay, SettingsPanel, StartPanel, ScrubOverlay, …) `ui/PlayerScreen.kt`
 * içinde `internal` — yeniden yazma, çağır.
 */
@Composable
fun PlayerScreen2(
  item: MediaItem,
  bindings: KeyBindings,
  library: Library,
  onBack: () -> Unit,
  kanallar: List<MediaItem> = emptyList(),
  onKanal: (MediaItem) -> Unit = {},
) {
  val core = rememberPlayerCore(item, library, onExit = onBack)
  val ui = remember(item.url) { PlayerUiState() }
  val keys = rememberPlayerKeys(core, ui, bindings, kanallar, onKanal, onExit = onBack)
  val exo = core.exo

  // Core UI bayraklarını bilmez; eskide kendisi sıfırlıyordu — köprü burada.
  core.flashControls = { ui.flashControls() }
  core.uiKapat = { gecis -> ui.panelAsList = false; if (gecis) ui.showControls = false }

  val context = LocalContext.current
  val keyPrefs = remember {
    context.getSharedPreferences("netmovies_keymap", android.content.Context.MODE_PRIVATE)
  }
  // Tuş göstergesi tercihi oynatıcıdan çıkınca kaybolmasın (eski 255-258).
  remember(ui) { ui.showKeys = keyPrefs.getBoolean("show_keys", true) }
  val rootFocus = remember { FocusRequester() }
  val panelFocus = remember { FocusRequester() }
  val previewExo = rememberPreviewPlayer(core, ui)

  // Bölümler sekmesi açılınca en zengin liste aranır (core bir kez yapar).
  LaunchedEffect(ui.ayarBolumler) {
    if (ui.ayarBolumler) core.requestBestEpisodes()
  }
  // Ayarlar kapanınca bir sonraki açılış varsayılan sekmeden başlasın (eski 1273).
  LaunchedEffect(ui.showSettings) {
    if (!ui.showSettings) ui.ayarBolumler = false
  }

  // Odak sahipliği: kök kutu odaksızsa D-pad controller'a gelmez. Tek requestFocus
  // ilk karede sessizce başarısız olabiliyor → 10 kare dene (eski AA 1297-1304).
  LaunchedEffect(ui.showSettings, ui.showSeek, core.showStartPanel, ui.scrubMode, core.ready) {
    if (ui.showSeek || core.showStartPanel) return@LaunchedEffect   // odağı kendileri alır
    repeat(10) {
      val target = if (ui.showSettings) panelFocus else rootFocus
      if (runCatching { target.requestFocus() }.isSuccess) return@LaunchedEffect
      withFrameNanos { }
    }
  }

  val panelAcik = ui.showSettings || ui.showSeek || ui.scrubMode
  val nextEp = core.nextEpIndex
  val prevEp = core.prevEpIndex

  Box(
    Modifier
      .fillMaxSize()
      // Video zemini SAF SİYAH (21:9'da mor şerit görünüyordu).
      .background(Color.Black)
      .focusRequester(rootFocus)
      // GERİ ve yetim ACTION_UP kökten aşağı ilk yolda yakalanır (keys).
      .onPreviewKeyEvent { keys.onPreviewKey(it) }
      .onKeyEvent { keys.onKey(it) }
      .focusable()
      // Dokunmatik (telefon): videoya dokun → kontrolleri aç/kapat.
      .pointerInput(Unit) {
        detectTapGestures {
          if (!ui.showSettings && !ui.scrubMode) {
            if (ui.showControls) ui.showControls = false else ui.flashControls()
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
          // Odağı ASLA almaz: Compose odak ağacından kopuyordu (Dean, 19 Eylül).
          isFocusable = false
          isFocusableInTouchMode = false
          descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
      },
      modifier = Modifier.fillMaxSize(),
    )

    // Hiç oynamadan beklenirken tam ekran geçiş; bulunamadıysa tekrar dene ekranı.
    val acilisBekleniyor = !core.ready && core.position == 0L && !ui.showSettings
    when {
      acilisBekleniyor && core.status == KAYNAK_YOK && !core.showStartPanel ->
        KaynakYokEkrani(item.poster, item.title) { core.tekrarDene() }
      acilisBekleniyor -> PlayerLoadingScreen(item.poster, item.title, core.status)
    }

    // Sarma göstergesi — sağ alt; kontrol çubuğu açıkken onun üstünde.
    core.seekHint?.let {
      Box(
        Modifier
          .fillMaxSize()
          .padding(NmDim.SafeArea)
          .padding(bottom = if (ui.showControls) 92.dp else 0.dp),
        contentAlignment = Alignment.BottomEnd,
      ) {
        Box(
          Modifier.clip(RoundedCornerShape(NmDim.PillRadius)).background(NmColor.Scrim)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        ) {
          Text(text = it, fontWeight = FontWeight.Bold, fontSize = NmType.Body, color = NmColor.OnSurface)
        }
      }
    }

    if (ui.showControls && !ui.scrubMode) {
      ControlsOverlay(
        isPlaying = core.isPlaying,
        position = core.position,
        duration = core.duration,
        onPlayPause = { core.playPause() },
        onSeekBack = { core.seekBy(-10_000) },
        onSeekFwd = { core.seekBy(10_000) },
        onOpenSettings = { ui.showSettings = true },
        onScrub = {
          ui.scrubPos = exo.currentPosition
          ui.scrubMode = true
          ui.scrubTick++          // önizlemeyi mevcut konuma seek et
          ui.showControls = true
        },
        onSeekToFraction = { core.seekToFraction(it) },
        nowLabel = core.simdikiEtiket.orEmpty(),
        onPrevEpisode = prevEp?.let { i -> { core.goToEpisode(i) } },
        onNextEpisode = nextEp?.let { i -> { core.goToEpisode(i) } },
        onOpenList = if (core.episodes.isEmpty()) null else {
          { ui.ayarBolumler = true; ui.showSettings = true; ui.showControls = false }
        },
        introRange = core.introRangeMs,
        creditsStart = core.creditsStartMs,
      )
    }

    // Açılışı atla — yalnız açılış çalarken, panel yokken.
    if (core.acilisAtlanabilir && !panelAcik && !ui.showControls) {
      SkipIntroCard(onSkip = { core.skipIntro() })
    }

    // Jenerikte geri sayımlı geçiş; işaretsizde son 70 sn teklif (otomatik geçiş yok).
    if (nextEp != null) {
      val geriSayim = core.geriSayim
      if (geriSayim != null || (core.sonrakiTeklif && !panelAcik)) {
        NextEpisodeCard(
          label = episodeLabel(core.episodes[nextEp], nextEp),
          countdown = geriSayim,
          onPlay = { core.goToEpisode(nextEp) },
        )
      }
    }

    if (ui.scrubMode) {
      previewExo?.let { ScrubOverlay(previewExo = it, scrubPos = ui.scrubPos, duration = core.duration) }
    }

    if (ui.showSeek) {
      SeekScreen(
        position = core.position,
        duration = core.duration,
        prevEpisodeLabel = prevEp?.let { episodeLabel(core.episodes[it], it) },
        nextEpisodeLabel = nextEp?.let { episodeLabel(core.episodes[it], it) },
        onSeekTo = { core.seekTo(it) },
        onPrevEpisode = { prevEp?.let { core.goToEpisode(it) } },
        onNextEpisode = { nextEp?.let { core.goToEpisode(it) } },
        onOpenEpisodes = if (core.episodes.isEmpty()) null else {
          { ui.ayarBolumler = true; ui.showSettings = true }
        },
        canliYayin = core.canliYayin,
        onTamponBasina = { core.tamponBasina() },
        onCanliyaDon = { core.canliyaDon() },
        onClose = { ui.showSeek = false },
      )
    }

    // Bölüm seçimi (goToEpisode değil): kayıt başka bölüme aitse "devam et" düşer.
    fun bolumSec(idx: Int) {
      if (core.resumeEpisode != null && core.resumeEpisode != idx) core.resumeLabel = null
      core.currentEpIndex = idx
      core.playRequested = true
      exo.playWhenReady = true
    }

    if (core.showStartPanel && ui.panelAsList && core.episodes.isNotEmpty()) {
      BolumSecici(
        title = item.title.orEmpty(),
        poster = item.poster,
        aciklama = core.details?.description,
        episodes = core.episodes,
        currentEpIndex = core.currentEpIndex,
        secilenSezon = ui.secilenSezon,
        onSezon = { ui.secilenSezon = it },
        onSelect = { bolumSec(it) },
        onClose = { core.showStartPanel = false; ui.panelAsList = false },
      )
    } else if (core.showStartPanel) {
      StartPanel(
        title = item.title.orEmpty(),
        details = core.details,
        rating = item.rating,
        episodes = core.episodes,
        currentEpIndex = core.currentEpIndex,
        links = core.links,
        currentLinkIndex = core.currentLinkIndex,
        resumeLabel = core.resumeLabel,
        hazir = core.links.isNotEmpty(),
        // Bölüme basmak DOĞRUDAN başlatır (Dean).
        onSelect = { idx ->
          bolumSec(idx)
          core.showStartPanel = false
          ui.panelAsList = false
        },
        onSelectLink = { core.selectLink(it) },
        onPlay = { core.panelOynat() },
        onOpenSettings = { core.showStartPanel = false; ui.panelGeriGelsin = true; ui.showSettings = true },
        onOpenEpisodes = { ui.panelAsList = true; ui.secilenSezon = null },
      )
    }

    if (ui.showSettings) {
      fun ayarKapat() {
        ui.showSettings = false
        if (ui.panelGeriGelsin) { ui.panelGeriGelsin = false; core.showStartPanel = true }
      }
      SettingsPanel(
        links = core.links,
        currentLinkIndex = core.currentLinkIndex,
        episodes = core.episodes,
        acilisBolumler = ui.ayarBolumler,
        listeKaynagi = core.listeKaynagi,
        currentEpIndex = core.currentEpIndex,
        tracks = core.tracks,
        speed = core.speed,
        panelFocus = panelFocus,
        library = library,
        item = item,
        onSelectSource = { idx -> core.selectLink(idx); ayarKapat() },
        onOpenEpisodes = {
          ui.showSettings = false
          ui.panelGeriGelsin = false
          ui.panelAsList = true
          ui.secilenSezon = null
          core.showStartPanel = true
        },
        onSelectEpisode = { idx ->
          ui.showSettings = false
          ui.panelGeriGelsin = false
          core.goToEpisode(idx)
        },
        onSelectAudio = { group, i -> core.selectAudio(group, i) },
        onSelectSubtitle = { group, i -> core.selectSubtitle(group, i) },
        onOpenSeek = { ui.showSettings = false; ui.panelGeriGelsin = false; ui.showSeek = true },
        qualityAuto = core.qualityAuto,
        onSelectQuality = { group, i -> core.selectQuality(group, i) },
        onSelectSpeed = { core.selectSpeed(it) },
        showReport = ui.showReport,
        onToggleReport = { ui.showReport = !ui.showReport },
        showKeys = ui.showKeys,
        onToggleKeys = {
          ui.showKeys = !ui.showKeys
          keyPrefs.edit().putBoolean("show_keys", ui.showKeys).apply()
        },
        onClose = { ayarKapat() },
        modifier = Modifier.align(Alignment.CenterEnd),
      )
    }

    // Kaynak bulunamadıysa dönen halka yerine ✕ (arama bitti).
    val status = core.status
    when {
      acilisBekleniyor -> Unit
      !core.ready && !ui.showSettings ->
        CornerStatus(status ?: "Yükleniyor…", loader = status != KAYNAK_YOK && status != BOLUM_YOK)
      status != null && !ui.showSettings ->
        CornerStatus(status, loader = status != KAYNAK_YOK && status != BOLUM_YOK)
    }

    // Tuş göstergesi EN ÜSTTE: paneller açıkken de görünsün.
    ui.keyHint?.let { KeyHintChip(it) }
  }
}
