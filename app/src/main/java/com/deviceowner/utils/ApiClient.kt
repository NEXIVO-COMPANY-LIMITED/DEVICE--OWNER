package com.deviceowner.utils

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * ApiClient handles HTTP communication with backend API
 */
class ApiClient(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("api_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val TAG = "ApiClient"
        private const val BASE_URL = "http://localhost:3000" // Change to your backend URL
        private const val TIMEOUT = 30000 // 30 seconds
    }
    
    /**
     * Make a GET request
     */
    fun get(endpoint: String): JSONObject? {
        return try {
            val url = URL("$BASE_URL$endpoint")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT
            connection.readTimeout = TIMEOUT
            connection.setRequestProperty("Content-Type", "application/json")
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = readResponse(connection)
                JSONObject(response)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Make a POST request
     */
    fun post(endpoint: String, data: Map<String, Any>): JSONObject? {
        return try {
            val url = URL("$BASE_URL$endpoint")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT
            connection.readTimeout = TIMEOUT
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            // Send request body
            val requestBody = JSONObject(data).toString()
            connection.outputStream.use { output ->
                output.write(requestBody.toByteArray())
                output.flush()
            }
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                val response = readResponse(connection)
                JSONObject(response)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Read response from connection
     */
    private fun readResponse(connection: HttpURLConnection): String {
        return BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
            reader.readText()
        }
    }
}
