package com.pureplus.delivery.data.remote

import com.pureplus.delivery.data.local.TokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the staff JWT to every request.
 * Reads from DataStore synchronously (only on OkHttp's IO thread — safe here).
 */
class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokenStore.getToken() }
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
