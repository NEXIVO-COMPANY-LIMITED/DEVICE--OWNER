package com.example.deviceowner.data.api

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private val TAG = "ApiClient"
    
    /**
     * Get base URL from ApiConfig
     * Supports runtime configuration and BuildConfig fallback
     */
    private fun getBaseUrl(context: android.content.Context? = null): String {
        return com.example.deviceowner.config.ApiConfig.getBaseUrl(context)
    }
    
    private val httpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
        
        // Add logging only if enabled (controlled by BuildConfig)
        if (com.example.deviceowner.config.ApiConfig.isLoggingEnabled()) {
            val logging = HttpLoggingInterceptor { message ->
                Log.d(TAG, message)
            }.apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(logging)
        } else {
            // Minimal logging in production
            val logging = HttpLoggingInterceptor { message ->
                // Only log errors in production
                if (message.contains("ERROR") || message.contains("error")) {
                    Log.e(TAG, message)
                }
            }.apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            builder.addInterceptor(logging)
        }
        
        builder
            .addInterceptor(SecurityInterceptor())
            .addInterceptor(RetryInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
    
    /**
     * Get Retrofit instance with configurable base URL
     */
    fun getRetrofit(context: android.content.Context? = null): Retrofit {
        return Retrofit.Builder()
            .baseUrl(getBaseUrl(context))
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    private val retrofit: Retrofit by lazy {
        getRetrofit()
    }
    
    val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }
    
    /**
     * Get API service with custom context (for runtime URL configuration)
     */
    fun getApiService(context: android.content.Context? = null): ApiService {
        return getRetrofit(context).create(ApiService::class.java)
    }
}
