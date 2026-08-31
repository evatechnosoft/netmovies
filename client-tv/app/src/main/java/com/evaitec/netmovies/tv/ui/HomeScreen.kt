package com.evaitec.netmovies.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.evaitec.netmovies.tv.HomeState
import com.evaitec.netmovies.tv.HomeViewModel
import com.evaitec.netmovies.tv.data.Library
import com.evaitec.netmovies.tv.data.MediaItem
import com.evaitec.netmovies.tv.data.proxiedPoster

private val POSTER_WIDTH = 104.dp   // ana sayfa carousel poster boyuyla uyumlu (küçük — focus'ta büyür)

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TvTopBarButton(
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1.0f, tween(150), label = "btnScale")

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))
            .background(if (isFocused) Color(0xFF8B5CF6) else Color(0xFF241F33))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0x338B5CF6),
                shape = RoundedCornerShape(20.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = if (isFocused) Color.White else Color(0xFFEDEDF2),
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onSelect: (MediaItem) -> Unit,
    onOpenBrowse: () -> Unit,
    onOpenKeyMap: () -> Unit,
    onOpenVault: () -> Unit,
    showVault: Boolean,
    onToggleVault: () -> Unit,
    onToggleMouseMode: () -> Unit = {},
    library: Library,
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
                CategoryRows(emptyList(), library, onSelect, onOpenBrowse, onOpenKeyMap, onOpenVault, showVault, onToggleVault, onToggleMouseMode)
            }
        }
        is HomeState.Ready   -> {
            if (s.items.isEmpty() && library.favorites.isEmpty() && library.watched.isEmpty()) {
                ErrorWithRetry("İçerik yok", onRetry = vm::load)
            } else {
                CategoryRows(s.items, library, onSelect, onOpenBrowse, onOpenKeyMap, onOpenVault, showVault, onToggleVault, onToggleMouseMode)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryRows(
    items: List<MediaItem>,
    library: Library,
    onSelect: (MediaItem) -> Unit,
    onOpenBrowse: () -> Unit,
    onOpenKeyMap: () -> Unit,
    onOpenVault: () -> Unit,
    showVault: Boolean,
    onToggleVault: () -> Unit,
    onToggleMouseMode: () -> Unit,
) {
    // Kategoriye göre grupla (web ana sayfadaki yatay raylar gibi). Sıra korunur.
    val groups = remember(items) {
        items.groupBy { it.category?.takeIf { c -> c.isNotBlank() } ?: "Yeni Çıkanlar" }
    }
    // Kitaplık satırları en üstte (İzlenenler + Favoriler), sonra agregasyon kategorileri.
    val sections = buildList {
        if (library.watched.isNotEmpty()) add("İzlenenler" to library.watched.toList())
        if (library.favorites.isNotEmpty()) add("Favoriler" to library.favorites.toList())
        groups.forEach { add(it.key to it.value) }
    }

    // Poster uzun-bas menüsü.
    var menuItem by remember { mutableStateOf<MediaItem?>(null) }

    // İlk poster karta başlangıç focus'u ver — yoksa D-pad'de hiçbir şey seçilemiyor.
    val firstFocus = remember { FocusRequester() }
    val firstKey = sections.firstOrNull()?.first
    LaunchedEffect(firstKey) {
        runCatching { firstFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "NetMovies",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = Color(0xFF8B5CF6),
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    TvTopBarButton("🔎 Gözat", onClick = onOpenBrowse, onLongClick = onToggleVault)
                    TvTopBarButton("⚙ Buton Eşleme", onClick = onOpenKeyMap)
                    TvTopBarButton("🖱 Fare Modu", onClick = onToggleMouseMode)
                    if (showVault) {
                        TvTopBarButton("🔒 Özel Koleksiyon", onClick = onOpenVault, onLongClick = onToggleVault)
                    }
                }
            }
            sections.forEachIndexed { sIndex, (title, list) ->
                item {
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 24.dp, top = 6.dp, bottom = 6.dp),
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        itemsIndexed(list) { index, item ->
                            val cardModifier =
                                if (sIndex == 0 && index == 0) Modifier.focusRequester(firstFocus)
                                else Modifier
                            PosterCard(
                                item = item,
                                isFavorite = library.isFavorite(item),
                                onClick = { onSelect(item) },
                                onLongPress = { menuItem = item },
                                modifier = cardModifier,
                            )
                        }
                    }
                }
            }
        }

        menuItem?.let { item ->
            PosterMenu(
                item = item,
                isFavorite = library.isFavorite(item),
                onPlay = { menuItem = null; onSelect(item) },
                onToggleFavorite = { library.toggleFavorite(item); menuItem = null },
                onClose = { menuItem = null },
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun PosterCard(
    item: MediaItem,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // D-pad focus → "büyüteç": kart büyür, primary çerçeve/glow, üstte kalır.
    // combinedClickable → OK tek bas = oynat, OK basılı tut = menü (favori vb.).
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.18f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "posterScale",
    )
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .width(POSTER_WIDTH)
            .aspectRatio(2f / 3f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .zIndex(if (focused) 1f else 0f)
            .clip(shape)
            .background(Color(0xFF241F33))
            .border(
                width = if (focused) 2.5.dp else 0.dp,
                color = if (focused) Color(0xFF8B5CF6) else Color.Transparent,
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = proxiedPoster(item.poster),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (isFavorite) {
                Text(
                    text = "★",
                    color = Color(0xFFFFC107),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                )
            }
            Text(
                text = item.title.orEmpty(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(4.dp),
            )
        }
    }
}

// Poster uzun-bas menüsü — sayfa açmadan hızlı aksiyonlar.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PosterMenu(
    item: MediaItem,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(Color(0xCC000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xF20F0F14))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.title ?: "Seçenekler",
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            MenuRow("▶  Oynat", onPlay)
            MenuRow(if (isFavorite) "★  Favorilerden çıkar" else "☆  Favorilere ekle", onToggleFavorite)
            MenuRow("Kapat", onClose)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x00000000))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(label)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ErrorWithRetry(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message)
            androidx.compose.foundation.layout.Spacer(Modifier.padding(6.dp))
            TouchButton("Tekrar dene", onRetry)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Center(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text)
    }
}
