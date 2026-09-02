package com.evaitec.netmovies.tv.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.evaitec.netmovies.tv.data.proxiedPoster
import com.evaitec.netmovies.tv.data.tmdbPoster

/**
 * Poster görseli — kaynak afişi yoksa ya da yüklenemezse (ölü CDN, hotlink,
 * 502) sunucunun `/tmdb-poster?title=` ucuna düşer. Web arayüzünde zaten olan
 * davranış; TV'de eksikti, kırık posterde gri kutu kalıyordu.
 */
@Composable
fun PosterImage(
    poster: String?,
    title: String?,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val primary = remember(poster) { proxiedPoster(poster) }
    val fallback = remember(title) { tmdbPoster(title) }
    // Kaynak posteri hiç yoksa doğrudan TMDB ile başla.
    var failed by remember(primary, fallback) { mutableStateOf(primary == null) }

    AsyncImage(
        model = if (failed) fallback else primary,
        contentDescription = title,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        onError = { if (!failed) failed = true },
    )
}
