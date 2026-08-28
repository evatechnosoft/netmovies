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
}
