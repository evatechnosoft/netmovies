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
}
