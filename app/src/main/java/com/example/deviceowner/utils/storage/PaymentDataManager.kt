package com.example.deviceowner.utils.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.*

/**
 * PaymentDataManager - Manages payment-related data from heartbeat responses
 * 
 * Stores:
 * - Next payment date and time
 * - Unlock password for payment
 * - Payment history
 * - Lock/unlock state
 * - Server time for synchronization
 */
class PaymentDataManager(private val context: Context) {
    
    companion object {
        private const val TAG = "PaymentDataManager"
        private const val PREF_NAME = "payment_data"
        
        // Payment info keys
        private const val KEY_NEXT_PAYMENT_DATE = "next_payment_date"
        private const val KEY_NEXT_PAYMENT_TIME = "next_payment_time"
        private const val KEY_UNLOCK_PASSWORD = "unlock_password"
        private const val KEY_PAYMENT_AMOUNT = "payment_amount"
        private const val KEY_PAYMENT_CURRENCY = "payment_currency"
        
        // Lock state keys
        private const val KEY_IS_LOCKED = "is_locked"
        private const val KEY_LOCK_REASON = "lock_reason"
        private const val KEY_LOCK_TIMESTAMP = "lock_timestamp"
        
        // Server sync keys
        private const val KEY_SERVER_TIME = "server_time"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        
        // Payment history
        private const val KEY_PAYMENT_HISTORY = "payment_history_json"
        
        // Days until payment
        private const val KEY_DAYS_UNTIL_PAYMENT = "days_until_payment"
    }
    
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    
    private val gson = com.example.deviceowner.utils.gson.SafeGsonProvider.getGson()

    // ============================================================
    // NEXT PAYMENT INFORMATION
    // ============================================================
    
