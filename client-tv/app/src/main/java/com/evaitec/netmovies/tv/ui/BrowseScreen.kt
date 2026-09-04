package com.evaitec.netmovies.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.evaitec.netmovies.tv.data.MediaItem
import com.evaitec.netmovies.tv.data.Network
import com.evaitec.netmovies.tv.data.PluginInfo
import com.evaitec.netmovies.tv.ui.theme.NmColor
import com.evaitec.netmovies.tv.ui.theme.NmDim
import com.evaitec.netmovies.tv.ui.theme.NmType
import com.evaitec.netmovies.tv.ui.theme.nmBottomScrim
import com.evaitec.netmovies.tv.ui.theme.nmFocusRing
import com.evaitec.netmovies.tv.ui.theme.nmFocusRingOnly
import com.evaitec.netmovies.tv.ui.theme.nmFocusScale
import com.evaitec.netmovies.tv.ui.theme.nmScale
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLDecoder

// Ozel Koleksiyon'a dusen eklenti adlari (kucuk harf, "contains" ile eslesir).
private val VAULT_KEYWORDS = listOf("porner", "porn", "spank", "hamster", "oxax", "maza")

private fun decode(s: String): String =
    runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

/** Ekran açılır açılmaz paralel çekilecek raf sayısı (üstteki görünür bölge). */
private const val PREFETCH_SHELVES = 6

/** Bir raf = (eklenti, kategori) çifti. Ana sayfadaki gibi yatay poster şeridi. */
private data class Shelf(
    val plugin: PluginInfo,
    val encUrl: String,
    val encCat: String,
) {
    val key: String get() = "${plugin.name}|$encCat"
    val title: String get() = "${plugin.name} · ${decode(encCat)}"
}

/** Rafın içeriğini çeker; hata/zaman aşımı boş liste (raf gizlenir). */
private suspend fun fetchShelf(shelf: Shelf): List<MediaItem> =
    withTimeoutOrNull(20_000) {
        runCatching {
            Network.api.getMainPage(shelf.plugin.name, 1, shelf.encUrl, shelf.encCat).result
                .map { it.copy(plugin = shelf.plugin.name, category = decode(shelf.encCat)) }
        }.getOrDefault(emptyList())
    } ?: emptyList()

