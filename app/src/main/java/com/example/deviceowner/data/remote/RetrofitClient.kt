package com.microspace.payo.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // IMPORTANT: Replace with your actual base URL from a config file or constants
    private const val BASE_URL = "https://your-api-base-url.com/"

    val instance: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            // You can add an OkHttpClient here for logging, timeouts, interceptors etc.
            .build()
    }
}
