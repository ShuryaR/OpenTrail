package com.example.data.remote

import com.example.data.local.AuthMode
import com.example.data.local.AuthSession
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object FrappeClientFactory {

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    fun buildOkHttpClient(session: AuthSession): OkHttpClient {
        val authInterceptor = Interceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
                .header("Accept", "application/json")

            when (session.authMode) {
                AuthMode.API_TOKEN -> {
                    if (session.apiKey.isNotBlank() && session.apiSecret.isNotBlank()) {
                        requestBuilder.header(
                            "Authorization",
                            "token ${session.apiKey}:${session.apiSecret}"
                        )
                    }
                }
                AuthMode.SESSION_COOKIE -> {
                    if (session.sessionCookie.isNotBlank()) {
                        requestBuilder.header("Cookie", session.sessionCookie)
                    }
                    if (session.csrfToken.isNotBlank()) {
                        requestBuilder.header("X-Frappe-CSRF-Token", session.csrfToken)
                    }
                    if (session.baseUrl.isNotBlank()) {
                        requestBuilder.header("Origin", session.baseUrl)
                    }
                }
            }
            chain.proceed(requestBuilder.build())
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    fun createService(session: AuthSession): FrappeApiService {
        val baseUrl = ensureTrailingSlash(session.baseUrl)
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(buildOkHttpClient(session))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FrappeApiService::class.java)
    }

    fun createUnauthenticatedService(baseUrl: String): FrappeApiService {
        val normalized = ensureTrailingSlash(normalizeBaseUrl(baseUrl))
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FrappeApiService::class.java)
    }

    fun normalizeBaseUrl(raw: String): String {
        var url = raw.trim().trimEnd('/')
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        return url
    }

    private fun ensureTrailingSlash(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}