// Gözat: her kategori kendi içeriğini poster rafı olarak gösterir (düz metin listesi yerine).
// Arama üstte sadece büyüteç; seçilince metin alanına dönüşür.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BrowseScreen(
    showVault: Boolean = false,
    vaultMode: Boolean = false,
    onSelect: (MediaItem) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var rawPlugins by remember { mutableStateOf<List<PluginInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Ozel Koleksiyon filtresi. Yeni bir kaynak eklendiginde anahtar kelimesi
    // BURAYA eklenir; listede olmayan eklenti normal raflarda gorunur (sessiz
    // sizinti). Tek nokta olsun diye ayri sabit.
    val isAdultPlugin = { name: String ->
        val n = name.lowercase()
        VAULT_KEYWORDS.any { n.contains(it) }
    }

    val plugins = remember(rawPlugins, showVault, vaultMode) {
        if (vaultMode) {
            rawPlugins.filter { isAdultPlugin(it.name) }
        } else if (showVault) {
            rawPlugins
        } else {
            rawPlugins.filter { !isAdultPlugin(it.name) }
        }
    }

    // Tek kaynak seçilebilir: tüm eklentilerin kategorileri alt alta dizilince
    // ekranda 40+ raf oluyordu ve aşağıdan yukarı dönmek işkenceydi.
    // null = "Tümü" (eski davranış).
    var selectedPlugin by remember { mutableStateOf<String?>(null) }

    val shelves = remember(plugins, selectedPlugin) {
        plugins.filter { selectedPlugin == null || it.name == selectedPlugin }
            .flatMap { plugin ->
                plugin.mainPage.entries.map { Shelf(plugin, it.key, it.value) }
            }
    }

    // Raf içerikleri: ekran boyunca yaşar → yukarı/aşağı gezinirken tekrar çekilmez.
    val shelfCache = remember { mutableStateMapOf<String, List<MediaItem>>() }
    // Aynı rafı hem önyükleme hem de satırın kendisi çekmesin.
    val started = remember { mutableSetOf<String>() }

    // İlk raflar ekrana girmeyi beklemeden PARALEL çekilir; sunucu tarafı 30 dk
    // cache'lediği için sonraki açılışlar anında gelir (Dean: "çok geç yükleniyor").
    LaunchedEffect(shelves) {
        val head = shelves.take(PREFETCH_SHELVES).filter { started.add(it.key) }
        if (head.isEmpty()) return@LaunchedEffect
        coroutineScope {
            head.map { shelf -> async { shelfCache[shelf.key] = fetchShelf(shelf) } }.awaitAll()
        }
    }

    // Kaydırma konumu ve son odaklı raf ekran seviyesinde tutulur; arama sonucuna
    // girip çıkınca liste en üstten başlamasın (Dean: "en üstten başlıyor, olmuyor").
    val listState = rememberLazyListState()
    var focusedShelf by remember { mutableStateOf(0) }

    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MediaItem>?>(null) }
    var resultsTitle by remember { mutableStateOf("") }
    var resultsLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            rawPlugins = Network.api.getAllPlugins().result
        } catch (e: Exception) {
            error = e.message ?: "Eklentiler yüklenemedi"
        }
        loading = false
    }

    // GERİ: arama/sonuç açıksa onu kapatır; raflarda aşağıdaysa önce EN ÜSTE döner;
    // en üstteyken ana ekrana çıkar. Aşağıdayken tek basışta ekrandan atmaz.
    val browseScope = rememberCoroutineScope()
    val atTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
    }
    BackHandler(enabled = true) {
        when {
            searchOpen      -> { searchOpen = false; query = "" }
            results != null -> results = null
            !atTop          -> browseScope.launch { listState.animateScrollToItem(0) }
            selectedPlugin != null -> selectedPlugin = null
            else            -> onBack()
        }
    }

    // Tüm eklentilerde paralel ara, birleştir.
    fun doSearch(q: String) {
        val term = q.trim()
        if (term.isEmpty()) return
        searchOpen = false
        resultsTitle = "Arama: $term"
        results = emptyList()
        resultsLoading = true
        scope.launch {
            val names = plugins.map { it.name }
            results = if (names.isEmpty()) emptyList() else coroutineScope {
                names.map { n ->
                    async {
                        // Yavaş kaynak (12s) tüm aramayı kilitlemesin → o kaynak boş sayılır.
                        withTimeoutOrNull(12_000) {
                            runCatching { Network.api.search(n, term).result.map { it.copy(plugin = n) } }
                                .getOrDefault(emptyList())
                        } ?: emptyList()
                    }
                }.awaitAll().flatten()
            }
            resultsLoading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        BrowseTopBar(
            open = searchOpen,
            query = query,
            onQueryChange = { query = it },
            onOpen = { searchOpen = true },
            onSearch = { doSearch(query) },
        )
        if (results == null && plugins.isNotEmpty()) {
            SourceChips(
                names = plugins.map { it.name },
                selected = selectedPlugin,
                onSelect = { name ->
                    selectedPlugin = name
                    focusedShelf = 0
                    browseScope.launch { listState.scrollToItem(0) }
                },
            )
        }
        Box(Modifier.fillMaxSize()) {
            when {
                results != null -> ItemGrid(
                    title = resultsTitle,
                    items = results!!,
                    loading = resultsLoading,
                    onSelect = onSelect,
                )
                loading      -> Center("Eklentiler yükleniyor…")
                error != null -> Center(error!!)
                else -> ShelfList(
                    shelves = shelves,
                    cache = shelfCache,
                    started = started,
                    listState = listState,
                    focusedShelf = focusedShelf,
                    onShelfFocused = { focusedShelf = it },
                    onSelect = onSelect,
                )
            }
        }
    }
}

