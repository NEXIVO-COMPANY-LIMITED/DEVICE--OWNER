package com.example.deviceowner.utils.helpers

import android.content.Context
import android.widget.Toast
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ErrorHandler {
    
    fun handleApiError(context: Context, error: Throwable): String {
        val message = when (error) {
            is UnknownHostException -> "No internet connection. Please check your network."
            is ConnectException -> "Cannot connect to server. Please try again later."
            is SocketTimeoutException -> "Request timeout. Please check your connection."
            else -> "An error occurred: ${error.message}"
        }
        
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        return message
    }
    
    fun getErrorMessage(error: Throwable): String {
        return when (error) {
            is UnknownHostException -> "No internet connection"
            is ConnectException -> "Cannot connect to server"
            is SocketTimeoutException -> "Request timeout"
            else -> error.message ?: "Unknown error"
        }
    }
}