package com.evaitec.netmovies.tv.data

import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface NetMoviesApi {

    // Yerel OTA: sunucuda APK varsa sürümü ve LAN indirme adresi.
    @GET("api/v1/app_update")
    suspend fun appUpdate(): AppUpdateResponse

    @GET("api/v1/aggregate_new")
    suspend fun aggregateNew(
        @Query("type") type: String = "movie",
        @Query("page") page: Int = 1,
    ): AggregateResponse

    // encoded = true: item.url zaten quote_plus ile kodlanmış geliyor; Retrofit yeniden
    // kodlamasın (web akışı da bu değeri olduğu gibi query'de geçiriyor). Oynatma
    // uçtan uca test edilince bu varsayım doğrulanmalı — çift-kodlama sorunu çıkarsa
    // encoded=false'a çevir veya URLDecoder ile bir tur çöz.
    // Oynatma kaynağı zinciri TEK uçta: seçili sağlayıcı → bölüm çözme →
    // alternatif sağlayıcılar → dil sıralaması. İstemci yalnız tüketir.
    // mode=fast: sadece seçili sağlayıcı (ilk oynatma beklemesin)
    // mode=full: alternatifler dahil (oynarken arka planda çağrılır)
    @GET("api/v1/resolve_sources")
    suspend fun resolveSources(
        @Query("plugin") plugin: String,
        @Query("encoded_url", encoded = true) encodedUrl: String,
        @Query("title") title: String? = null,
        @Query("episode") episode: Int = 0,
        @Query("mode") mode: String = "full",
    ): ResolveResponse

    @GET("api/v1/load_links")
    suspend fun loadLinks(
        @Query("plugin") plugin: String,
        @Query("encoded_url", encoded = true) encodedUrl: String,
    ): LinksResponse

    // Eklenti/kategori tarayıcı: tüm eklentiler + kategori haritaları.
    @GET("api/v1/get_all_plugins")
    suspend fun getAllPlugins(): PluginsResponse

    // Seçilen kategorinin içerikleri. encoded_url/encoded_category zaten quote_plus
    // kodlu (get_all_plugins'ten geliyor) → Retrofit yeniden kodlamasın.
    @GET("api/v1/get_main_page")
    suspend fun getMainPage(
        @Query("plugin") plugin: String,
        @Query("page") page: Int = 1,
        @Query("encoded_url", encoded = true) encodedUrl: String,
        @Query("encoded_category", encoded = true) encodedCategory: String,
    ): MainPageResponse

    // Tek eklentide arama. Gözat çoklu eklentide paralel çağırıp birleştirir.
    @GET("api/v1/search")
    suspend fun search(
        @Query("plugin") plugin: String,
        @Query("query") query: String,
    ): MainPageResponse

    // ------------------------------------------------------------ İzleme senkronu
    // Sunucu SQLite'ta tutar → TV, telefon ve web aynı listeyi görür.
    // POST'lar gövdesiz: stream middleware (Core/Modules/_istek.py) ÖNCE query
    // params'a bakar, JSON/form'a sonra düşer.
    @GET("api/v1/continue_watching")
    suspend fun continueWatching(@Query("limit") limit: Int = 20): ProgressListResponse

    @GET("api/v1/progress")
    suspend fun getProgress(
        @Query("title") title: String,
        @Query("media_type") mediaType: String = "",
    ): ProgressResponse

    @POST("api/v1/progress")
    suspend fun saveProgress(
        @Query("title") title: String,
        @Query("plugin") plugin: String,
        @Query("poster") poster: String = "",
        @Query("media_type") mediaType: String = "",
        @Query("content_url") contentUrl: String = "",
        @Query("episode") episode: String = "",
        @Query("position_seconds") positionSeconds: Double = 0.0,
        @Query("duration_seconds") durationSeconds: Double = 0.0,
    ): OkResponse

    @GET("api/v1/favorites")
    suspend fun favorites(): ProgressListResponse

    // İdempotent ekleme — yerelde birikmiş favorileri sunucuya taşırken kullanılır
    // (toggle kullanılsa zaten kayıtlı olanı SİLERDİ).
    @POST("api/v1/favorites")
    suspend fun addFavorite(
        @Query("title") title: String,
        @Query("plugin") plugin: String,
        @Query("poster") poster: String = "",
        @Query("media_type") mediaType: String = "",
        @Query("content_url") contentUrl: String = "",
    ): OkResponse

    @POST("api/v1/favorites/toggle")
    suspend fun toggleFavorite(
        @Query("title") title: String,
        @Query("plugin") plugin: String,
        @Query("poster") poster: String = "",
        @Query("media_type") mediaType: String = "",
        @Query("content_url") contentUrl: String = "",
    ): OkResponse

    // Merkezi ayarlar — gizli/yetişkin kaynak listesi tek yerden (web /admin).
    // `/api/admin/config` DEĞİL: orası ADMIN_PASS ile korunuyor, istemci parolayı
    // taşımadığı için 401 alıp sessizce yerleşik listeye düşüyordu. Bu uç salt-okunur
    // ve yalnız istemcinin ihtiyacı olan alanları verir.
    @GET("api/v1/client_config")
    suspend fun clientConfig(): ClientConfigResponse

    // "TV'de oynat": telefon komutu bırakır, TV yoklayıp açar. Yansıtma değil —
    // akışı yine TV çözer (kalite/kaynak zinciri TV'de kalır).
    // `wait`: sunucu komut gelene kadar bağlantıyı açık tutar (uzun-yoklama).
    // D-pad'in kumanda gibi hissettirmesi buna bağlı — 4 sn'lik turlarla her tuş
    // ortalama iki saniye gecikiyordu. Sunucu tavanı 25 sn (`remote.py: _MAX_WAIT`).
    @GET("api/v1/remote/poll")
    suspend fun remotePoll(@Query("wait") wait: Int = 0): RemoteCommandResponse

    // Telefon/tablet bu uygulamadan gönderir; televizyon yoklayıp açar.
    @POST("api/v1/remote/play")
    suspend fun remotePlay(
        @Query("plugin") plugin: String,
        @Query("url") url: String,
        @Query("title") title: String = "",
        @Query("poster") poster: String = "",
    ): OkResponse

    // Televizyon oynatırken birkaç saniyede bir bildirir: telefon kumandasındaki
    // "şu an oynayan" şeridi (ad, geçen/kalan süre, ilerleme) buradan beslenir.
    @POST("api/v1/remote/state")
    suspend fun remoteState(
        @Query("title") title: String,
        @Query("position") position: Double,
        @Query("duration") duration: Double,
        @Query("playing") playing: Boolean,
        @Query("plugin") plugin: String = "",
        @Query("url") url: String = "",
        @Query("poster") poster: String = "",
    ): OkResponse

    // Kişisel ayarlar (buton eşleme vb.) — cihazda değil sunucuda. Uygulamayı
    // yeniden kurmak ya da başka bir TV'den girmek ayarları sıfırlamasın.
    @GET("api/v1/prefs")
    suspend fun prefsGet(): PrefsResponse

    @POST("api/v1/prefs")
    suspend fun prefsPost(@Body body: Map<String, String>): OkResponse

    // Canlı kanallar — tek uç, 170+ kanal (M3U listeleri).
    @GET("api/v1/quick_channels")
    suspend fun quickChannels(): ChannelsResponse

    // Takip listesi + yayın takvimi (sonraki bölüm günü sunucuda TMDB'den gelir).
    @GET("api/v1/following")
    suspend fun following(): FollowingResponse

    @POST("api/v1/lists/toggle")
    suspend fun toggleList(
        @Query("list_name") listName: String,
        @Query("title") title: String,
        @Query("plugin") plugin: String,
        @Query("poster") poster: String = "",
        @Query("media_type") mediaType: String = "serie",
        @Query("content_url") contentUrl: String = "",
    ): OkResponse

    // Yönetim paneli ayarları. Ham JsonObject: sunucu POST edilen gövdeyi olduğu gibi
    // yazıyor, kısmi gövde göndermek featured/custom_repos gibi alanları silerdi.
    // Tam config okunup yalnız ilgili alan değiştirilir.
    @GET("api/admin/config")
    suspend fun adminConfig(): JsonObject

    @POST("api/admin/config")
    suspend fun saveAdminConfig(@Body body: JsonObject): JsonObject

    // Dizi detayları ve bölüm listesi (dizi linki seçildiğinde bölümleri listelemek için)
    @GET("api/v1/load_item")
    suspend fun loadItem(
        @Query("plugin") plugin: String,
        @Query("encoded_url", encoded = true) url: String,
    ): ItemResponse
}
