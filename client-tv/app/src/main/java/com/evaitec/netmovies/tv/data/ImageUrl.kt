package com.evaitec.netmovies.tv.data

import android.net.Uri

// Poster URL üreteci — sunucudaki Jinja `poster(url, title)` ve web'deki
// posterUrl() ile AYNI sözleşme. Zincirin tamamı /proxy/image içinde çalışır:
// kaynak → proxy cache → TMDB (başlıkla) → placeholder. İstemcinin ayrı bir
// fallback denemesi kurmasına gerek yoktur.
// Aktif sunucu (local/uzak) ServerResolver'dan alınır → posterler de içerikle
// aynı sunucuya gider. Boş/yerel URL olduğu gibi döner.
fun proxiedPoster(url: String?, title: String? = null): String? {
    if (!url.isNullOrBlank() && (url.startsWith("/") || url.startsWith("data:"))) return url
    if (url.isNullOrBlank() && title.isNullOrBlank()) return null

    val base = ServerResolver.activeBaseString()
    val query = StringBuilder("url=").append(Uri.encode(url ?: ""))
    if (!title.isNullOrBlank()) query.append("&title=").append(Uri.encode(title))
    return "$base/proxy/image?$query"
}

// Doğrudan TMDB fallback'i — poster hattı dışında (ör. yalnız başlık bilinen
// yerler) gerekirse kullanılır.
fun tmdbPoster(title: String?): String? {
    if (title.isNullOrBlank()) return null
    val base = ServerResolver.activeBaseString()
    return "$base/tmdb-poster?title=" + Uri.encode(title)
}
