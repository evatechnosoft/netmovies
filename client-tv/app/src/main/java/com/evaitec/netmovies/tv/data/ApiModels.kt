package com.evaitec.netmovies.tv.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Engine/stream /api/v1/aggregate_new yanıtı:
// { "with": ..., "schema": ..., "result": { "type", "count", "items": [ ... ] } }
@Serializable
data class AggregateResponse(
    val result: AggregateResult? = null,
)

@Serializable
data class AggregateResult(
    val type: String = "",
    val count: Int = 0,
    val items: List<MediaItem> = emptyList(),
)

@Serializable
data class MediaItem(
    val plugin: String = "",
    val title: String? = null,
    val url: String = "",          // quote_plus ile kodlanmış içerik URL'i (encoded_url olarak geri gönderilir)
    val poster: String? = null,
    val category: String? = null,
)

// /api/v1/load_links yanıtı:
// { "with": ..., "result": [ { "name", "url", "referer", "user_agent", "subtitles" } ] }
@Serializable
data class LinksResponse(
    val result: List<StreamLink> = emptyList(),
)

@Serializable
data class StreamLink(
    val name: String = "",
    val url: String = "",
    val referer: String = "",
    @SerialName("user_agent") val userAgent: String = "",
)
