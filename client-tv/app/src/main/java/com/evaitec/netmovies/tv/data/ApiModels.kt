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
    // TMDB puanı; sunucu cache'inde varsa gelir, yoksa null (yıldız çizilmez).
    val rating: Double? = null,
    // TMDB yayın yılı — katalog sıralaması bunu kullanıyor, arama satırı gösteriyor.
    val year: Int? = null,
    // Telefondan "TV'de oynat" ile gelen içerik: onay telefonda (basılı tutma) zaten
    // verildi, TV'de başlangıç paneli bir daha OYNAT beklemesin.
    val autoplay: Boolean = false,
    // Telefon belirli bir bölümü seçtiyse (0 tabanlı sıra); -1 = kayıttan/baştan.
    val episode: Int = -1,
    // Poster rozetleri ("DUB", "ALT", "ORJ") — sunucu yalnız DAHA ÖNCE çözümlenmiş
    // içerik için doldurur (lang_memo.py). Hiç açılmamış kart rozetsiz gelir.
    val lang: List<String> = emptyList(),
    // Arama (`group=1`) satırının zengin alanları; katalogda gelmez.
    @SerialName("episode_count") val episodeCount: Int? = null,
    @SerialName("season_count") val seasonCount: Int? = null,
    val providers: List<ProviderRef> = emptyList(),
    // Canlı kanallarda yayın rehberi (EPG): "şu an ne oynuyor". Rehberde
    // olmayan kanalda null gelir — kart yine çizilir, satır boş kalır.
    val simdi: NowPlaying? = null,
)

// /api/v1/quick_channels → kanal başına yayın rehberi satırı.
@Serializable
data class NowPlaying(
    val program: String = "",
    val ozet: String = "",
    val baslangic: String = "",
    val bitis: String = "",
    val sonraki: String = "",
)

// /api/v1/load_links yanıtı:
// { "with": ..., "result": [ { "name", "url", "referer", "user_agent", "subtitles" } ] }
@Serializable
data class LinksResponse(
    val result: List<StreamLink> = emptyList(),
)

@Serializable
data class StreamLink(
    /** Kaynağı veren sağlayıcı (engine doldurur). Kaynak puanlaması bu adı bildirir. */
    val plugin: String = "",
    val name: String = "",
    val url: String = "",
    val referer: String = "",
    @SerialName("user_agent") val userAgent: String = "",
    val subtitles: List<Subtitle> = emptyList(),
    // Sunucu doldurur (resolve_sources): dil kuralı istemcide tekrarlanmaz.
    val language: LanguageTag? = null,
)

// KekikStream Subtitle → { "name": "Türkçe", "url": ".../tr.vtt" }
@Serializable
data class Subtitle(
    val name: String = "",
    val url: String = "",
)

// /api/v1/markers yanıtı: bölümün açılış/jenerik işaretleri (saniye).
// Bulunamayan işaret null gelir — oynatıcı o özelliği hiç göstermez.
@Serializable
data class MarkersResponse(
    val result: Markers = Markers(),
)

@Serializable
data class Markers(
    @SerialName("intro_start") val introStart: Double? = null,
    @SerialName("intro_end") val introEnd: Double? = null,
    @SerialName("credits_start") val creditsStart: Double? = null,
    val source: String? = null,
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
    // Başlangıç paneli bilgi satırı; kaynak sayı ya da metin gönderebiliyor.
    val year: kotlinx.serialization.json.JsonPrimitive? = null,
    val rating: kotlinx.serialization.json.JsonPrimitive? = null,
    val tags: kotlinx.serialization.json.JsonElement? = null,
    val actors: kotlinx.serialization.json.JsonElement? = null,
    val episodes: List<EpisodeItem> = emptyList(),
) {
    // "Bilim Kurgu, Gizem" — liste ya da metin gelsin, tek satır.
    private fun metin(e: kotlinx.serialization.json.JsonElement?): String = when (e) {
        null -> ""
        is kotlinx.serialization.json.JsonArray -> e.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.joinToString(", ")
        is kotlinx.serialization.json.JsonPrimitive -> e.content
        else -> ""
    }
    val tagsText: String get() = metin(tags)
    val actorsText: String get() = metin(actors)
    val yearText: String get() = year?.content?.takeIf { it != "null" }.orEmpty()
    val ratingText: String get() = rating?.content?.takeIf { it != "null" && it.isNotBlank() }.orEmpty()
}

@Serializable
data class EpisodeItem(
    val season: Int = 1,
    val episode: Int? = null,
    val title: String? = null,
    val url: String = "",
)

