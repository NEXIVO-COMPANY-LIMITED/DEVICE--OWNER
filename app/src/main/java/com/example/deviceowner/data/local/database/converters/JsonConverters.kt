package com.microspace.payo.data.local.database.converters

import android.util.Log
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.microspace.payo.utils.gson.SafeGsonProvider

/**
 * JSON Type Converters for Room Database
 * 
 * Converts complex objects to/from JSON for storage
 * 
 * CRITICAL: Uses SafeGsonProvider to handle TypeToken safely and avoid
 * ExceptionInInitializerError when R8 strips generic type information.
 */
class JsonConverters {
    
    private val gson: Gson = SafeGsonProvider.getGson()
    private val TAG = "JsonConverters"
    
    @TypeConverter
    fun fromMap(value: Map<String, Any?>?): String? {
        return if (value == null) null else {
            try {
                SafeGsonProvider.toJson(value)
            } catch (e: Exception) {
                Log.e(TAG, "Error serializing map", e)
                null
            }
        }
    }
    
    @TypeConverter
    fun toMap(value: String?): Map<String, Any?>? {
        return if (value == null) null else {
            try {
                SafeGsonProvider.fromJson(value) { SafeGsonProvider.getMapType() }
            } catch (e: Exception) {
                Log.e(TAG, "Error deserializing map: ${e.message}", e)
                null
            }
        }
    }
    
    @TypeConverter
    fun fromList(value: List<Any>?): String? {
        return if (value == null) null else {
            try {
                SafeGsonProvider.toJson(value)
            } catch (e: Exception) {
                Log.e(TAG, "Error serializing list", e)
                null
            }
        }
    }
    
    @TypeConverter
    fun toList(value: String?): List<Any>? {
        return if (value == null) null else {
            try {
                SafeGsonProvider.fromJson(value) { SafeGsonProvider.getListType() }
            } catch (e: Exception) {
                Log.e(TAG, "Error deserializing list: ${e.message}", e)
                null
            }
        }
    }
    
    @TypeConverter
    fun fromStringMap(value: Map<String, String>?): String? {
        return if (value == null) null else {
            try {
                SafeGsonProvider.toJson(value)
            } catch (e: Exception) {
                Log.e(TAG, "Error serializing string map", e)
                null
            }
        }
    }
    
    @TypeConverter
    fun toStringMap(value: String?): Map<String, String>? {
        return if (value == null) null else {
            try {
                SafeGsonProvider.fromJson(value) { SafeGsonProvider.getStringMapType() }
            } catch (e: Exception) {
                Log.e(TAG, "Error deserializing string map: ${e.message}", e)
                null
            }
        }
    }
}
