package com.pureplus.delivery.data.remote

import com.pureplus.delivery.BuildConfig
import com.pureplus.delivery.PurePlusDeliveryApp
import com.pureplus.delivery.data.local.TokenStore
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private val tokenStore by lazy { TokenStore(PurePlusDeliveryApp.instance) }

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStore))
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BODY
                        }
                    )
                }
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
