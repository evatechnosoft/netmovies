package com.evaitec.netmovies.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.evaitec.netmovies.tv.data.MediaItem
import com.evaitec.netmovies.tv.data.proxiedPoster

private val POSTER_WIDTH = 104.dp   // ana sayfa carousel poster boyuyla uyumlu (küçük — focus'ta büyür)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onSelect: (MediaItem) -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    when (val s = state) {
        is HomeState.Loading -> Center("Yükleniyor…")
        is HomeState.Error   -> ErrorWithRetry(s.message, onRetry = vm::load)
        is HomeState.Ready   -> {
            if (s.items.isEmpty()) {
                ErrorWithRetry("İçerik yok", onRetry = vm::load)
            } else {
                CategoryRows(s.items, onSelect)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryRows(items: List<MediaItem>, onSelect: (MediaItem) -> Unit) {
    // Kategoriye göre grupla (web ana sayfadaki yatay raylar gibi). Sıra korunur.
    val groups = remember(items) {
        items.groupBy { it.category?.takeIf { c -> c.isNotBlank() } ?: "Yeni Çıkanlar" }
    }
    // İlk poster karta başlangıç focus'u ver — yoksa D-pad'de hiçbir şey seçilemiyor.
    val firstFocus    = remember { FocusRequester() }
    val firstCategory = groups.keys.firstOrNull()
    LaunchedEffect(firstCategory) {
        runCatching { firstFocus.requestFocus() }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text(
                text = "NetMovies — Yeni Çıkanlar",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 24.dp, bottom = 4.dp),
            )
        }
        groups.forEach { (category, list) ->
            item {
                Text(
                    text = category,
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
                            if (category == firstCategory && index == 0)
                                Modifier.focusRequester(firstFocus)
                            else Modifier
                        PosterCard(item, onClick = { onSelect(item) }, modifier = cardModifier)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PosterCard(item: MediaItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Box + foundation clickable → hem dokunmatik (telefon) hem D-pad (TV) çalışır.
    // D-pad focus → "büyüteç": kart büyür, primary çerçeve/glow, üstte kalır.
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
            // graphicsLayer ile ölçek: layout'u itmez, komşuların üstüne büyür.
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            // Focus'lu kart komşularını kesmesin diye üstte kalsın.
            .zIndex(if (focused) 1f else 0f)
            .clip(shape)
            .background(Color(0xFF241F33))
            .border(
                width = if (focused) 2.5.dp else 0.dp,
                color = if (focused) Color(0xFF8B5CF6) else Color.Transparent,
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() },   // clickable zaten focusable + OK/DPAD_CENTER'ı işler
    ) {
        Box(Modifier.fillMaxSize()) {
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
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(4.dp),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ErrorWithRetry(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