    /**
     * Save next payment information from heartbeat response
     * 
     * @param dateTime ISO-8601 format: "2026-02-07T23:59:00+03:00"
     * @param unlockPassword Password to unlock device after payment
     * @param amount Payment amount (optional)
     * @param currency Payment currency (optional)
     */
    fun saveNextPaymentInfo(
        dateTime: String?,
        unlockPassword: String?,
        amount: String? = null,
        currency: String? = null
    ) {
        try {
            sharedPreferences.edit().apply {
                putString(KEY_NEXT_PAYMENT_DATE, dateTime)
                putString(KEY_UNLOCK_PASSWORD, unlockPassword)
                putString(KEY_PAYMENT_AMOUNT, amount)
                putString(KEY_PAYMENT_CURRENCY, currency)
                putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis())
                apply()
            }
            
            Log.d(TAG, "✅ Next payment info saved:")
            Log.d(TAG, "   - Date: $dateTime")
            Log.d(TAG, "   - Password: ${if (unlockPassword != null) "***saved" else "none"}")
            Log.d(TAG, "   - Amount: $amount $currency")
            
            // Calculate days until payment
            calculateDaysUntilPayment(dateTime)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving payment info: ${e.message}", e)
        }
    }
    
    fun getNextPaymentDate(): String? {
        return sharedPreferences.getString(KEY_NEXT_PAYMENT_DATE, null)
    }
    
    fun getUnlockPassword(): String? {
        return sharedPreferences.getString(KEY_UNLOCK_PASSWORD, null)
    }
    
    fun getPaymentAmount(): String? {
        return sharedPreferences.getString(KEY_PAYMENT_AMOUNT, null)
    }
    
    fun getPaymentCurrency(): String? {
        return sharedPreferences.getString(KEY_PAYMENT_CURRENCY, null)
    }
    
    fun getDaysUntilPayment(): Int {
        return sharedPreferences.getInt(KEY_DAYS_UNTIL_PAYMENT, -1)
    }
    
    /**
     * Calculate days until payment from ISO-8601 datetime
     */
    private fun calculateDaysUntilPayment(dateTimeStr: String?) {
        if (dateTimeStr == null) return
        
        try {
            // Parse ISO-8601 format: "2026-02-07T23:59:00+03:00"
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            
            val paymentDate = sdf.parse(dateTimeStr.substringBefore("+").substringBefore("-"))
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            val paymentCalendar = Calendar.getInstance().apply {
                time = paymentDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            val daysUntil = ((paymentCalendar.timeInMillis - today.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
            
            sharedPreferences.edit().putInt(KEY_DAYS_UNTIL_PAYMENT, daysUntil).apply()
            
            Log.d(TAG, "📅 Days until payment: $daysUntil")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error calculating days until payment: ${e.message}", e)
        }
    }
    
    // ============================================================
    // LOCK STATE
    // ============================================================
    
    fun saveLockState(isLocked: Boolean, reason: String? = null) {
        try {
            sharedPreferences.edit().apply {
                putBoolean(KEY_IS_LOCKED, isLocked)
                putString(KEY_LOCK_REASON, reason)
                putLong(KEY_LOCK_TIMESTAMP, System.currentTimeMillis())
                apply()
            }
            
            Log.d(TAG, "🔒 Lock state saved: isLocked=$isLocked, reason=$reason")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving lock state: ${e.message}", e)
        }
    }
    
    fun isDeviceLocked(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_LOCKED, false)
    }
    
    fun getLockReason(): String? {
        return sharedPreferences.getString(KEY_LOCK_REASON, null)
    }
    
    fun getLockTimestamp(): Long {
        return sharedPreferences.getLong(KEY_LOCK_TIMESTAMP, 0L)
    }
    
    // ============================================================
    // SERVER TIME SYNC
    // ============================================================
    
    fun saveServerTime(serverTime: String?) {
        try {
            sharedPreferences.edit().apply {
                putString(KEY_SERVER_TIME, serverTime)
                putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis())
                apply()
            }
            
            Log.d(TAG, "🕐 Server time saved: $serverTime")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving server time: ${e.message}", e)
        }
    }
    
    fun getServerTime(): String? {
        return sharedPreferences.getString(KEY_SERVER_TIME, null)
    }
    
    fun getLastSyncTime(): Long {
        return sharedPreferences.getLong(KEY_LAST_SYNC_TIME, 0L)
    }
    
    // ============================================================
    // PAYMENT HISTORY
    // ============================================================
    
    data class PaymentRecord(
        val date: Long,
        val amount: String?,
        val currency: String?,
        val status: String, // "pending", "paid", "overdue"
        val daysUntilDue: Int
    )
    
    fun addPaymentRecord(record: PaymentRecord) {
        try {
            val history = getPaymentHistory().toMutableList()
            history.add(record)
            
            // Keep only last 12 records
            if (history.size > 12) {
                history.removeAt(0)
            }
            
            val json = gson.toJson(history)
            sharedPreferences.edit().putString(KEY_PAYMENT_HISTORY, json).apply()
            
            Log.d(TAG, "📝 Payment record added: $record")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error adding payment record: ${e.message}", e)
        }
    }
    
    fun getPaymentHistory(): List<PaymentRecord> {
        return try {
            val json = sharedPreferences.getString(KEY_PAYMENT_HISTORY, null) ?: return emptyList()
            com.example.deviceowner.utils.gson.SafeGsonProvider.fromJson(json) {
                object : com.google.gson.reflect.TypeToken<List<PaymentRecord>>() {}.type
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error reading payment history: ${e.message}", e)
            emptyList()
        }
    }
    
    fun clearPaymentHistory() {
        try {
            sharedPreferences.edit().remove(KEY_PAYMENT_HISTORY).apply()
            Log.d(TAG, "🗑️ Payment history cleared")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing payment history: ${e.message}", e)
        }
    }
    
    // ============================================================
    // UTILITY METHODS
    // ============================================================
    
    /**
     * Check if payment is overdue
     */
    fun isPaymentOverdue(): Boolean {
        val daysUntil = getDaysUntilPayment()
        return daysUntil >= 0 && daysUntil <= 0
    }
    
    /**
     * Check if payment is due soon (within 3 days)
     */
    fun isPaymentDueSoon(): Boolean {
        val daysUntil = getDaysUntilPayment()
        return daysUntil in 1..3
    }
    
    /**
     * Get formatted payment due date
     */
    fun getFormattedPaymentDate(): String? {
        val dateStr = getNextPaymentDate() ?: return null
        
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val date = sdf.parse(dateStr.substringBefore("+").substringBefore("-"))
            
            val displaySdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
            displaySdf.format(date)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error formatting payment date: ${e.message}", e)
            dateStr
        }
    }
    
    /**
     * Clear all payment data
     */
    fun clearAllPaymentData() {
        try {
            sharedPreferences.edit().apply {
                remove(KEY_NEXT_PAYMENT_DATE)
                remove(KEY_NEXT_PAYMENT_TIME)
                remove(KEY_UNLOCK_PASSWORD)
                remove(KEY_PAYMENT_AMOUNT)
                remove(KEY_PAYMENT_CURRENCY)
                remove(KEY_IS_LOCKED)
                remove(KEY_LOCK_REASON)
                remove(KEY_LOCK_TIMESTAMP)
                remove(KEY_SERVER_TIME)
                remove(KEY_LAST_SYNC_TIME)
                remove(KEY_PAYMENT_HISTORY)
                remove(KEY_DAYS_UNTIL_PAYMENT)
                apply()
            }
            
            Log.d(TAG, "🗑️ All payment data cleared")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing payment data: ${e.message}", e)
        }
    }
}
