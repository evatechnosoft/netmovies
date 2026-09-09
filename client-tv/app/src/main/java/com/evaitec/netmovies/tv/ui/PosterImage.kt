package com.evaitec.netmovies.tv.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.compose.AsyncImage
import com.evaitec.netmovies.tv.data.Network
import com.evaitec.netmovies.tv.data.proxiedPoster

// Tek ImageLoader: pinli DNS'li OkHttp ile (bkz. Network.imageClient). Her
// composable'da yeni loader kurmak bellek cache'ini de böler.
@Volatile private var posterLoader: ImageLoader? = null

private fun posterLoader(context: android.content.Context): ImageLoader =
    posterLoader ?: synchronized(Network) {
        posterLoader ?: ImageLoader.Builder(context.applicationContext)
            .okHttpClient(Network.imageClient)
            .build()
            .also { posterLoader = it }
    }

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
        imageLoader = posterLoader(LocalContext.current),
        contentDescription = title,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}
