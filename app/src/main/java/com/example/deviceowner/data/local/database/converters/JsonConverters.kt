package com.example.deviceowner.data.local.database.converters

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * JSON Type Converters for Room Database
 * 
 * Converts complex objects to/from JSON for storage
 */
class JsonConverters {
    
    private val gson = Gson()
    
    @TypeConverter
    fun fromMap(value: Map<String, Any?>?): String? {
        return if (value == null) null else gson.toJson(value)
    }
    
    @TypeConverter
    fun toMap(value: String?): Map<String, Any?>? {
        return if (value == null) null else {
            val type = object : TypeToken<Map<String, Any?>>() {}.type
            gson.fromJson(value, type)
        }
    }
    
    @TypeConverter
    fun fromList(value: List<Any>?): String? {
        return if (value == null) null else gson.toJson(value)
    }
    
    @TypeConverter
    fun toList(value: String?): List<Any>? {
        return if (value == null) null else {
            val type = object : TypeToken<List<Any>>() {}.type
            gson.fromJson(value, type)
        }
    }
    
    @TypeConverter
    fun fromStringMap(value: Map<String, String>?): String? {
        return if (value == null) null else gson.toJson(value)
    }
    
    @TypeConverter
    fun toStringMap(value: String?): Map<String, String>? {
        return if (value == null) null else {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson(value, type)
        }
    }
}