// /api/v1/episode_overviews yanıtı: sezonun bölüm özetleri (TMDB).
@Serializable
data class EpisodeOverview(
    val title: String = "",
    val overview: String = "",
    val still: String = "",
)

@Serializable
data class OverviewsResult(
    val season: Int = 1,
    val episodes: Map<String, EpisodeOverview> = emptyMap(),
)

@Serializable
data class OverviewsResponse(val result: OverviewsResult = OverviewsResult())

// /api/v1/resolve_sources yanıtı — oynatma zincirinin sunucudaki tek çıktısı.
@Serializable
data class ResolveResponse(
    val result: ResolveResult? = null,
)

@Serializable
data class ResolveResult(
    val mode: String = "",
    val count: Int = 0,
    val sources: List<StreamLink> = emptyList(),
    val episodes: List<EpisodeItem> = emptyList(),
    val diagnostics: List<Diagnostic> = emptyList(),
)

// Sunucunun teşhis kaydı: hangi sağlayıcı eşleşti, hangisi kaynak vermedi.
// Kaynak raporu ekranı bunu istemci kayıtlarıyla birlikte gösterir.
@Serializable
data class Diagnostic(
    val level: String = "info",
    val stage: String = "",
    val message: String = "",
)

// `/api/v1/episodes_best` yanıtı: en zengin bölüm listesi ve onu veren sağlayıcı.
@Serializable
data class EpisodesBestResult(
    val plugin: String = "",
    @SerialName("encoded_url") val encodedUrl: String = "",
    val episodes: List<EpisodeItem> = emptyList(),
    @SerialName("kaynak_sayisi") val kaynakSayisi: Int = 0,
)

@Serializable
data class EpisodesBestResponse(val result: EpisodesBestResult = EpisodesBestResult())

// Gruplanmış arama satırında aynı içeriği veren sağlayıcılardan biri.
@Serializable
data class ProviderRef(
    val plugin: String = "",
    val url: String = "",
)

// Kaynağın dili sunucuda belirlenir; istemci yalnız etiketi basar.
@Serializable
data class LanguageTag(
    // 0 dublaj · 1 Türkçe altyazı · 2 orijinal dil · 3 bilinmiyor (language.py).
    val rank: Int = 3,
    val label: String = "dil bilinmiyor",
)

// --------------------------------------------------------------- İzleme senkronu
// Sunucu tarafı SQLite (stream/Public/Home/Libs/watch_store.py). Anahtar
// SİTE-AGNOSTİK: `content_key` başlıktan türer, plugin içermez → aynı film başka
// kaynakta bulunsa da kayıt tutar. İstemci key hesaplamaz, başlığı gönderir.
//
// DİKKAT: `content_url` sunucuda HAM tutulur (web böyle yazıyor), oysa MediaItem.url
// quote_plus KODLU. Dönüşüm tek yerde: rawUrl() / encodedUrl().
@Serializable
data class ProgressRow(
    @SerialName("content_key") val contentKey: String = "",
    val plugin: String = "",
    val title: String = "",
    val poster: String = "",
    @SerialName("media_type") val mediaType: String = "",
    @SerialName("content_url") val contentUrl: String = "",
    val episode: String = "",
    @SerialName("position_seconds") val positionSeconds: Double = 0.0,
    @SerialName("duration_seconds") val durationSeconds: Double = 0.0,
)

@Serializable
data class ProgressResponse(val result: ProgressRow? = null)

@Serializable
data class ProgressListResponse(val result: List<ProgressRow> = emptyList())

@Serializable
data class OkResult(
    val ok: Boolean = false,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
    // lists/toggle sonrası yeni durum: true = listede. Menü satırı bunu gösterir,
    // yoksa takip et/bırak kör bir düğmeydi.
    val saved: Boolean = false,
)

@Serializable
data class OkResponse(val result: OkResult = OkResult())

// /api/v1/client_config — istemciye açık yönetim ayarları (salt okunur).
@Serializable
data class ClientConfig(
    @SerialName("adult_providers") val adultProviders: List<String> = emptyList(),
    @SerialName("hidden_providers") val hiddenProviders: List<String> = emptyList(),
    @SerialName("vault_alias") val vaultAlias: String = "Özel Koleksiyon",
    /** Oynatma kalitesi tavanı: "auto" | "1080" | "720" | "480". Yönetim panelinden gelir. */
    @SerialName("default_quality") val defaultQuality: String = "auto",
)

/**
 * Kalite tavanı süreç ömrü boyunca bir kez çekilir: her oynatmada yönetim
 * ayarını sormanın anlamı yok, panel değişince uygulama yeniden açılır.
 */
