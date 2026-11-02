package com.animevostbr.core

import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
  * Undocumented JSON API of animevost.org (see docs/api.md).
  *
  *  GET  /v1/last (page, quantity) — feed; without both params returns a single title,
  *                                   `page` alone gives HTTP 500, quantity is capped at 40
  *  POST /v1/playlist (id)          — episodes: a bare array of {name, hd, std, preview}
  *  POST /v1/info (id)              — title card, same schema as /v1/last
  *  POST /v1/search (name)          — substring search by title; nothing found → HTTP 404
  */
interface AnimeVostApi {

    @GET("v1/last")
    suspend fun last(
        @Query("page") page: Int,
        @Query("quantity") quantity: Int = LAST_PAGE_SIZE
    ): ResponseBody

    @FormUrlEncoded
    @POST("v1/playlist")
    suspend fun playlist(@Field("id") id: String): ResponseBody

    @FormUrlEncoded
    @POST("v1/info")
    suspend fun info(@Field("id") id: String): ResponseBody

    @FormUrlEncoded
    @POST("v1/search")
    suspend fun search(@Field("name") name: String): ResponseBody

    companion object {
        /** The server never returns more than 40. */
        const val LAST_PAGE_SIZE = 40
    }
}

object ApiClient {

    const val UA = "AnimeVostBR/1.0 (Android; client)"

    val ok: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(40, TimeUnit.SECONDS)
            .followRedirects(true)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", UA)
                        .header("Accept", "application/json, text/plain, */*")
                        .build()
                )
            }
            .build()
    }

    val api: AnimeVostApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.animevost.org/")
            .client(ok)
            .build()
            .create(AnimeVostApi::class.java)
    }
}
