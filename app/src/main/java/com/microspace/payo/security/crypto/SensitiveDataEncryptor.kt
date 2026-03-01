package com.microspace.payo.security.crypto

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * Encrypts and decrypts sensitive data models before storage.
 * Provides a high-level API for protecting complex data structures.
 */
class SensitiveDataEncryptor(private val context: Context) {

    private val encryption = SecureDataEncryption(context)
    private val gson = Gson()

    /**
     * Encrypts a data object to JSON string
     */
    fun <T> encryptObject(obj: T): String {
        return try {
            val json = gson.toJson(obj)
            encryption.encryptString(json)
        } catch (e: Exception) {
            throw EncryptionException("Failed to encrypt object", e)
        }
    }

    /**
     * Decrypts a JSON string back to object
     */
    fun <T> decryptObject(encryptedJson: String, clazz: Class<T>): T {
        return try {
            val json = encryption.decryptString(encryptedJson)
            gson.fromJson(json, clazz)
        } catch (e: Exception) {
            throw EncryptionException("Failed to decrypt object", e)
        }
    }

    /**
     * Encrypts specific sensitive fields in a JSON object
     */
    fun encryptSensitiveFields(json: String, sensitiveFields: List<String>): String {
        return try {
            val jsonObject = gson.fromJson(json, JsonObject::class.java)
            
            sensitiveFields.forEach { field ->
                if (jsonObject.has(field)) {
                    val value = jsonObject.get(field).asString
                    val encrypted = encryption.encryptString(value)
                    jsonObject.addProperty(field, encrypted)
                }
            }
            
            jsonObject.toString()
        } catch (e: Exception) {
            throw EncryptionException("Failed to encrypt sensitive fields", e)
        }
    }

    /**
     * Decrypts specific sensitive fields in a JSON object
     */
    fun decryptSensitiveFields(json: String, sensitiveFields: List<String>): String {
        return try {
            val jsonObject = gson.fromJson(json, JsonObject::class.java)
            
            sensitiveFields.forEach { field ->
                if (jsonObject.has(field)) {
                    try {
                        val encrypted = jsonObject.get(field).asString
                        val decrypted = encryption.decryptString(encrypted)
                        jsonObject.addProperty(field, decrypted)
                    } catch (e: Exception) {
                        // Field might not be encrypted, skip
                    }
                }
            }
            
            jsonObject.toString()
        } catch (e: Exception) {
            throw EncryptionException("Failed to decrypt sensitive fields", e)
        }
    }

    /**
     * Encrypts device registration data
     */
    fun encryptRegistrationData(
        loanNumber: String,
        deviceId: String,
        imei: String
    ): Map<String, String> {
        return mapOf(
            "loan_number" to encryption.encryptString(loanNumber),
            "device_id" to encryption.encryptString(deviceId),
            "imei" to encryption.encryptString(imei)
        )
    }

    /**
     * Decrypts device registration data
     */
    fun decryptRegistrationData(encryptedData: Map<String, String>): Map<String, String> {
        return encryptedData.mapValues { (_, value) ->
            try {
                encryption.decryptString(value)
            } catch (e: Exception) {
                value
            }
        }
    }

    /**
     * Encrypts payment information
     */
    fun encryptPaymentData(
        loanNumber: String,
        phoneNumber: String,
        amount: String
    ): Map<String, String> {
        return mapOf(
            "loan_number" to encryption.encryptString(loanNumber),
            "phone_number" to encryption.encryptString(phoneNumber),
            "amount" to encryption.encryptString(amount)
        )
    }

    /**
     * Encrypts device identifiers
     */
    fun encryptDeviceIdentifiers(
        imei: String,
        serialNumber: String,
        androidId: String
    ): Map<String, String> {
        return mapOf(
            "imei" to encryption.encryptString(imei),
            "serial_number" to encryption.encryptString(serialNumber),
            "android_id" to encryption.encryptString(androidId)
        )
    }

    /**
     * Decrypts device identifiers
     */
    fun decryptDeviceIdentifiers(encryptedData: Map<String, String>): Map<String, String> {
        return encryptedData.mapValues { (_, value) ->
            try {
                encryption.decryptString(value)
            } catch (e: Exception) {
                value
            }
        }
    }
}