// --------------------------------------------------------------------- Üst bar
// Kapalıyken sadece büyüteç düğmesi; OK'a basınca metin alanı açılır (Dean: "çok kaba").
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BrowseTopBar(
    open: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpen: () -> Unit,
    onSearch: () -> Unit,
) {
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(open) { if (open) runCatching { fieldFocus.requestFocus() } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = NmDim.SafeH, end = NmDim.SafeH, top = NmDim.SafeV, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Gözat",
            fontWeight = FontWeight.Bold,
            fontSize = NmType.ScreenTitle,
            color = NmColor.OnSurface,
            modifier = Modifier.weight(1f),
        )
        if (open) {
            var focused by remember { mutableStateOf(false) }
            val shape = RoundedCornerShape(NmDim.PillRadius)
            Box(
                modifier = Modifier
                    .width(320.dp)
                    .clip(shape)
                    .background(NmColor.SurfaceHigh)
                    .nmFocusRing(focused, shape)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = NmColor.OnSurface, fontSize = NmType.Body),
                    cursorBrush = SolidColor(NmColor.Primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }, onDone = { onSearch() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(fieldFocus)
                        .onFocusChanged { focused = it.isFocused },
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text("Ara…", color = NmColor.OnSurfaceFaint, fontSize = NmType.Body)
                        }
                        inner()
                    },
                )
            }
        } else {
            IconPill(glyph = "🔎", onClick = onOpen)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun IconPill(glyph: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale = nmFocusScale(focused, NmDim.FocusScalePill, label = "browseIcon")
    Box(
        modifier = Modifier
            .size(46.dp)
            .nmScale(scale)
            .clip(CircleShape)
            .background(if (focused) NmColor.Primary else NmColor.Surface)
            .nmFocusRing(focused, CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontSize = NmType.Body, color = NmColor.OnSurface)
    }
}

// ------------------------------------------------------------------ Kaynaklar
// Eklenti (kanal) seçici. Seçilen kaynağın kategorileri gösterilir; bir kaynağın
// rafları başka kaynağınkilerle iç içe geçmez.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SourceChips(names: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().focusGroup(),
        contentPadding = PaddingValues(horizontal = NmDim.SafeH, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SourceChip("Tümü", selected == null) { onSelect(null) } }
        items(names.size) { i -> SourceChip(names[i], selected == names[i]) { onSelect(names[i]) } }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SourceChip(label: String, active: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(NmDim.PillRadius)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                when {
                    focused -> NmColor.Primary
                    active  -> NmColor.PrimarySelected
                    else    -> NmColor.Surface
                }
            )
            .nmFocusRing(focused, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            fontSize = NmType.Label,
            maxLines = 1,
            color = if (focused) NmColor.OnPrimary else NmColor.OnSurface,
            fontWeight = if (active || focused) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

// --------------------------------------------------------------------- Raflar
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ShelfList(
    shelves: List<Shelf>,
    cache: androidx.compose.runtime.snapshots.SnapshotStateMap<String, List<MediaItem>>,
    started: MutableSet<String>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    focusedShelf: Int,
    onShelfFocused: (Int) -> Unit,
    onSelect: (MediaItem) -> Unit,
) {
    // Bu liste her ekrana dönüşte yeniden oluşur; odağı SON kalınan rafa geri ver
    // (yeniden en üste atlamasın). Kullanıcı gezinmeye başlayınca bir daha çalmaz.
    var pendingFocus by remember(shelves.firstOrNull()?.key) { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = NmDim.SafeV + 16.dp),
        verticalArrangement = Arrangement.spacedBy(NmDim.RowGap),
    ) {
        items(shelves.size) { index ->
            ShelfRow(
                shelf = shelves[index],
                cache = cache,
                started = started,
                // Hedef raf boş çıkarsa (kaynak ölü) odak sonraki dolu rafa düşsün.
                autoFocus = pendingFocus && index >= focusedShelf,
                onFocusConsumed = { pendingFocus = false },
                onFocused = { onShelfFocused(index) },
                onSelect = onSelect,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ShelfRow(
    shelf: Shelf,
    cache: androidx.compose.runtime.snapshots.SnapshotStateMap<String, List<MediaItem>>,
    started: MutableSet<String>,
    autoFocus: Boolean,
    onFocusConsumed: () -> Unit,
    onFocused: () -> Unit,
    onSelect: (MediaItem) -> Unit,
) {
    val items = cache[shelf.key]

    // Raf ekrana girdiğinde içeriğini çeker; sonuç önbellekte kalır.
    LaunchedEffect(shelf.key) {
        if (!started.add(shelf.key)) return@LaunchedEffect   // önyükleme zaten aldı
        cache[shelf.key] = fetchShelf(shelf)
    }

    // Boş dönen kategori (ölü/değişmiş kaynak) hiç yer kaplamasın.
    if (items != null && items.isEmpty()) return

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(autoFocus, items) {
        if (autoFocus && !items.isNullOrEmpty()) {
            runCatching { firstFocus.requestFocus() }.onSuccess { onFocusConsumed() }
        }
    }

    Column(Modifier.fillMaxWidth().onFocusChanged { if (it.hasFocus) onFocused() }) {
        Text(
            text = shelf.title,
            fontWeight = FontWeight.SemiBold,
            fontSize = NmType.RowTitle,
            color = NmColor.OnSurface,
            modifier = Modifier.padding(start = NmDim.SafeH, bottom = 2.dp),
        )
        if (items == null) {
            ShelfSkeleton()
        } else {
            LazyRow(
                modifier = Modifier.focusGroup(),
                // Odak büyüteci kartı taşırdığı için dikey nefes payı bırakılır.
                contentPadding = PaddingValues(horizontal = NmDim.SafeH, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(NmDim.CardGap),
            ) {
                itemsIndexed(items) { i, item ->
                    val mod = Modifier
                        .width(NmDim.PosterWidth)
                        .then(if (i == 0) Modifier.focusRequester(firstFocus) else Modifier)
                    BrowsePoster(item, modifier = mod) { onSelect(item) }
                }
            }
        }
    }
}

/** İçerik gelene kadar rafın yerini tutan gri poster iskeleti (liste zıplamasın). */
@Composable
private fun ShelfSkeleton() {
    Row(
        modifier = Modifier.padding(horizontal = NmDim.SafeH, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(NmDim.CardGap),
    ) {
        repeat(4) {
            Box(
                Modifier
                    .width(NmDim.PosterWidth)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(NmDim.CardRadius))
                    .background(NmColor.Surface),
            )
        }
    }
}

// --------------------------------------------------------------- Arama sonucu
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ItemGrid(
    title: String,
    items: List<MediaItem>,
    loading: Boolean,
    onSelect: (MediaItem) -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(items) { if (items.isNotEmpty()) runCatching { firstFocus.requestFocus() } }

    Column(Modifier.fillMaxSize().padding(horizontal = NmDim.SafeH)) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = NmType.RowTitle,
            color = NmColor.OnSurface,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        when {
            loading -> Center("Yükleniyor…")
            items.isEmpty() -> Center("İçerik bulunamadı")
            else -> LazyVerticalGrid(
                modifier = Modifier.focusGroup(),
                columns = GridCells.Adaptive(minSize = NmDim.GridPosterMin),
                contentPadding = PaddingValues(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(NmDim.CardGap),
                verticalArrangement = Arrangement.spacedBy(NmDim.CardGap),
            ) {
                items(items.size) { i ->
                    val mod = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier
                    BrowsePoster(items[i], modifier = mod) { onSelect(items[i]) }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BrowsePoster(item: MediaItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale = nmFocusScale(focused, NmDim.FocusScaleCard, label = "browseScale")
    val shape = RoundedCornerShape(NmDim.CardRadius)
    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .nmScale(scale)
            .zIndex(if (focused) 1f else 0f)
            .clip(shape)
            .background(NmColor.SurfaceHigh)
            .nmFocusRingOnly(focused, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() },
    ) {
        PosterImage(poster = item.poster, title = item.title)
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(54.dp)
                .background(nmBottomScrim),
        )
        Text(
            text = item.title.orEmpty(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontSize = NmType.Caption,
            color = NmColor.OnSurface,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
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
