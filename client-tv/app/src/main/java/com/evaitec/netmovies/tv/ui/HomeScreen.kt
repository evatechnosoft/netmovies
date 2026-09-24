package com.evaitec.netmovies.tv.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import com.evaitec.netmovies.tv.input.NmBackHandler
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.evaitec.netmovies.tv.HomeState
import com.evaitec.netmovies.tv.HomeViewModel
import com.evaitec.netmovies.tv.BuildConfig
import com.evaitec.netmovies.tv.data.ServerResolver
import com.evaitec.netmovies.tv.UpdateUi
import com.evaitec.netmovies.tv.UpdateViewModel
import com.evaitec.netmovies.tv.data.Library
import com.evaitec.netmovies.tv.data.MediaItem
import com.evaitec.netmovies.tv.data.Network
import com.evaitec.netmovies.tv.data.encodedUrl
import com.evaitec.netmovies.tv.ui.theme.NmColor
import com.evaitec.netmovies.tv.ui.theme.NmDim
import com.evaitec.netmovies.tv.ui.theme.NmType
import com.evaitec.netmovies.tv.ui.theme.nmBottomScrim
import com.evaitec.netmovies.tv.ui.theme.nmFocusRing
import com.evaitec.netmovies.tv.ui.theme.nmFocusRingOnly
import com.evaitec.netmovies.tv.ui.theme.nmFocusScale
import com.evaitec.netmovies.tv.ui.theme.nmScale

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TvTopBarButton(
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = false,   // yazısız ikon butonu (⚙ / 📱): dar, kare-ye yakın
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale = nmFocusScale(isFocused, label = "topBarBtnScale")
    val shape = RoundedCornerShape(NmDim.PillRadius)

    Box(
        modifier = modifier
            .nmScale(scale)
            .clip(shape)
            .background(if (isFocused) NmColor.Primary else NmColor.SurfaceHigh)
            .nmFocusRing(isFocused, shape)
            // `combinedClickable` zaten odaklanabilir yapar; ayrıca `focusable()`
            // eklemek buton başına İKİ odak hedefi üretiyordu — odak boş hedefe
            // düşünce OK basışı hiçbir yere gitmiyordu (Ayarlar açılmıyordu).
            .onFocusChanged { isFocused = it.isFocused }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = if (compact) 12.dp else 20.dp, vertical = if (compact) 8.dp else 11.dp),
    ) {
        Text(
            text = label,
            color = if (isFocused) NmColor.OnPrimary else NmColor.OnSurface,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            fontSize = NmType.Label,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    // Ekranın yeri (kaydırma, raf ve poster odağı) — oynatıcıdan GERİ ile
    // dönüldüğünde aynı posterde kalınsın diye DIŞARIDA tutulur.
    position: HomePosition,
    onSelect: (MediaItem) -> Unit,
    // Uzun-bas menüsünden bölüm seçildi: TV'de o bölüm açılır, telefonda TV'ye
    // o bölümle gönderilir.
    onSelectEpisode: (MediaItem, Int) -> Unit,
    // Telefonda kart TV'ye komut gönderir: tek dokunuşta gitmesin, önce menü
    // açılsın (Dean: "çok hızlı TV'ye yolluyor, kaydırmak için basmamla birlikte").
    // Televizyonda dokunuş zaten içeriği açar, menü uzun basışta.
    menuOnTap: Boolean,
    onExit: () -> Unit,
    onOpenBrowse: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenKeyMap: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenFollowing: () -> Unit,
    onOpenAgenda: () -> Unit,
    onOpenChannels: () -> Unit,
    library: Library,
    onOpenRemote: () -> Unit = {},
    vm: HomeViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    when (val s = state) {
        is HomeState.Loading -> Center("Yükleniyor…")
        is HomeState.Error   -> {
            // İçerik yüklenemese bile Favoriler/İzlenenler doluysa onları göster.
            if (library.favorites.isEmpty() && library.watched.isEmpty()) {
                ErrorWithRetry(s.message, onRetry = vm::load)
            } else {
                CategoryRows(position, emptyList(), library, onSelect, onSelectEpisode, menuOnTap, onExit, onOpenBrowse, onOpenSearch, onOpenKeyMap, onOpenVault, onOpenAdmin, onOpenFollowing, onOpenAgenda, onOpenChannels, onOpenRemote)
            }
        }
        is HomeState.Ready   -> {
            if (s.items.isEmpty() && library.favorites.isEmpty() && library.watched.isEmpty()) {
                ErrorWithRetry("İçerik yok", onRetry = vm::load)
            } else {
                CategoryRows(position, s.items, library, onSelect, onSelectEpisode, menuOnTap, onExit, onOpenBrowse, onOpenSearch, onOpenKeyMap, onOpenVault, onOpenAdmin, onOpenFollowing, onOpenAgenda, onOpenChannels, onOpenRemote)
            }
        }
    }
}

// Bir rafın ana sayfada görünmesi için gereken en az poster sayısı.
private const val MIN_ROW_ITEMS = 4

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun CategoryRows(
    position: HomePosition,
    items: List<MediaItem>,
    library: Library,
    onSelect: (MediaItem) -> Unit,
    onSelectEpisode: (MediaItem, Int) -> Unit,
    menuOnTap: Boolean,
    onExit: () -> Unit,
    onOpenBrowse: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenKeyMap: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenFollowing: () -> Unit,
    onOpenAgenda: () -> Unit,
    onOpenChannels: () -> Unit,
    onOpenRemote: () -> Unit = {},
) {
    // Kategoriye göre grupla (web ana sayfadaki yatay raylar gibi). Sıra korunur.
    // Tek-iki posterlik raflar elenir: M3U grup adları ("Business", "Animation;Kids")
    // ekranı bir ton boş rafla dolduruyordu ve aşağı inmek işkenceydi.
    val groups = remember(items) {
        items.groupBy { it.category?.takeIf { c -> c.isNotBlank() } ?: "Yeni Çıkanlar" }
            .filterValues { it.size >= MIN_ROW_ITEMS }
    }
    // Kitaplık satırları en üstte (İzlenenler + Favoriler), sonra agregasyon kategorileri.
    // remember ŞART: bu liste 500+ öğe taşıyor ve her recomposition'da yeniden
    // kurulursa raflar arasında gezinmek takılıyor.
    // Tek "Favoriler" rafı vardı: her şey aynı torbaya giriyordu (Dean, 19 Eylül:
    // "favori listelerine dönüştür, izlenecekler devam edenler gibi anlamlı").
    // Devam edenler izleme kaydından KENDİLİĞİNDEN dolar; diğer üçü elle işaretlenir.
    val sections = remember(groups, library.watched, library.favorites, library.izlenecek, library.takip) {
        buildList {
            if (library.watched.isNotEmpty()) add("Devam edenler" to library.watched.toList())
            if (library.izlenecek.isNotEmpty()) add("İzlenecekler" to library.izlenecek.toList())
            if (library.takip.isNotEmpty()) add("Takip ettiklerim" to library.takip.toList())
            if (library.favorites.isNotEmpty()) add("Beğendiklerim" to library.favorites.toList())
            groups.forEach { add(it.key to it.value) }
        }
    }

    // Kumanda yoklaması BURADA DEĞİL: tek döngü MainActivity'de. Ekran başına
    // döngü kurulduğunda oynatıcı açıkken kumanda ölüyordu (bkz. data/RemoteBus.kt).

    // Poster uzun-bas menüsü.
    var menuItem by remember { mutableStateOf<MediaItem?>(null) }

    // Ayarlar menüsü durumu
    var showSettingsMenu by remember { mutableStateOf(false) }

    // Başlangıç odağı — yoksa D-pad'de hiçbir şey seçilemiyor. Hedef ilk poster
    // DEĞİL, son kalınan poster: oynatıcıdan dönüşte kullanıcı çıktığı içeriği
    // bulmalı. Liste kısaldıysa (katalog tazelendi) sona kırpılır.
    val firstFocus = remember { FocusRequester() }
    val listState = position.listState
    val targetRow = position.row.coerceIn(0, (sections.size - 1).coerceAtLeast(0))
    val targetCard = position.card.coerceAtLeast(0)
    val firstKey = sections.firstOrNull()?.first
    LaunchedEffect(firstKey, sections.size) {
        if (targetRow > 0) runCatching { listState.scrollToItem(targetRow + 1) }  // 0 = TopBar
        repeat(6) {
            if (runCatching { firstFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            withFrameNanos {}
        }
    }

    // GERİ tuşu: listede aşağıdayken uygulamadan ATMAZ — önce en üste döner.
    // TV alışkanlığı bu; kullanıcı rafların arasında gezerken yanlışlıkla çıkmasın.
    // En üstteyken ikinci GERİ çıkışa gider.
    val scope = rememberCoroutineScope()
    val atTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 } }
    // Modal (Ayarlar / poster menüsü) açıkken bu handler DEVRE DIŞI: GERİ tuşu
    // modalı kapatmalı, uygulamadan atmamalı. Modalın kendi handler'ı devralır.
    val modalOpen = showSettingsMenu || menuItem != null
    val context = androidx.compose.ui.platform.LocalContext.current
    NmBackHandler(enabled = !modalOpen) {
        if (atTop) {
            // Ana ekranda tek GERİ artık çıkmıyor; çıkış GERİ'yi BASILI TUTMAK
            // (MainActivity.dispatchKeyEvent) ya da HOME. Yanlışlıkla bir basış
            // uygulamayı kapatıyordu (Dean). Sessiz kalınca GERİ "bozuk" sanılıyor.
            android.widget.Toast.makeText(context, "Çıkmak için GERİ'yi basılı tut", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            position.toTop()   // odak isteyicisi ilk postere taşınsın
            scope.launch {
                // Odak ÖNCE en üste alınır: sırası ters olunca liste 0'a kayıyor,
                // odak hâlâ aşağıdaki rafta kaldığı için Compose hemen geri
                // kaydırıyordu — GERİ "başa dönmüyor, raflar arası geziyor"
                // gibi görünüyordu (Dean).
                runCatching { firstFocus.requestFocus() }
                listState.animateScrollToItem(0)
                repeat(4) {
                    withFrameNanos {}
                    if (runCatching { firstFocus.requestFocus() }.isSuccess) return@launch
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            // Modal açıkken arkadaki raflar odak aramasından çıkarılır. Yoksa D-pad
            // aşağı basınca odak menüden çıkıp posterlerde geziniyordu.
            modifier = Modifier.fillMaxSize().focusProperties { canFocus = !modalOpen },
            contentPadding = PaddingValues(top = NmDim.SafeV, bottom = NmDim.SafeV + 16.dp),
            verticalArrangement = Arrangement.spacedBy(NmDim.RowGap),
        ) {
            item {
                TopBar(
                    onOpenBrowse = onOpenBrowse,
                    onOpenSearch = onOpenSearch,
                    onOpenRemote = onOpenRemote,
                    onOpenChannels = onOpenChannels,
                    onOpenAgenda = onOpenAgenda,
                    onOpenFollowing = onOpenFollowing,
                    onOpenSettings = { showSettingsMenu = true },
                )
            }

            sections.forEachIndexed { sIndex, (title, list) ->
                // Başlık ve raf TEK öğe: ayrı öğelerken odak, henüz oluşturulmamış
                // alt raflara geçemiyor ve liste ortada takılıyordu (Dean: "gerilim
                // kalıyor ama oraya kadar inmiyor").
                item(key = "raf-$title") {
                    // Yatay kaydırma da geri verilir: odak uzaktaki bir karttaysa
                    // raf o karta kaydırılmazsa odak ekran dışında kalır.
                    val rowState = rememberLazyListState()
                    LaunchedEffect(list.size) {
                        if (sIndex == targetRow && targetCard > 0) {
                            runCatching { rowState.scrollToItem(targetCard.coerceAtMost(list.lastIndex)) }
                        }
                    }
                    Column {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Medium,
                            fontSize = NmType.RowTitle,
                            color = NmColor.OnSurfaceMuted,
                            modifier = Modifier.padding(start = NmDim.SafeH),
                        )
                        LazyRow(
                            modifier = Modifier.focusGroup(),
                            state = rowState,
                            contentPadding = PaddingValues(horizontal = NmDim.SafeH, vertical = NmDim.RowPadV),
                            horizontalArrangement = Arrangement.spacedBy(NmDim.CardGap),
                        ) {
                            // Anahtar: aynı içerik iki rafta olabildiği için indeksle eşsizleşir.
                            itemsIndexed(list, key = { index, it -> "${it.url}#$index" }) { index, item ->
                                val hedef = sIndex == targetRow &&
                                    index == targetCard.coerceAtMost(list.lastIndex)
                                val cardModifier = (if (hedef) Modifier.focusRequester(firstFocus) else Modifier)
                                    .onFocusChanged {
                                        if (it.isFocused) { position.row = sIndex; position.card = index }
                                    }
                                PosterCard(
                                    item = item,
                                    isFavorite = library.isFavorite(item),
                                    progress = library.progress[item.url] ?: 0f,
                                    onClick = { if (menuOnTap) menuItem = item else onSelect(item) },
                                    onLongPress = { menuItem = item },
                                    modifier = cardModifier,
                                )
                            }
                    }
                    }
                }
            }
        }

        menuItem?.let { item ->
            PosterMenu(
                item = item,
                library = library,
                onPlay = { menuItem = null; onSelect(item) },
                onPlayEpisode = { idx -> menuItem = null; onSelectEpisode(item, idx) },
                // Benzer seçimi aramadan bir katalog kartı döndürür: pad kapanmaz,
                // o içeriğe geçer — "benzerinin benzeri" zinciri tek ekranda gezilir.
                onOpenItem = { bulunan -> menuItem = bulunan },
                onClose = { menuItem = null },
            )
        }

        if (showSettingsMenu) {
            SettingsMenu(
                onOpenKeyMap = onOpenKeyMap,
                onOpenVault = onOpenVault,
                onOpenAdmin = onOpenAdmin,
                onOpenChannels = onOpenChannels,
                onClose = { showSettingsMenu = false }
            )
        }
    }
}

// Üst bar bir GEZİNME çubuğudur: marka (ana sayfa) + Canlı TV · Ajanda · Listem.
// Bu üçü Ayarlar menüsünün içine gömülüydü; en çok kullanılan ekranlar iki adım
// uzaktaydı (Dean: "üstünde gezebilelim, yanına diğerlerini koyalım").
// Arama tam genişlikte bir çubuktu ve bandın tamamını yiyordu — küçük bir büyüteç
// düğmesine indi, sekmelere yer açtı.
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun TopBar(
    onOpenBrowse: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenRemote: () -> Unit,
    onOpenChannels: () -> Unit,
    onOpenAgenda: () -> Unit,
    onOpenFollowing: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup()
            .padding(horizontal = NmDim.SafeH, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "NetMovies",
            fontWeight = FontWeight.ExtraBold,
            fontSize = NmType.Wordmark,
            color = NmColor.Primary,
            modifier = Modifier.padding(end = 4.dp),
        )
        // Büyüteç SOL BAŞTA: en sık kullanılan giriş, sağ uçta kaybolmasın
        // (Dean: "arama butonu ana ekran sol üstte olsun, sadece büyüteç").
        // Artık Gözat'ı değil kendi arama ekranını açıyor; Gözat'ın kendi
        // arama kutusu yerinde duruyor.
        // Hepsi yalnız İKON: metinli düğmeler dar ekranda satır sarıyor ve
        // "Aja/nda" gibi kırpılmış etiketler çıkıyordu (Dean: "üstte yazılar
        // kalmasın"). Gözat aramanın hemen yanında, başta.
        // İkonun adı yalnız odaktayken, ikonların sağında tek yerde yazılır: kalıcı
        // yazı yok (Dean'in isteği), ama "🗓 neydi?" diye basıp denemek de gerekmez.
        var odakAdi by remember { mutableStateOf<String?>(null) }
        fun ad(isim: String) = Modifier.onFocusChanged {
            if (it.isFocused) odakAdi = isim else if (odakAdi == isim) odakAdi = null
        }
        TvTopBarButton("🔎", onClick = onOpenSearch, compact = true, modifier = ad("Ara"))
        TvTopBarButton("▦", onClick = onOpenBrowse, compact = true, modifier = ad("Kaynaklar"))
        TvTopBarButton("🗓", onClick = onOpenAgenda, compact = true, modifier = ad("Ajanda"))
        TvTopBarButton("★", onClick = onOpenFollowing, compact = true, modifier = ad("Listem"))
        Text(
            text = odakAdi.orEmpty(),
            fontSize = NmType.Label,
            color = NmColor.OnSurfaceMuted,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        TvTopBarButton("📱", onClick = onOpenRemote, compact = true, modifier = ad("Telefon kumandası"))
        TvTopBarButton("⚙", onClick = onOpenSettings, compact = true, modifier = ad("Ayarlar"))
    }
}

// 📱: tek dokunuş, uygulama içinde RemoteScreen (tarayıcıya atmaz).

// OK bu süre basılı kalırsa posterde joystick açılır (kumanda tekrar yollamasa da).
private const val UZUN_BASIS_MS = 500L

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun PosterCard(
    item: MediaItem,
    isFavorite: Boolean,
    progress: Float = 0f,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // D-pad focus → "büyüteç": kart büyür, beyaz odak halkası, üstte kalır.
    // combinedClickable → OK tek bas = oynat, OK basılı tut = menü (favori vb.).
    var focused by remember { mutableStateOf(false) }
    // `combinedClickable(onLongClick=)` yalnız DOKUNMADA tetikleniyor: kumandanın
    // OK'unu basılı tutmak hiçbir şey yapmıyordu (Dean, hem LG hem Mi Box).
    // Framework uzun basışta ACTION_DOWN'u FLAG_LONG_PRESS / repeatCount>0 ile
    // tekrarlar; bırakıştaki ACTION_UP yutulur, yoksa menü açılır açılmaz
    // arkasından kart da açılır.
    var uzunBasildi by remember { mutableStateOf(false) }
    // Kumanda yolu: onKeyEvent clickable'dan SONRA duruyordu — odak hedefinin altında
    // kaldığı için olay ona hiç ulaşmıyordu; uzun basış Mi Box'ta hiç açılmadı.
    // onPreviewKeyEvent clickable'dan ÖNCE: OK'un tamamı burada, tek-bas/uzun-bas
    // kararı tek yerde. Tekrar göndermeyen kumanda için süre dolunca da açılır.
    val scope = rememberCoroutineScope()
    var uzunZamanlayici by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val uzunBas = {
        if (!uzunBasildi) {
            uzunBasildi = true
            onLongPress()
        }
    }
    val scale = nmFocusScale(focused, NmDim.FocusScaleCard, label = "posterScale")
    val shape = RoundedCornerShape(NmDim.CardRadius)
    Box(
        modifier = modifier
            .width(com.evaitec.netmovies.tv.ui.theme.nmRafPosterGenisligi())
            .aspectRatio(2f / 3f)
            .nmScale(scale)
            .zIndex(if (focused) 1f else 0f)
            .clip(shape)
            .background(NmColor.SurfaceHigh)
            .nmFocusRingOnly(focused, shape)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { ke ->
                val ne = ke.nativeKeyEvent
                if (ne.keyCode != android.view.KeyEvent.KEYCODE_DPAD_CENTER &&
                    ne.keyCode != android.view.KeyEvent.KEYCODE_ENTER &&
                    ne.keyCode != android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
                ) {
                    false
                } else {
                    when (ne.action) {
                        android.view.KeyEvent.ACTION_DOWN ->
                            if (ne.repeatCount == 0) {
                                uzunBasildi = false
                                uzunZamanlayici?.cancel()
                                uzunZamanlayici = scope.launch {
                                    kotlinx.coroutines.delay(UZUN_BASIS_MS)
                                    uzunBas()
                                }
                            } else if (ne.isLongPress || ne.repeatCount > 0) {
                                uzunBas()
                            }
                        android.view.KeyEvent.ACTION_UP -> {
                            uzunZamanlayici?.cancel()
                            if (!uzunBasildi) onClick()
                            uzunBasildi = false
                        }
                    }
                    true
                }
            }
            // Dokunma yolu (telefon): uzun basış pointer'la burada.
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        PosterImage(poster = item.poster, title = item.title)
        // Başlık degradesi — poster ne olursa olsun yazı okunur kalsın.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(54.dp)
                .background(nmBottomScrim),
        )
        // İzlenen oran — Devam Et rafında nerede kaldığın tek bakışta görünsün.
        if (progress > 0f) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(NmColor.TrackIdle),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(NmColor.Primary),
                )
            }
        }
        if (isFavorite) {
            Text(
                text = "★",
                color = NmColor.Star,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            )
        }
        // TMDB puanı — sol üstte, okunsun diye kendi zemininde.
        item.rating?.let { puan ->
            Text(
                text = "★ %.1f".format(puan),
                color = NmColor.Star,
                fontSize = NmType.Caption,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(NmDim.PillRadius))
                    .background(NmColor.ScrimSoft)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        // Dil rozetleri — daha önce bir kez açılmış içerikte dolu gelir
        // (sunucu: lang_memo.py). Başlık yazısının hemen üstünde durur.
        if (item.lang.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 34.dp),
            ) {
                item.lang.forEach { rozet ->
                    Text(
                        text = rozet,
                        fontSize = NmType.Caption,
                        fontWeight = FontWeight.SemiBold,
                        color = if (rozet == "DUB") NmColor.Primary else NmColor.OnSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(NmDim.PillRadius))
                            .background(NmColor.ScrimSoft)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
        Text(
            text = item.title.orEmpty(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontSize = NmType.Label,
            color = NmColor.OnSurface,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
        )
    }
}

/**
 * Menüdeki kaynak yoklamasının hâli. Oynat satırının sonuna yazılır ki basmadan
 * önce çalışıp çalışmayacağı görünsün.
 */
private sealed interface Yoklama {
    object Baslamadi : Yoklama
    object Suruyor : Yoklama
    /** `diller` sunucunun dil etiketleri — dublaj var mı, basmadan önce görünsün. */
    data class Bulundu(val adet: Int, val diller: List<String> = emptyList()) : Yoklama
    object Yok : Yoklama

    fun kuyruk(): String = when (this) {
        Baslamadi     -> ""
        Suruyor       -> "   ·  kaynak yoklanıyor…"
        is Bulundu    -> "   ·  $adet kaynak ✓" + diller.joinToString(", ", prefix = "  ·  ").takeIf { diller.isNotEmpty() }.orEmpty()
        Yok           -> "   ·  kaynak bulunamadı"
    }
}

// Poster uzun-bas pad'i — tam sayfa DEĞİL. Dean (22 Eylül): "koca bir liste
// açıyor... 4 yön tuşu gibi pad, hiçbir özellik tam sayfa olmasın."
// Merkez oynatır, her yön kendi küçük katmanını açar, GERİ bir katman geri alır.
//
//            ▲ Bölümler
//   ◀ Özet   ▶ OYNAT   Benzerleri ▶
//            ▼ Listeler
//
// Gezinme index'le yapılır, Compose odak ağacına GÜVENİLMEZ: aynı hata oynatıcıda
// iki kez yaşandı (odak katman üstü karta inmiyor).
private enum class PadMod { PAD, BOLUM, LISTE, OZET, BENZER }

/** Alt sıradaki liste düğmeleri — sıra ekranda göründüğü sıradır. */
private val LISTE_SIRASI = listOf(
    Library.LISTE_IZLENECEK,
    Library.LISTE_TAKIP,
    "favori",
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PosterMenu(
    item: MediaItem,
    library: Library,
    onPlay: () -> Unit,
    onPlayEpisode: (Int) -> Unit,
    onOpenItem: (MediaItem) -> Unit,
    onClose: () -> Unit,
) {
    // Tek `load_item` isteği: bölümler DE özet DE buradan gelir. Film ise
    // `episodes` boş döner ve YUKARI yönü hiç çizilmez.
    var detay by remember(item.url) { mutableStateOf<com.evaitec.netmovies.tv.data.ItemDetails?>(null) }
    LaunchedEffect(item.url) {
        detay = runCatching { Network.api.loadItem(item.plugin, item.url, item.title, item.mediaType.ifBlank { null }).result }.getOrNull()
    }
    val bolumler = detay?.episodes.orEmpty()

    // Seçilen bölüm ve onun kaynak yoklaması. Bölüm seçince DOĞRUDAN oynatmak,
    // kaynak yoksa oynatıcıyı açıp kapatıyordu — izleyen "bir şey oldu, kapandı"
    // görüyordu (Dean, 17 Eylül). Sonuç OYNAT'ın üstünde yazılı durur.
    var secilenBolum by remember(item.url) { mutableStateOf<Int?>(null) }
    var yoklama by remember(item.url) { mutableStateOf<Yoklama>(Yoklama.Baslamadi) }

    LaunchedEffect(item.url, secilenBolum, bolumler.size) {
        // Dizide bölüm seçilmeden yoklama yapılmaz: hangi bölümün kaynağına
        // bakılacağı belli değil, boşuna zincir taraması olur.
        if (bolumler.isNotEmpty() && secilenBolum == null) {
            yoklama = Yoklama.Baslamadi
            return@LaunchedEffect
        }
        yoklama = Yoklama.Suruyor
        yoklama = runCatching {
            Network.api.resolveSources(
                plugin = item.plugin,
                encodedUrl = item.url,
                title = item.title,
                episode = secilenBolum?.let { it + 1 } ?: 0,
                mode = "fast",
            ).result?.sources.orEmpty()
        }.fold(
            onSuccess = { kaynaklar ->
                if (kaynaklar.isEmpty()) Yoklama.Yok
                else Yoklama.Bulundu(
                    kaynaklar.size,
                    // Dil sıralı geldiği için sıra korunur: dublaj varsa en başta yazar.
                    kaynaklar.mapNotNull { it.language?.label?.takeIf { l -> l.isNotBlank() } }.distinct(),
                )
            },
            onFailure = { Yoklama.Yok },
        )
    }

    // Benzerler yalnız SAĞ'a basınca yüklenir: her kart açılışında TMDB'ye gitmek,
    // çoğu açılışta hiç bakılmayan bir liste için istek demek.
    var benzerler by remember(item.url) { mutableStateOf<List<com.evaitec.netmovies.tv.data.SimilarItem>?>(null) }
    var benzerDurum by remember(item.url) { mutableStateOf("") }
    var benzerIdx by remember(item.url) { mutableStateOf(0) }

    var mod by remember(item.url) { mutableStateOf(PadMod.PAD) }
    var bolumIdx by remember(item.url) { mutableStateOf(0) }
    var listeIdx by remember(item.url) { mutableStateOf(0) }
    val bolumState = rememberLazyListState()
    val benzerState = rememberLazyListState()
    val ozetState = rememberScrollState()
    val kapsam = rememberCoroutineScope()

    LaunchedEffect(mod) {
        if (mod == PadMod.BENZER && benzerler == null) {
            benzerDurum = "Benzerler aranıyor…"
            benzerler = runCatching {
                Network.api.similar(item.title.orEmpty(), if (bolumler.isNotEmpty()) "serie" else "movie").result
            }.getOrElse { emptyList() }
            benzerDurum = if (benzerler.isNullOrEmpty()) "Benzer bulunamadı" else ""
        }
    }

    // Seçili satır listenin görünmeyen yerine kayarsa kullanıcı neyi seçtiğini
    // göremez: her adımda o satıra kaydırılır.
    LaunchedEffect(bolumIdx, mod) {
        if (mod == PadMod.BOLUM && bolumler.isNotEmpty()) {
            runCatching { bolumState.scrollToItem(bolumIdx.coerceIn(0, bolumler.lastIndex)) }
        }
    }
    LaunchedEffect(benzerIdx, mod) {
        val adet = benzerler?.size ?: 0
        if (mod == PadMod.BENZER && adet > 0) {
            runCatching { benzerState.scrollToItem(benzerIdx.coerceIn(0, adet - 1)) }
        }
    }

    fun oynat() {
        secilenBolum?.let(onPlayEpisode) ?: onPlay()
    }

    fun listeUygula(i: Int) {
        when (LISTE_SIRASI.getOrNull(i)) {
            Library.LISTE_IZLENECEK -> library.toggleListe(item, Library.LISTE_IZLENECEK)
            Library.LISTE_TAKIP     -> library.toggleListe(item, Library.LISTE_TAKIP)
            else                    -> library.toggleFavorite(item)
        }
    }

    /** Benzer seçimi katalog kartı değil, bir BAŞLIK: aramaya beslenir. */
    fun benzerAc(secim: com.evaitec.netmovies.tv.data.SimilarItem) {
        benzerDurum = "Aranıyor: " + secim.title
        kapsam.launch {
            val bulunan = runCatching {
                Network.api.searchAll(secim.title).result.orEmpty().firstOrNull()
            }.getOrNull()
            if (bulunan == null) benzerDurum = "Katalogda yok: " + secim.title
            else onOpenItem(bulunan)
        }
    }

    // Kartın kendi tuş işleyicisi. `true` = tüketildi; arkadaki raflar hiçbir tuş
    // görmez (modal açıkken `canFocus = false` zaten odak aramasını da kesiyor).
    fun tus(code: Int): Boolean {
        val bolumVar = bolumler.isNotEmpty()
        when (mod) {
            PadMod.PAD -> when (code) {
                android.view.KeyEvent.KEYCODE_DPAD_UP    -> if (bolumVar) mod = PadMod.BOLUM
                android.view.KeyEvent.KEYCODE_DPAD_DOWN  -> mod = PadMod.LISTE
                android.view.KeyEvent.KEYCODE_DPAD_LEFT  -> mod = PadMod.OZET
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> mod = PadMod.BENZER
                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                android.view.KeyEvent.KEYCODE_ENTER      -> oynat()
                else -> return false
            }
            PadMod.BOLUM -> when (code) {
                android.view.KeyEvent.KEYCODE_DPAD_UP   -> if (bolumIdx == 0) mod = PadMod.PAD else bolumIdx--
                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> bolumIdx = (bolumIdx + 1).coerceAtMost(bolumler.lastIndex)
                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                android.view.KeyEvent.KEYCODE_ENTER     -> { secilenBolum = bolumIdx; onPlayEpisode(bolumIdx) }
                android.view.KeyEvent.KEYCODE_BACK      -> mod = PadMod.PAD
                else -> return false
            }
            PadMod.LISTE -> when (code) {
                android.view.KeyEvent.KEYCODE_DPAD_LEFT  -> listeIdx = (listeIdx - 1).coerceAtLeast(0)
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> listeIdx = (listeIdx + 1).coerceAtMost(LISTE_SIRASI.lastIndex)
                android.view.KeyEvent.KEYCODE_DPAD_UP    -> mod = PadMod.PAD
                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                android.view.KeyEvent.KEYCODE_ENTER      -> listeUygula(listeIdx)
                android.view.KeyEvent.KEYCODE_BACK       -> mod = PadMod.PAD
                else -> return false
            }
            PadMod.OZET -> when (code) {
                android.view.KeyEvent.KEYCODE_DPAD_UP    -> kapsam.launch { ozetState.scrollTo((ozetState.value - 120).coerceAtLeast(0)) }
                android.view.KeyEvent.KEYCODE_DPAD_DOWN  -> kapsam.launch { ozetState.scrollTo(ozetState.value + 120) }
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                android.view.KeyEvent.KEYCODE_BACK       -> mod = PadMod.PAD
                else -> return false
            }
            PadMod.BENZER -> when (code) {
                android.view.KeyEvent.KEYCODE_DPAD_LEFT  -> if (benzerIdx == 0) mod = PadMod.PAD else benzerIdx--
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT ->
                    benzerIdx = (benzerIdx + 1).coerceAtMost(((benzerler?.size ?: 1) - 1).coerceAtLeast(0))
                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                android.view.KeyEvent.KEYCODE_ENTER      -> benzerler?.getOrNull(benzerIdx)?.let { benzerAc(it) }
                android.view.KeyEvent.KEYCODE_BACK       -> mod = PadMod.PAD
                else -> return false
            }
        }
        return true
    }

    // Pad'i AÇAN uzun basış hâlâ basılı: framework OK'un ACTION_DOWN tekrarlarını
    // yeni odaklanan düğüme — yani pad'e — göndermeye devam ediyor. Pad o tekrarı
    // "OK'a basıldı" sayıp ORTA düğmeyi (Oynat) anında çalıştırıyordu: kullanıcı
    // henüz parmağını kaldırmadan komut gidiyordu. Pad, kendi gördüğü ilk
    // BIRAKMAYA kadar hiçbir tuşu işlemez.
    var tusHazir by remember(item.url) { mutableStateOf(false) }

    val kartFocus = remember { FocusRequester() }
    // Tek `requestFocus()` ilk karede henüz yerleşmemiş düğümde sessizce başarısız
    // olur; tuşlar o zaman hiçbir yere gitmez (oynatıcıda aynı tuzak yaşandı).
    LaunchedEffect(item.url) {
        repeat(8) {
            if (runCatching { kartFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            withFrameNanos {}
        }
    }
    NmBackHandler(enabled = true) { if (mod == PadMod.PAD) onClose() else mod = PadMod.PAD }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Yarı saydam zemin: pad küçük, arkadaki raflar görünür kalsın —
            // "hiçbir özellik tam sayfa olmasın".
            .background(NmColor.ScrimSoft)
            .zIndex(10f)
            .focusRequester(kartFocus)
            .onKeyEvent { ke ->
                val ne = ke.nativeKeyEvent
                when (ne.action) {
                    android.view.KeyEvent.ACTION_UP -> { tusHazir = true; true }
                    // Pad'i açan basışın tekrarları yutulur (bkz. `tusHazir`).
                    android.view.KeyEvent.ACTION_DOWN -> if (tusHazir) tus(ne.keyCode) else true
                    else -> true
                }
            }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                // PAD: panel YOK. Dean: "arka alan ve açıklamaya gerek yok, küçük bir
                // joystick gibi yeterli" — beş ikon ekranın üstünde serbest durur.
                // Alt modlar (bölüm listesi, özet, benzerler) panelde kalır.
                .then(if (mod == PadMod.PAD) Modifier.wrapContentWidth() else Modifier.width(620.dp))
                .wrapContentHeight()
                .clip(RoundedCornerShape(NmDim.PanelRadius))
                .then(
                    if (mod == PadMod.PAD) Modifier
                    else Modifier.background(NmColor.SurfaceHigh).padding(18.dp),
                ),
        ) {
            // PAD modunda künye de yok: joystick tek başına. Alt modlarda afişli
            // künye hangi içerikte olduğunu taşır.
            if (mod != PadMod.PAD) {
                // Künye — hangi içerikte olduğun her katmanda görünür.
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier
                            .width(62.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(NmDim.CardRadius)),
                    ) {
                        PosterImage(poster = detay?.poster ?: item.poster, title = item.title)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = detay?.title ?: item.title.orEmpty(),
                            fontSize = NmType.RowTitle,
                            fontWeight = FontWeight.Bold,
                            color = NmColor.OnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val kunye = listOfNotNull(
                            detay?.yearText?.takeIf { it.isNotBlank() },
                            detay?.ratingText?.takeIf { it.isNotBlank() }?.let { "★ " + it },
                            detay?.tagsText?.takeIf { it.isNotBlank() },
                        ).joinToString("  ·  ")
                        if (kunye.isNotBlank()) {
                            Text(
                                text = kunye,
                                fontSize = NmType.Caption,
                                color = NmColor.OnSurfaceMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val diller = (yoklama as? Yoklama.Bulundu)?.diller.orEmpty()
                        if (diller.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { diller.forEach { Rozet(it) } }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
            }

            when (mod) {
                // YONCA: dört küçük ikon + ortada oynat. Yazı yok — Dean: "isim
                // yazmasına gerek yok, küçük sadece ikon". Ne olduğu alt satırdaki
                // ipucu şeridinde yazar, düğmenin üstünde değil.
                PadMod.PAD -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // Detay gelmediyse kol soluk ama "bölüm yok" demek değil.
                        YoncaKol("☰", aktif = bolumler.isNotEmpty() || detay == null)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            YoncaKol("ℹ")
                            YoncaOrta()
                            YoncaKol("✧")
                        }
                        YoncaKol("☆")
                    }
                }

                // YUKARI: bölümler — küçük liste, pad'in içinde kalır.
                PadMod.BOLUM -> {
                    SutunBasligi("Bölümler (" + bolumler.size + ")", true)
                    LazyColumn(state = bolumState, modifier = Modifier.heightIn(max = 260.dp)) {
                        itemsIndexed(bolumler) { i, ep ->
                            val numara = ep.episode?.let { "S" + ep.season + "B" + it } ?: ("Bölüm " + (i + 1))
                            val ad = ep.title?.takeIf { it.isNotBlank() }
                            KartSatir(
                                label = if (ad != null) numara + " · " + ad else numara,
                                secili = i == bolumIdx,
                                isaretli = i == secilenBolum,
                            )
                        }
                    }
                }

                // AŞAĞI: üç düğme yan yana — izleneceklerim, takip, beğendiklerim.
                PadMod.LISTE -> {
                    SutunBasligi("Listeler", true)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.weight(1f)) {
                            KartSatir(
                                label = if (library.inIzlenecek(item)) "☆ İzleneceklerde ✓" else "☆ İzleneceklere",
                                secili = listeIdx == 0,
                            )
                        }
                        Box(Modifier.weight(1f)) {
                            KartSatir(
                                label = if (library.inTakip(item)) "📋 Takipte ✓" else "📋 Takip et",
                                secili = listeIdx == 1,
                            )
                        }
                        Box(Modifier.weight(1f)) {
                            KartSatir(
                                label = if (library.isFavorite(item)) "★ Beğendim ✓" else "★ Beğendim",
                                secili = listeIdx == 2,
                            )
                        }
                    }
                }

                // SOL: özet — orta boy, kaydırılabilir.
                PadMod.OZET -> {
                    SutunBasligi("Özet", true)
                    Column(Modifier.heightIn(max = 240.dp).verticalScroll(ozetState)) {
                        Text(
                            text = detay?.description?.takeIf { it.isNotBlank() } ?: "Özet yok.",
                            fontSize = NmType.Caption,
                            color = NmColor.OnSurfaceMuted,
                        )
                    }
                }

                // SAĞ: benzerleri — yatay şerit. Katalog kartı değil, TMDB başlığı.
                PadMod.BENZER -> {
                    SutunBasligi("Benzerleri", true)
                    if (benzerDurum.isNotBlank()) {
                        Text(benzerDurum, fontSize = NmType.Caption, color = NmColor.OnSurfaceMuted)
                    }
                    LazyRow(
                        state = benzerState,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(benzerler.orEmpty()) { i, b ->
                            Column(Modifier.width(92.dp)) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(2f / 3f)
                                        .clip(RoundedCornerShape(NmDim.CardRadius))
                                        .nmFocusRing(i == benzerIdx, RoundedCornerShape(NmDim.CardRadius)),
                                ) {
                                    PosterImage(poster = b.poster, title = b.title)
                                }
                                Text(
                                    text = b.title,
                                    fontSize = NmType.Caption,
                                    color = if (i == benzerIdx) NmColor.OnSurface else NmColor.OnSurfaceMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            // İpucu şeridi PAD modunda yok: joystick açıklamasız durur.
            if (mod != PadMod.PAD) {
                Text(
                    text = when (mod) {
                        PadMod.PAD    -> "▶ oynat · ☰ bölüm · ℹ özet · ✧ benzer · ☆ liste"
                        PadMod.BOLUM  -> "▲▼ gez   OK oynat   GERİ pad"
                        PadMod.LISTE  -> "◀▶ seç   OK ekle/çıkar   GERİ pad"
                        PadMod.OZET   -> "▲▼ kaydır   GERİ pad"
                        PadMod.BENZER -> "◀▶ gez   OK ara ve aç   GERİ pad"
                    },
                    fontSize = NmType.Caption,
                    color = NmColor.OnSurfaceFaint,
                    textAlign = if (mod == PadMod.PAD) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun SutunBasligi(text: String, aktif: Boolean) {
    Text(
        text = text,
        fontSize = NmType.Caption,
        fontWeight = FontWeight.Bold,
        color = if (aktif) NmColor.Primary else NmColor.OnSurfaceMuted,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

/** Yoncanın bir yön kolu: tek ikon, küçük kare. Pasifse soluk çizilir. */
@Composable
private fun YoncaKol(ikon: String, aktif: Boolean = true) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(RoundedCornerShape(NmDim.RowRadius))
            .background(NmColor.SurfaceHigh),   // panelsiz joystickte yarı saydam zemin afişte kayboluyordu
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = ikon,
            fontSize = NmType.Body,
            color = if (aktif) NmColor.OnSurface else NmColor.OnSurfaceFaint,
        )
    }
}

/** Yoncanın ortası: OK'un doğrudan çalıştırdığı eylem (oynat), hep vurgulu. */
@Composable
private fun YoncaOrta() {
    val shape = RoundedCornerShape(NmDim.RowRadius)
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(shape)
            .background(NmColor.Primary)
            .nmFocusRing(true, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text("▶", fontSize = NmType.Body, fontWeight = FontWeight.Bold, color = NmColor.OnPrimary)
    }
}

/** Kart içindeki tek satır. Seçim index'ten gelir — Compose odağı kullanılmaz. */
@Composable
private fun KartSatir(
    label: String,
    secili: Boolean,
    isaretli: Boolean = false,
    buyuk: Boolean = false,
) {
    val shape = RoundedCornerShape(NmDim.RowRadius)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(shape)
            .background(if (secili) NmColor.Primary else NmColor.ScrimSoft)
            .nmFocusRing(secili, shape)
            .padding(horizontal = 14.dp, vertical = if (buyuk) 14.dp else 9.dp),
    ) {
        Text(
            text = if (isaretli) "•  $label" else label,
            fontSize = if (buyuk) NmType.Body else NmType.Caption,
            fontWeight = if (secili || buyuk) FontWeight.Bold else FontWeight.Medium,
            color = if (secili) NmColor.OnPrimary else NmColor.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Rozet(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(NmDim.PillRadius))
            .background(NmColor.ScrimSoft)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, fontSize = NmType.Caption, color = NmColor.OnSurface)
    }
}


// Ortak modal kabuğu: scrim + panel + başlık. Menülerin görünümü tek yerden gelir.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ModalCard(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(NmDim.PanelRadius)

    // Modal açılınca odak İÇERİ taşınır. Bu yoktu: odak arkadaki "Ayarlar" butonunda
    // kalıyor, D-pad aşağı menüyü değil alttaki film raflarını geziyordu.
    // İlk kare henüz yerleşmemiş olabilir → birkaç kare boyunca denenir.
    val panelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(8) {
            if (runCatching { panelFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            withFrameNanos { }
        }
    }

    // GERİ modalı kapatır (arkadaki ana ekran handler'ı modal açıkken kapalıdır).
    NmBackHandler(enabled = true) { onClose() }

    Box(
        // Ortadaki kutu ekranı kaplıyordu: menü ve bölüm listesi sağ alta,
        // dar bir sütuna alındı; arkadaki raflar görünür kalır (Dean).
        Modifier.fillMaxSize().background(NmColor.ScrimSoft).padding(NmDim.SafeArea),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Column(
            modifier = Modifier
                .width(NmDim.DialogWidth * 0.72f)
                // Kutu ekranin tamamini kapliyordu: dort satirlik menu icin bos bir
                // sutun uzayip gidiyordu (Dean, 17 Eylul: "cok uzun bir liste fakat
                // ici bos"). Yukseklik icerik kadar, uzun listede kaydirmaya duser.
                .wrapContentHeight()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .clip(shape)
                .background(NmColor.SurfaceDialog)
                .nmFocusRing(false, shape)
                .focusRequester(panelFocus)
                .focusGroup()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = NmType.RowTitle,
                color = NmColor.Primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            content()
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(NmDim.RowRadius)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isFocused) NmColor.Primary else NmColor.Surface)
            .nmFocusRing(isFocused, shape)
            .onFocusChanged { isFocused = it.isFocused }
            // `clickable` zaten odaklanabilir yapar; ayrıca `focusable()` eklemek
            // satır başına İKİ odak hedefi üretiyor ve D-pad'de bir aşağı basış
            // yutuluyordu. Tek hedef bırakıldı.
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            fontSize = NmType.Body,
            color = if (isFocused) NmColor.OnPrimary else NmColor.OnSurface,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Center(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, fontSize = NmType.Body, color = NmColor.OnSurfaceMuted)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsMenu(
    onOpenKeyMap: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenChannels: () -> Unit,
    onClose: () -> Unit,
    updateVm: UpdateViewModel = viewModel(),
) {
    val updateState by updateVm.ui.collectAsStateWithLifecycle()
    ModalCard(title = "Ayarlar", onClose = onClose) {
        // Yüklü sürüm hep görünür: "güncelleme geldi mi" sorusu tahminle değil,
        // ekrandaki numarayla cevaplanır.
        // Düz yazı: satır odak alıyor ama OK hiçbir şey yapmıyordu.
        Text(
            "Sürüm: ${BuildConfig.RELEASE_TAG}",
            fontSize = NmType.Label,
            color = NmColor.OnSurfaceMuted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        // Güncelleme şeridi ekranın en üstünde; oraya ulaşmak için D-pad ile iki kez
        // yukarı çıkmak gerekiyordu. Aynı eylem burada da, doğrudan erişilebilir.
        // Durum satırı menüde KALIR: eskiden kontrol menüyü kapatıyor, sonuç ana
        // ekranın şeridinde beliriyordu — kullanıcı hiçbir şey olmadı sanıyordu.
        when (val u = updateState) {
            is UpdateUi.Available ->
                MenuRow("⬆  Güncelle: ${u.info.tag}", onClick = { onClose(); updateVm.download(u.info) })
            is UpdateUi.NeedsPermission ->
                MenuRow("🔓  Kurulum iznini aç", onClick = { onClose(); updateVm.grantInstallPermission(u.info) })
            is UpdateUi.Downloading ->
                MenuRow("⏬  İndiriliyor: ${u.tag}", onClick = {})
            is UpdateUi.Opened ->
                MenuRow("📦  Kurulum açıldı: ${u.tag}", onClick = {})
            is UpdateUi.UpToDate ->
                MenuRow("✔  Güncel (${u.tag}) — tekrar kontrol et", onClick = { updateVm.check(verbose = true) })
            is UpdateUi.Failed ->
                MenuRow("⚠  ${u.message} — tekrar dene", onClick = { updateVm.check(verbose = true) })
            UpdateUi.Idle ->
                MenuRow("⬆  Güncellemeyi kontrol et", onClick = { updateVm.check(verbose = true) })
        }
        MenuRow("⚙  Buton Eşleme", onClick = { onClose(); onOpenKeyMap() })
        // Tek satır: eskiden önce "Göster" bayrağı çevrilip Ayarlar TEKRAR açılıyordu.
        // İki adımın ikincisi bulunamıyordu; koleksiyon doğrudan açılıyor.
        // Kilit ikonu yok: PIN/parola YOK, güvenlik vaat edilmiyor.
        // Listem ve Ajanda üst barda (★ / 🗓); burada ikinci kopyaları vardı.
        MenuRow("🗂  Özel Koleksiyon", onClick = { onClose(); onOpenVault() })
        // Web'deki /admin paneli — gizli kaynak/kategori, öne çıkanlar, puan eşiği.
        MenuRow("🛠  Yönetim Paneli", onClick = { onClose(); onOpenAdmin() })
        MenuRow("✕  Kapat", onClose)
    }
}
