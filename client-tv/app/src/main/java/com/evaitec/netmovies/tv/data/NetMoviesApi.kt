package com.evaitec.netmovies.tv.data

import retrofit2.http.GET
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

    // Dizi detayları ve bölüm listesi (dizi linki seçildiğinde bölümleri listelemek için)
    @GET("api/v1/load_item")
    suspend fun loadItem(
        @Query("plugin") plugin: String,
        @Query("encoded_url", encoded = true) url: String,
    ): ItemResponse
}
