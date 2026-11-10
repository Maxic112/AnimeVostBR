package com.animevostbr.core

import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.IOException
import retrofit2.Retrofit
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
  * Undocumented JSON API of AnimeVost (see docs/api.md), on api.animetop.info and api.animevost.org.
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

    /**
      * The same API answers on two domains with different servers (see docs/api.md). Some operators
      * and ISPs block animevost.org, so the animetop.info one goes first.
      */
    val API_HOSTS = listOf("api.animetop.info", "api.animevost.org")

    /** Host of the last good API answer; tried first next time. */
    @Volatile
    var apiHost: String = API_HOSTS.first()
        private set

    val ok: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
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

    /** API calls only: on a dead or blocked host, the same request goes to the other one. */
    private val apiHttp: OkHttpClient by lazy {
        ok.newBuilder().addInterceptor { chain ->
            val request = chain.request()
            val hosts = listOf(apiHost) + API_HOSTS.filter { it != apiHost }
            var lastError: IOException? = null
            for (host in hosts) {
                val response = try {
                    chain.proceed(request.newBuilder().url(request.url.newBuilder().host(host).build()).build())
                } catch (e: IOException) {
                    lastError = e
                    continue
                }
                if (isApiAnswer(response)) {
                    apiHost = host
                    return@addInterceptor response
                }
                lastError = IOException("Сервер $host ответил не как API (HTTP ${response.code})")
                response.close()
            }
            throw lastError ?: IOException("API недоступен")
        }.build()
    }

    /**
      * Every API answer, a 404 "nothing found" included, is JSON. A block page of an ISP comes as HTML,
      * and a 5xx means the server is down: both count as a failed host.
      */
    internal fun isApiAnswer(code: Int, contentType: String?): Boolean =
        code < 500 && contentType?.contains("json", ignoreCase = true) == true

    private fun isApiAnswer(response: Response): Boolean = isApiAnswer(response.code, response.header("Content-Type"))

    val api: AnimeVostApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://${API_HOSTS.first()}/")
            .client(apiHttp)
            .build()
            .create(AnimeVostApi::class.java)
    }
}
