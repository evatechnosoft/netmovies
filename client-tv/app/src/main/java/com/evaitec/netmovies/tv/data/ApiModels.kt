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
    val subtitles: List<Subtitle> = emptyList(),
)

// KekikStream Subtitle → { "name": "Türkçe", "url": ".../tr.vtt" }
@Serializable
data class Subtitle(
    val name: String = "",
    val url: String = "",
)

// /api/v1/get_all_plugins yanıtı: eklenti listesi + her birinin kategori haritası.
@Serializable
data class PluginsResponse(
    val result: List<PluginInfo> = emptyList(),
)

@Serializable
data class PluginInfo(
    val name: String = "",
    val language: String = "",
    @SerialName("main_url") val mainUrl: String = "",
    val favicon: String? = null,
    val description: String? = null,
    // main_page: { <quote_plus url> : <quote_plus kategori adı> }
    @SerialName("main_page") val mainPage: Map<String, String> = emptyMap(),
)

// /api/v1/get_main_page yanıtı: seçilen kategorinin içerikleri (düz liste).
@Serializable
data class MainPageResponse(
    val result: List<MediaItem> = emptyList(),
)

// /api/v1/load_item yanıtı: dizi detayları ve bölüm listesi.
@Serializable
data class ItemResponse(
    val result: ItemDetails? = null,
)

@Serializable
data class ItemDetails(
    val url: String = "",
    val title: String? = null,
    val poster: String? = null,
    val description: String? = null,
    val episodes: List<EpisodeItem> = emptyList(),
)

@Serializable
data class EpisodeItem(
    val season: Int = 1,
    val episode: Int? = null,
    val title: String? = null,
    val url: String = "",
)
