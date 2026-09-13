package com.example.localhand_new.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptors aren't suspend, so reading the DataStore-backed token needs
 * a blocking bridge; runBlocking here is cheap since DataStore's Flow.first() just
 * replays its already-cached in-memory value on every call after the first.
 */
class AuthInterceptor(private val tokenProvider: suspend () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokenProvider() }
        val request = chain.request().let { original ->
            if (token.isNullOrBlank()) {
                original
            } else {
                original.newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            }
        }
        return chain.proceed(request)
    }
}
