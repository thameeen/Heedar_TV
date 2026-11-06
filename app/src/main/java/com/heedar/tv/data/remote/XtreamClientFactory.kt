package com.heedar.tv.data.remote

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds a Retrofit stack for one panel. A client is created per login attempt so
 * that changing the panel never reuses a stale base URL.
 */
object XtreamClientFactory {

    private const val CONNECT_TIMEOUT_SECONDS = 20L
    private const val READ_TIMEOUT_SECONDS = 30L

    fun create(baseUrl: String): XtreamApiService {
        val normalised = requireNotNull(UrlNormalizer.normalize(baseUrl)) {
            "Invalid server URL: $baseUrl"
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val gson = GsonBuilder().setLenient(true).create()

        return Retrofit.Builder()
            .baseUrl(normalised)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(XtreamApiService::class.java)
    }
}
