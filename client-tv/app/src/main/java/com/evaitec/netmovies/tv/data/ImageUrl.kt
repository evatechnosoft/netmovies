package com.evaitec.netmovies.tv.data

import android.net.Uri

// Poster URL'ini stream'in /proxy/image endpoint'inden geçirir (hotlink koruması
// + cache). Aktif sunucu (local/uzak) ServerResolver'dan alınır → posterler de
// içerikle aynı sunucuya gider. Boş/yerel URL olduğu gibi döner.
fun proxiedPoster(url: String?): String? {
    if (url.isNullOrBlank()) return null
    if (url.startsWith("/") || url.startsWith("data:")) return url
    val base = ServerResolver.activeBaseString()
    return "$base/proxy/image?url=" + Uri.encode(url)
}

// Kaynak posteri yoksa/kırıksa sunucunun TMDB fallback'ine düşer: başlığa göre
// afiş arar ve image.tmdb.org'a 302 döner. Anahtar yoksa 404 → gri kutu kalır.
fun tmdbPoster(title: String?): String? {
    if (title.isNullOrBlank()) return null
    val base = ServerResolver.activeBaseString()
    return "$base/tmdb-poster?title=" + Uri.encode(title)
}
