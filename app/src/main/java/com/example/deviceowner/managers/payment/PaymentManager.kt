package com.example.deviceowner.managers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.deviceowner.models.*
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Manages payment records and loan enforcement
 * Feature 4.4: Remote Lock/Unlock - Payment Integration
 */
class PaymentManager(private val context: Context) {

    companion object {
        private const val TAG = "PaymentManager"
        private const val PREFS_NAME = "payment_prefs"
        private const val KEY_PAYMENTS = "payments"
        private const val KEY_PAYMENT_HISTORY = "payment_history"
        private const val MAX_HISTORY_SIZE = 100
        private const val OVERDUE_THRESHOLD_DAYS = 14
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val auditLog = IdentifierAuditLog(context)
    private val loanManager = LoanManager(context)

    init {
        // Initialize with mocked data if empty
        if (prefs.getString(KEY_PAYMENTS, null) == null) {
            initializeMockedPayments()
        }
    }

    /**
     * Initialize with mocked payment data for testing
     */
    private fun initializeMockedPayments() {
        try {
            val now = System.currentTimeMillis()
            val mockedPayments = mapOf(
                "PAY_001" to PaymentRecord(
                    paymentId = "PAY_001",
                    loanId = "LOAN_001",
                    deviceId = "device_001",
                    userRef = "USER_001",
                    amount = 500000.0,
                    currency = "TZS",
                    paymentStatus = "PENDING",
                    paymentDate = now,
                    dueDate = now + TimeUnit.DAYS.toMillis(30),
                    createdAt = now - TimeUnit.DAYS.toMillis(10)
                ),
                "PAY_002" to PaymentRecord(
                    paymentId = "PAY_002",
                    loanId = "LOAN_002",
                    deviceId = "device_002",
                    userRef = "USER_002",
                    amount = 750000.0,
                    currency = "TZS",
                    paymentStatus = "OVERDUE",
                    paymentDate = now,
                    dueDate = now - TimeUnit.DAYS.toMillis(5),
                    daysOverdue = 5,
                    createdAt = now - TimeUnit.DAYS.toMillis(45)
                ),
                "PAY_003" to PaymentRecord(
                    paymentId = "PAY_003",
                    loanId = "LOAN_003",
                    deviceId = "device_003",
                    userRef = "USER_003",
                    amount = 1000000.0,
                    currency = "TZS",
                    paymentStatus = "DEFAULTED",
                    paymentDate = now,
                    dueDate = now - TimeUnit.DAYS.toMillis(30),
                    daysOverdue = 30,
                    createdAt = now - TimeUnit.DAYS.toMillis(90)
                ),
                "PAY_004" to PaymentRecord(
                    paymentId = "PAY_004",
                    loanId = "LOAN_004",
                    deviceId = "device_004",
                    userRef = "USER_004",
                    amount = 600000.0,
                    currency = "TZS",
                    paymentStatus = "COMPLETED",
                    paymentDate = now - TimeUnit.DAYS.toMillis(5),
                    dueDate = now - TimeUnit.DAYS.toMillis(10),
                    createdAt = now - TimeUnit.DAYS.toMillis(20)
                )
            )

            val paymentsJson = gson.toJson(mockedPayments)
            prefs.edit().putString(KEY_PAYMENTS, paymentsJson).apply()

            Log.d(TAG, "✓ Initialized ${mockedPayments.size} mocked payments")
            auditLog.logAction("PAYMENTS_INITIALIZED", "Initialized ${mockedPayments.size} mocked payments")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing mocked payments", e)
        }
    }

    /**
     * Get payment by ID
     */
    fun getPayment(paymentId: String): PaymentRecord? {
        return try {
            val paymentsJson = prefs.getString(KEY_PAYMENTS, null) ?: return null
            val payments = gson.fromJson(paymentsJson, Map::class.java)
            val paymentJson = gson.toJson(payments[paymentId])
            gson.fromJson(paymentJson, PaymentRecord::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting payment", e)
            null
        }
    }

    /**
     * Get all payments
     */
    fun getAllPayments(): List<PaymentRecord> {
        return try {
            val paymentsJson = prefs.getString(KEY_PAYMENTS, null) ?: return emptyList()
            val payments = gson.fromJson(paymentsJson, Map::class.java)
            payments.values.mapNotNull { payment ->
                try {
                    gson.fromJson(gson.toJson(payment), PaymentRecord::class.java)
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all payments", e)
            emptyList()
        }
    }

    /**
     * Get payments by device ID
     */
    fun getPaymentsByDevice(deviceId: String): List<PaymentRecord> {
        return getAllPayments().filter { it.deviceId == deviceId }
    }

    /**
     * Get payments by user reference
     */
    fun getPaymentsByUser(userRef: String): List<PaymentRecord> {
        return getAllPayments().filter { it.userRef == userRef }
    }

    /**
     * Get overdue payments
     */
    fun getOverduePayments(): List<PaymentRecord> {
        return getAllPayments().filter { payment ->
            payment.paymentStatus == "OVERDUE" ||
            payment.paymentStatus == "DEFAULTED"
        }
    }

    /**
     * Check if payment is overdue
     */
    fun isPaymentOverdue(paymentId: String): Boolean {
        val payment = getPayment(paymentId) ?: return false
        val now = System.currentTimeMillis()
        val daysOverdue = (now - payment.dueDate) / TimeUnit.DAYS.toMillis(1)
        return daysOverdue > 0
    }

    /**
     * Get days overdue for payment
     */
    fun getDaysOverdue(paymentId: String): Long {
        val payment = getPayment(paymentId) ?: return 0
        val now = System.currentTimeMillis()
        val daysOverdue = (now - payment.dueDate) / TimeUnit.DAYS.toMillis(1)
        return if (daysOverdue > 0) daysOverdue else 0
    }

    /**
     * Update payment status
     */
    fun updatePaymentStatus(paymentId: String, status: String): Boolean {
        return try {
            val paymentsJson = prefs.getString(KEY_PAYMENTS, null) ?: return false
            val type = object : com.google.gson.reflect.TypeToken<MutableMap<String, PaymentRecord>>() {}.type
            val payments = gson.fromJson<MutableMap<String, PaymentRecord>>(paymentsJson, type)
            
            val payment = payments[paymentId] ?: return false
            
            val updatedPayment = payment.copy(
                paymentStatus = status,
                updatedAt = System.currentTimeMillis()
            )
            
            payments[paymentId] = updatedPayment
            val updatedJson = gson.toJson(payments)
            prefs.edit().putString(KEY_PAYMENTS, updatedJson).apply()
            
            Log.d(TAG, "✓ Payment status updated: $paymentId -> $status")
            auditLog.logAction("PAYMENT_STATUS_UPDATED", "Payment $paymentId status updated to $status")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating payment status", e)
            false
        }
    }

    /**
     * Determine lock type based on payment status
     */
    fun determineLockType(paymentId: String): LockType? {
        val payment = getPayment(paymentId) ?: return null
        
        return when (payment.paymentStatus) {
            "OVERDUE" -> {
                val daysOverdue = getDaysOverdue(paymentId)
                if (daysOverdue >= OVERDUE_THRESHOLD_DAYS) LockType.HARD else LockType.SOFT
            }
            "DEFAULTED" -> LockType.HARD
            else -> null
        }
    }

    /**
     * Get lock message for payment
     */
    fun getLockMessage(paymentId: String): String {
        val payment = getPayment(paymentId) ?: return "Payment enforcement lock"
        val daysOverdue = getDaysOverdue(paymentId)
        
        return when (payment.paymentStatus) {
            "OVERDUE" -> {
                "Payment overdue by $daysOverdue days. Amount due: ${payment.amount} ${payment.currency}"
            }
            "DEFAULTED" -> {
                "Loan defaulted. Device locked for repossession. Contact support to resolve."
            }
            else -> "Payment enforcement lock"
        }
    }

    /**
     * Add payment to history
     */
    private fun addToPaymentHistory(payment: PaymentRecord) {
        try {
            val historyJson = prefs.getString(KEY_PAYMENT_HISTORY, null)
            val history = if (historyJson != null) {
                gson.fromJson(historyJson, Array<PaymentRecord>::class.java).toMutableList()
            } else {
                mutableListOf<PaymentRecord>()
            }

            history.add(0, payment)
            if (history.size > MAX_HISTORY_SIZE) {
                history.removeAt(history.size - 1)
            }

            prefs.edit().putString(KEY_PAYMENT_HISTORY, gson.toJson(history)).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding to payment history", e)
        }
    }
}
