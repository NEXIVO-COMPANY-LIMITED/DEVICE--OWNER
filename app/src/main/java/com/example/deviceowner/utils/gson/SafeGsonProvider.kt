package com.example.deviceowner.utils.gson

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

/**
 * SafeGsonProvider - Provides Gson instances with safe TypeToken handling
 */
object SafeGsonProvider {
    
    private const val TAG = "SafeGsonProvider"
    
    private val gsonInstance: Gson by lazy {
        try {
            GsonBuilder()
                .serializeNulls()
                .create()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create Gson instance, using default", e)
            Gson()
        }
    }
    
    fun getGson(): Gson = gsonInstance
    
    /**
     * Safely get a Type for a generic class
     */
    fun getType(typeToken: () -> Type): Type {
        return try {
            typeToken()
        } catch (e: Exception) {
            Log.w(TAG, "TypeToken creation failed, using Object.class as fallback", e)
            Any::class.java
        }
    }
    
    /**
     * Safely deserialize JSON to a generic type
     */
    fun <T> fromJson(json: String?, typeToken: () -> Type): T? {
        if (json == null) return null
        return try {
            val type = getType(typeToken)
            gsonInstance.fromJson<T>(json, type)
        } catch (e: Exception) {
            Log.e(TAG, "Deserialization failed: ${e.message}", e)
            null
        }
    }
    
    /**
     * Safely serialize an object to JSON
     */
    fun toJson(obj: Any?): String? {
        if (obj == null) return null
        return try {
            gsonInstance.toJson(obj)
        } catch (e: Exception) {
            Log.e(TAG, "Serialization failed: ${e.message}", e)
            null
        }
    }
    
    // Static helpers to avoid repeated anonymous class creation
    fun getMapType(): Type = getType { object : TypeToken<Map<String, Any?>>() {}.type }
    fun getListType(): Type = getType { object : TypeToken<List<Any>>() {}.type }
    fun getStringMapType(): Type = getType { object : TypeToken<Map<String, String>>() {}.type }
    fun getListStringType(): Type = getType { object : TypeToken<List<String>>() {}.type }
}
