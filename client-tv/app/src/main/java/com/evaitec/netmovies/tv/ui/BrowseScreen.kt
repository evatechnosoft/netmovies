package com.evaitec.netmovies.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.evaitec.netmovies.tv.data.MediaItem
import com.evaitec.netmovies.tv.data.Network
import com.evaitec.netmovies.tv.data.PluginInfo
import com.evaitec.netmovies.tv.data.proxiedPoster
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

private fun decode(s: String): String =
    runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

// Eklenti/kategori tarayıcı: eklenti → kategori seç → içerikler inline grid → poster seç → oynat.
// Tek ekran, derin sayfa nav yok (Geri: grid → kategori listesi → çık).
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

    val isAdultPlugin = { name: String ->
        val n = name.lowercase()
        n.contains("porner") || n.contains("porn") || n.contains("spank") ||
        n.contains("hamster") || n.contains("oxax") || n.contains("maza")
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

    // Seçilen kategori / arama sonucu görünümü (null → kategori listesi).
    var catItems by remember { mutableStateOf<List<MediaItem>?>(null) }
    var catTitle by remember { mutableStateOf("") }
    var catLoading by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    fun openCategory(plugin: PluginInfo, encUrl: String, encCat: String) {
        catTitle = "${plugin.name} · ${decode(encCat)}"
        catItems = emptyList()
        catLoading = true
        scope.launch {
            catItems = withTimeoutOrNull(20_000) {
                try {
                    Network.api.getMainPage(plugin.name, 1, encUrl, encCat).result
                        .map { it.copy(plugin = plugin.name, category = decode(encCat)) }
                } catch (e: Exception) {
                    emptyList()
                }
            } ?: emptyList()
            catLoading = false
        }
    }

    LaunchedEffect(Unit) {
        try {
            val list = Network.api.getAllPlugins().result
            rawPlugins = list
            loading = false
            if (vaultMode) {
                val adultPlg = list.firstOrNull { isAdultPlugin(it.name) }
                if (adultPlg != null && adultPlg.mainPage.isNotEmpty()) {
                    val firstEntry = adultPlg.mainPage.entries.first()
                    openCategory(adultPlg, firstEntry.key, firstEntry.value)
                }
            }
        } catch (e: Exception) {
            error = e.message ?: "Eklentiler yüklenemedi"
            loading = false
        }
    }

    BackHandler(enabled = true) {
        if (catItems != null && !vaultMode) catItems = null else onBack()
    }

    // Tüm eklentilerde paralel ara, birleştir.
    fun doSearch(q: String) {
        val query2 = q.trim()
        if (query2.isEmpty()) return
        catTitle = "Arama: $query2"
        catItems = emptyList()
        catLoading = true
        scope.launch {
            val names = plugins.map { it.name }
            catItems = if (names.isEmpty()) emptyList() else coroutineScope {
                names.map { n ->
                    async {
                        // Yavaş kaynak (12s) tüm aramayı kilitlemesin → o kaynak boş sayılır.
                        withTimeoutOrNull(12_000) {
                            runCatching { Network.api.search(n, query2).result.map { it.copy(plugin = n) } }
                                .getOrDefault(emptyList())
                        } ?: emptyList()
                    }
                }.awaitAll().flatten()
            }
            catLoading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        SearchBar(query = query, onQueryChange = { query = it }, onSearch = { doSearch(query) })
        Box(Modifier.fillMaxSize()) {
            when {
                catItems != null -> ItemGrid(
                    title = catTitle,
                    items = catItems!!,
                    loading = catLoading,
                    onSelect = onSelect,
                )
                loading -> Center("Eklentiler yükleniyor…")
                error != null -> Center(error!!)
                else -> CategoryList(plugins, onOpen = ::openCategory)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(NmDim.PillRadius)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = NmDim.SafeH, end = NmDim.SafeH, top = NmDim.SafeV, bottom = 8.dp)
            .clip(shape)
            .background(if (focused) NmColor.SurfaceHigh else NmColor.Surface)
            .nmFocusRing(focused, shape)
            .padding(horizontal = 20.dp, vertical = 14.dp),
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
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        text = "🔎  Ara — tüm kaynaklarda…",
                        color = NmColor.OnSurfaceFaint,
                        fontSize = NmType.Body,
                    )
                }
                inner()
            },
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryList(
    plugins: List<PluginInfo>,
    onOpen: (PluginInfo, String, String) -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(plugins) { runCatching { firstFocus.requestFocus() } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().focusGroup(),
        contentPadding = PaddingValues(horizontal = NmDim.SafeH, vertical = NmDim.SafeV),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text(
                text = "Gözat — Eklenti & Kategoriler",
                fontWeight = FontWeight.Bold,
                fontSize = NmType.ScreenTitle,
                color = NmColor.OnSurface,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        plugins.forEachIndexed { pIndex, plugin ->
            item {
                Text(
                    text = plugin.name,
                    color = NmColor.Primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = NmType.RowTitle,
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                )
            }
            val entries = plugin.mainPage.entries.toList()
            itemsIndexed(entries, plugin, pIndex, firstFocus, onOpen)
        }
    }
}

// LazyListScope içinde kategori satırlarını üretir (ilk plugin'in ilk satırına focus).
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexed(
    entries: List<Map.Entry<String, String>>,
    plugin: PluginInfo,
    pIndex: Int,
    firstFocus: FocusRequester,
    onOpen: (PluginInfo, String, String) -> Unit,
) {
    items(entries.size) { i ->
        val (encUrl, encCat) = entries[i].toPair()
        val mod = if (pIndex == 0 && i == 0) Modifier.focusRequester(firstFocus) else Modifier
        CategoryRow(label = decode(encCat), modifier = mod) { onOpen(plugin, encUrl, encCat) }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryRow(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(NmDim.RowRadius)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) NmColor.Primary else NmColor.Surface)
            .nmFocusRing(focused, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 13.dp),
    ) {
        Text(
            text = label,
            fontSize = NmType.Body,
            color = if (focused) NmColor.OnPrimary else NmColor.OnSurface,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

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

    Column(Modifier.fillMaxSize().padding(horizontal = NmDim.SafeH, vertical = NmDim.SafeV)) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = NmType.ScreenTitle,
            color = NmColor.OnSurface,
            modifier = Modifier.padding(bottom = 12.dp),
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
        AsyncImage(
            model = proxiedPoster(item.poster),
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
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
