package com.evaitec.netmovies.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.evaitec.netmovies.tv.data.MediaItem
import com.evaitec.netmovies.tv.data.Network
import com.evaitec.netmovies.tv.data.PluginInfo
import com.evaitec.netmovies.tv.data.proxiedPoster
import kotlinx.coroutines.launch
import java.net.URLDecoder

private fun decode(s: String): String =
    runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

// Eklenti/kategori tarayıcı: eklenti → kategori seç → içerikler inline grid → poster seç → oynat.
// Tek ekran, derin sayfa nav yok (Geri: grid → kategori listesi → çık).
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BrowseScreen(onSelect: (MediaItem) -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var plugins by remember { mutableStateOf<List<PluginInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Seçilen kategori görünümü (null → kategori listesi).
    var catItems by remember { mutableStateOf<List<MediaItem>?>(null) }
    var catTitle by remember { mutableStateOf("") }
    var catLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            plugins = Network.api.getAllPlugins().result
            loading = false
        } catch (e: Exception) {
            error = e.message ?: "Eklentiler yüklenemedi"
            loading = false
        }
    }

    BackHandler(enabled = true) {
        if (catItems != null) catItems = null else onBack()
    }

    fun openCategory(plugin: PluginInfo, encUrl: String, encCat: String) {
        catTitle = "${plugin.name} · ${decode(encCat)}"
        catItems = emptyList()
        catLoading = true
        scope.launch {
            catItems = try {
                Network.api.getMainPage(plugin.name, 1, encUrl, encCat).result
                    .map { it.copy(plugin = plugin.name, category = decode(encCat)) }
            } catch (e: Exception) {
                emptyList()
            }
            catLoading = false
        }
    }

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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryList(
    plugins: List<PluginInfo>,
    onOpen: (PluginInfo, String, String) -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(plugins) { runCatching { firstFocus.requestFocus() } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Text("Gözat — Eklenti & Kategoriler", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
        }
        plugins.forEachIndexed { pIndex, plugin ->
            item {
                Text(
                    text = plugin.name,
                    color = Color(0xFF8B5CF6),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color(0x338B5CF6) else Color(0xFF1A1726))
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(label, fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal)
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

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
        when {
            loading -> Center("Yükleniyor…")
            items.isEmpty() -> Center("İçerik bulunamadı")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
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
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.12f else 1f,
        animationSpec = tween(150),
        label = "browseScale",
    )
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(Color(0xFF241F33))
            .border(
                width = if (focused) 2.5.dp else 0.dp,
                color = if (focused) Color(0xFF8B5CF6) else Color.Transparent,
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() },
    ) {
        AsyncImage(
            model = proxiedPoster(item.poster),
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            text = item.title.orEmpty(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(4.dp),
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Center(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text) }
}
