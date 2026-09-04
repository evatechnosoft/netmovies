package com.evaitec.netmovies.tv.data

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface NetMoviesApi {

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

    // Dizi detayları ve bölüm listesi (dizi linki seçildiğinde bölümleri listelemek için)
    @GET("api/v1/load_item")
    suspend fun loadItem(
        @Query("plugin") plugin: String,
        @Query("encoded_url", encoded = true) url: String,
    ): ItemResponse
}
