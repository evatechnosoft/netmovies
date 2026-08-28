package com.evaitec.netmovies.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.evaitec.netmovies.tv.HomeState
import com.evaitec.netmovies.tv.HomeViewModel
import com.evaitec.netmovies.tv.data.MediaItem
import com.evaitec.netmovies.tv.data.proxiedPoster

private val POSTER_WIDTH = 118.dp   // ana sayfa carousel poster boyuyla uyumlu (küçük)

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
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(list) { item -> PosterCard(item, onClick = { onSelect(item) }) }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PosterCard(item: MediaItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(POSTER_WIDTH).aspectRatio(2f / 3f),
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
            Button(onClick = onRetry) { Text("Tekrar dene") }
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