object OynatmaAyari {
    @Volatile var kaliteTavani: String = "auto"
    @Volatile var okundu: Boolean = false

    /** "1080" → 1920x1080. "auto" ve tanınmayan değer → null (sınır yok). */
    fun tavanBoyutu(): Pair<Int, Int>? = when (kaliteTavani) {
        "1080" -> 1920 to 1080
        "720"  -> 1280 to 720
        "480"  -> 854 to 480
        else   -> null
    }
}

@Serializable
data class ClientConfigResponse(val result: ClientConfig = ClientConfig())

/** Oynatma denemesinin sonucu — kaynak sırasının tek kanıt kaynağı. */
@Serializable
data class SourceEvent(val plugin: String = "", val ok: Boolean = false)

// /api/v1/remote/poll — telefon kumandasından gelen komut (yoksa result null).
// Tek şema, beş tür: play / key / transport / text / nav. Sunucu (Routers/remote.py)
// alanları doğrulayıp yollar; burada tanınmayan tür sessizce yok sayılır — eski
// APK'nın yeni bir komut türüyle çökmemesi için alanların hepsi varsayılanlı.
@Serializable
data class RemoteCommand(
    val type: String = "play",
    val plugin: String = "",
    val url: String = "",
    val title: String = "",
    val poster: String = "",
    val key: String = "",
    val action: String = "",
    val value: Float = 0f,
    val screen: String = "",
    val text: String = "",
    val submit: Boolean = false,
    // play: telefonun seçtiği bölüm sırası (0 tabanlı), yoksa -1.
    val episode: Int = -1,
)

@Serializable
data class RemoteCommandResponse(val result: RemoteCommand? = null)

// /api/v1/prefs — serbest anahtar/değer. TV yalnız kendi anahtarlarını okur;
// kumandanın yazdıkları (rc_*) burada da görünür, TV onları yok sayar.
@Serializable
data class PrefsResponse(val result: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap())

// /api/v1/following — takip edilen diziler + TMDB yayın takvimi, Türkçe/yabancı ayrık.
@Serializable
data class FollowedShow(
    @SerialName("content_key") val contentKey: String = "",
    val plugin: String = "",
    val title: String = "",
    val poster: String = "",
    @SerialName("content_url") val contentUrl: String = "",
    val status: String = "",
    @SerialName("next_date") val nextDate: String = "",
    @SerialName("next_season") val nextSeason: Int = 0,
    @SerialName("next_episode") val nextEpisode: Int = 0,
    @SerialName("next_name") val nextName: String = "",
)

@Serializable
data class FollowingGroups(
    val turkish: List<FollowedShow> = emptyList(),
    val foreign: List<FollowedShow> = emptyList(),
)

@Serializable
data class FollowingResponse(val result: FollowingGroups = FollowingGroups())

// /api/v1/quick_channels — canlı kanal listesi (M3U). `url` HAM gelir; MediaItem'a
// çevirirken encodedUrl() ile kodlanır, oynatma zinciri kodlu bekliyor.
@Serializable
data class ChannelsResponse(val result: List<MediaItem> = emptyList())

// /api/v1/app_update yanıtı — YEREL OTA.
// APK evdeki sunucuda duruyorken güncellemeyi GitHub'dan indirmek gereksiz:
// internet kesikse güncelleme hiç gelmiyor, GitHub'ın saatlik 60 istek sınırı
// da ev ağının tamamını kilitliyor. `result` null ise GitHub'a düşülür.
@Serializable
data class AppUpdateResponse(
    val result: AppUpdateInfo? = null,
)

@Serializable
data class AppUpdateInfo(
    val tag: String = "",
    val url: String = "",
    val size: Long = 0,
    val name: String = "",
)

// /api/v1/agenda — yayınlanacak bölümler ve vizyona girecek filmler, gün gün.
// Sunucu tarihe göre sıralayıp gruplar (TMDB, günde bir tazelenir); istemci çizer.
@Serializable
data class AgendaItem(
    val tur: String = "",          // "dizi" | "film"
    val baslik: String = "",
    val tarih: String = "",
    val poster: String = "",
    val bolum: String = "",        // "2. sezon 16. bölüm" | "Vizyon"
    val ozet: String = "",
    val puan: Double = 0.0,
)

@Serializable
data class AgendaDay(
    val tarih: String = "",
    val ogeler: List<AgendaItem> = emptyList(),
)

@Serializable
data class AgendaResult(
    val view: String = "week",
    val toplam: Int = 0,
    val gunler: List<AgendaDay> = emptyList(),
)

@Serializable
data class AgendaResponse(val result: AgendaResult = AgendaResult())
