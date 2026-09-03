package com.evaitec.netmovies.tv.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.evaitec.netmovies.tv.data.proxiedPoster

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
    // Zincir (kaynak → proxy cache → TMDB) sunucuda çözülür: tek URL yeter,
    // istemcide ikinci deneme yoktur. Sunucu da veremezse Coil kendi hata
    // durumunda kalır (gri kutu).
    val model = remember(poster, title) { proxiedPoster(poster, title) }

    AsyncImage(
        model = model,
        contentDescription = title,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}
