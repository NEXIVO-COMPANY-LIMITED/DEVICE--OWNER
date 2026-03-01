package com.microspace.payo.data.repository

import android.content.Context
import com.microspace.payo.AppConfig
import com.microspace.payo.data.models.payment.InstallmentResponse
import com.microspace.payo.data.models.payment.PaymentRequest
import com.microspace.payo.data.models.payment.PaymentResponse
import com.microspace.payo.data.remote.ApiService
import com.microspace.payo.data.remote.api.ApiHeadersInterceptor
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class PaymentRepository(private val context: Context) {

    private val gson = GsonBuilder().setLenient().create()

    private val apiService: ApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (AppConfig.ENABLE_LOGGING) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(ApiHeadersInterceptor())
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(AppConfig.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        retrofit.create(ApiService::class.java)
    }

    suspend fun getInstallments(deviceId: String): Response<com.microspace.payo.data.models.payment.InstallmentsResponse> = withContext(Dispatchers.IO) {
        apiService.getDeviceInstallments(deviceId)
    }

    suspend fun processPayment(loanNumber: String, phoneNumber: String, amount: Double): Response<PaymentResponse> = withContext(Dispatchers.IO) {
        val request = PaymentRequest(loanNumber, phoneNumber, amount)
        apiService.processPayment(request)
    }
}




