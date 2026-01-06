package com.example.deviceowner.managers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.deviceowner.models.LoanRecord
import com.example.deviceowner.models.PaymentRecord
import com.google.gson.Gson
import java.util.concurrent.TimeUnit

/**
 * Manages loan records and payment tracking
 * Feature 4.4: Remote Lock/Unlock
 */
class LoanManager(private val context: Context) {

    companion object {
        private const val TAG = "LoanManager"
        private const val PREFS_NAME = "loan_manager_prefs"
        private const val KEY_LOANS = "loans"
        private const val KEY_PAYMENTS = "payments"
        private const val KEY_MOCKED_DATA_INITIALIZED = "mocked_data_initialized"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    init {
        // Initialize mocked data on first run
        if (!prefs.getBoolean(KEY_MOCKED_DATA_INITIALIZED, false)) {
            initializeMockedData()
        }
    }

    /**
     * Initialize mocked loan and payment data
     */
    private fun initializeMockedData() {
        try {
            Log.d(TAG, "Initializing mocked loan and payment data")

            val now = System.currentTimeMillis()
            val loans = mutableMapOf<String, LoanRecord>()

            // Loan 1: ACTIVE - Payment due in 30 days
            loans["LOAN_001"] = LoanRecord(
                loanId = "LOAN_001",
                deviceId = "device_001",
                userRef = "USER_001",
                loanAmount = 500000.0,
                currency = "TZS",
                loanStatus = "ACTIVE",
                dueDate = now + TimeUnit.DAYS.toMillis(30),
                createdAt = now - TimeUnit.DAYS.toMillis(10)
            )

            // Loan 2: OVERDUE - 5 days overdue
            loans["LOAN_002"] = LoanRecord(
                loanId = "LOAN_002",
                deviceId = "device_002",
                userRef = "USER_002",
                loanAmount = 750000.0,
                currency = "TZS",
                loanStatus = "OVERDUE",
                dueDate = now - TimeUnit.DAYS.toMillis(5),
                daysOverdue = 5,
                createdAt = now - TimeUnit.DAYS.toMillis(45)
            )

            // Loan 3: DEFAULTED - 30 days overdue (repossession)
            loans["LOAN_003"] = LoanRecord(
                loanId = "LOAN_003",
                deviceId = "device_003",
                userRef = "USER_003",
                loanAmount = 1000000.0,
                currency = "TZS",
                loanStatus = "DEFAULTED",
                dueDate = now - TimeUnit.DAYS.toMillis(30),
                daysOverdue = 30,
                createdAt = now - TimeUnit.DAYS.toMillis(90)
            )

            // Loan 4: PAID - Payment completed
            loans["LOAN_004"] = LoanRecord(
                loanId = "LOAN_004",
                deviceId = "device_004",
                userRef = "USER_004",
                loanAmount = 600000.0,
                currency = "TZS",
                loanStatus = "PAID",
                dueDate = now - TimeUnit.DAYS.toMillis(10),
                paymentDate = now - TimeUnit.DAYS.toMillis(5),
                createdAt = now - TimeUnit.DAYS.toMillis(60)
            )

            // Save loans
            val loansJson = gson.toJson(loans)
            prefs.edit().putString(KEY_LOANS, loansJson).apply()

            // Initialize payment records
            val payments = mutableMapOf<String, PaymentRecord>()

            payments["PAY_001"] = PaymentRecord(
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
            )

            payments["PAY_002"] = PaymentRecord(
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
            )

            payments["PAY_003"] = PaymentRecord(
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
            )

            payments["PAY_004"] = PaymentRecord(
                paymentId = "PAY_004",
                loanId = "LOAN_004",
                deviceId = "device_004",
                userRef = "USER_004",
                amount = 600000.0,
                currency = "TZS",
                paymentStatus = "COMPLETED",
                paymentDate = now - TimeUnit.DAYS.toMillis(5),
                dueDate = now - TimeUnit.DAYS.toMillis(10),
                createdAt = now - TimeUnit.DAYS.toMillis(60)
            )

            // Save payments
            val paymentsJson = gson.toJson(payments)
            prefs.edit().putString(KEY_PAYMENTS, paymentsJson).apply()

            prefs.edit().putBoolean(KEY_MOCKED_DATA_INITIALIZED, true).apply()

            Log.d(TAG, "✓ Mocked data initialized: 4 loans, 4 payments")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing mocked data", e)
        }
    }

    /**
     * Get loan by ID
     */
    fun getLoan(loanId: String): LoanRecord? {
        return try {
            val loansJson = prefs.getString(KEY_LOANS, "{}")
            val loansMap = gson.fromJson(loansJson, Map::class.java) as? Map<String, Any>
            val loanData = loansMap?.get(loanId) as? Map<String, Any>
            
            if (loanData != null) {
                gson.fromJson(gson.toJson(loanData), LoanRecord::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting loan", e)
            null
        }
    }

    /**
     * Get loan by device ID
     */
    fun getLoanByDeviceId(deviceId: String): LoanRecord? {
        return try {
            val loansJson = prefs.getString(KEY_LOANS, "{}")
            val loansMap = gson.fromJson(loansJson, Map::class.java) as? Map<String, Any>
            
            for ((_, loanData) in loansMap ?: emptyMap()) {
                val loan = gson.fromJson(gson.toJson(loanData), LoanRecord::class.java)
                if (loan.deviceId == deviceId) {
                    return loan
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting loan by device ID", e)
            null
        }
    }

    /**
     * Get all loans
     */
    fun getAllLoans(): List<LoanRecord> {
        return try {
            val loansJson = prefs.getString(KEY_LOANS, "{}")
            val loansMap = gson.fromJson(loansJson, Map::class.java) as? Map<String, Any>
            
            val loans = mutableListOf<LoanRecord>()
            for ((_, loanData) in loansMap ?: emptyMap()) {
                val loan = gson.fromJson(gson.toJson(loanData), LoanRecord::class.java)
                loans.add(loan)
            }
            loans
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all loans", e)
            emptyList()
        }
    }

    /**
     * Get payment by ID
     */
    fun getPayment(paymentId: String): PaymentRecord? {
        return try {
            val paymentsJson = prefs.getString(KEY_PAYMENTS, "{}")
            val paymentsMap = gson.fromJson(paymentsJson, Map::class.java) as? Map<String, Any>
            val paymentData = paymentsMap?.get(paymentId) as? Map<String, Any>
            
            if (paymentData != null) {
                gson.fromJson(gson.toJson(paymentData), PaymentRecord::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting payment", e)
            null
        }
    }

    /**
     * Get payment by device ID
     */
    fun getPaymentByDeviceId(deviceId: String): PaymentRecord? {
        return try {
            val paymentsJson = prefs.getString(KEY_PAYMENTS, "{}")
            val paymentsMap = gson.fromJson(paymentsJson, Map::class.java) as? Map<String, Any>
            
            for ((_, paymentData) in paymentsMap ?: emptyMap()) {
                val payment = gson.fromJson(gson.toJson(paymentData), PaymentRecord::class.java)
                if (payment.deviceId == deviceId) {
                    return payment
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting payment by device ID", e)
            null
        }
    }

    /**
     * Get all payments
     */
    fun getAllPayments(): List<PaymentRecord> {
        return try {
            val paymentsJson = prefs.getString(KEY_PAYMENTS, "{}")
            val paymentsMap = gson.fromJson(paymentsJson, Map::class.java) as? Map<String, Any>
            
            val payments = mutableListOf<PaymentRecord>()
            for ((_, paymentData) in paymentsMap ?: emptyMap()) {
                val payment = gson.fromJson(gson.toJson(paymentData), PaymentRecord::class.java)
                payments.add(payment)
            }
            payments
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all payments", e)
            emptyList()
        }
    }

    /**
     * Update loan status
     */
    fun updateLoanStatus(loanId: String, newStatus: String): Boolean {
        return try {
            val loan = getLoan(loanId) ?: return false
            val updatedLoan = loan.copy(
                loanStatus = newStatus,
                updatedAt = System.currentTimeMillis()
            )
            
            val loansJson = prefs.getString(KEY_LOANS, "{}")
            val loansMap = gson.fromJson(loansJson, Map::class.java) as? MutableMap<String, Any>
                ?: mutableMapOf()
            
            loansMap[loanId] = gson.fromJson(gson.toJson(updatedLoan), Map::class.java)
            prefs.edit().putString(KEY_LOANS, gson.toJson(loansMap)).apply()
            
            Log.d(TAG, "Loan status updated: $loanId -> $newStatus")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating loan status", e)
            false
        }
    }

    /**
     * Update payment status
     */
    fun updatePaymentStatus(paymentId: String, newStatus: String): Boolean {
        return try {
            val payment = getPayment(paymentId) ?: return false
            val updatedPayment = payment.copy(
                paymentStatus = newStatus,
                updatedAt = System.currentTimeMillis()
            )
            
            val paymentsJson = prefs.getString(KEY_PAYMENTS, "{}")
            val paymentsMap = gson.fromJson(paymentsJson, Map::class.java) as? MutableMap<String, Any>
                ?: mutableMapOf()
            
            paymentsMap[paymentId] = gson.fromJson(gson.toJson(updatedPayment), Map::class.java)
            prefs.edit().putString(KEY_PAYMENTS, gson.toJson(paymentsMap)).apply()
            
            Log.d(TAG, "Payment status updated: $paymentId -> $newStatus")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating payment status", e)
            false
        }
    }

    /**
     * Get days overdue for a loan
     */
    fun getDaysOverdue(loanId: String): Int {
        return try {
            val loan = getLoan(loanId) ?: return 0
            
            if (loan.loanStatus == "PAID") {
                return 0
            }
            
            val now = System.currentTimeMillis()
            val daysOverdue = ((now - loan.dueDate) / (24 * 60 * 60 * 1000)).toInt()
            
            return if (daysOverdue > 0) daysOverdue else 0
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating days overdue", e)
            0
        }
    }

    /**
     * Get days until due for a loan
     */
    fun getDaysUntilDue(loanId: String): Int {
        return try {
            val loan = getLoan(loanId) ?: return 0
            
            if (loan.loanStatus == "PAID") {
                return 0
            }
            
            val now = System.currentTimeMillis()
            val daysUntilDue = ((loan.dueDate - now) / (24 * 60 * 60 * 1000)).toInt()
            
            return if (daysUntilDue > 0) daysUntilDue else 0
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating days until due", e)
            0
        }
    }

    /**
     * Check if loan is overdue
     */
    fun isLoanOverdue(loanId: String): Boolean {
        return try {
            val loan = getLoan(loanId) ?: return false
            loan.loanStatus == "OVERDUE" || loan.loanStatus == "DEFAULTED"
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if loan is overdue", e)
            false
        }
    }

    /**
     * Check if loan is defaulted
     */
    fun isLoanDefaulted(loanId: String): Boolean {
        return try {
            val loan = getLoan(loanId) ?: return false
            loan.loanStatus == "DEFAULTED"
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if loan is defaulted", e)
            false
        }
    }

    /**
     * Get all overdue loans
     */
    fun getOverdueLoans(): List<LoanRecord> {
        return getAllLoans().filter { it.loanStatus == "OVERDUE" || it.loanStatus == "DEFAULTED" }
    }

    /**
     * Get all active loans
     */
    fun getActiveLoans(): List<LoanRecord> {
        return getAllLoans().filter { it.loanStatus == "ACTIVE" }
    }

    /**
     * Get all paid loans
     */
    fun getPaidLoans(): List<LoanRecord> {
        return getAllLoans().filter { it.loanStatus == "PAID" }
    }
}

