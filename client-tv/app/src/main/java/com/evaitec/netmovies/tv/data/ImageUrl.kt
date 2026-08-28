package com.evaitec.netmovies.tv.data

import android.net.Uri
import com.evaitec.netmovies.tv.BuildConfig

// Poster URL'ini stream'in /proxy/image endpoint'inden geçirir (hotlink koruması
// + cache). Web arayüzüyle aynı güvenilirlik. Boş/yerel URL olduğu gibi döner.
fun proxiedPoster(url: String?): String? {
    if (url.isNullOrBlank()) return null
    if (url.startsWith("/") || url.startsWith("data:")) return url
    val base = BuildConfig.BASE_URL.trimEnd('/')
    return "$base/proxy/image?url=" + Uri.encode(url)
}
