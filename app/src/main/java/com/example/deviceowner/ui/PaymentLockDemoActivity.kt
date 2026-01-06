package com.example.deviceowner.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.deviceowner.R
import com.example.deviceowner.managers.PaymentUserLockManager
import com.example.deviceowner.models.LockType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Demo Activity for Feature 4.4: Remote Lock/Unlock
 * Showcases payment user lock enforcement with mocked data
 */
class PaymentLockDemoActivity : AppCompatActivity() {

    private lateinit var paymentLockManager: PaymentUserLockManager
    private lateinit var outputTextView: TextView
    private lateinit var pinInput: EditText
    private lateinit var deviceIdInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment_lock_demo)

        paymentLockManager = PaymentUserLockManager(this)

        // Initialize UI components
        outputTextView = findViewById(R.id.output_text)
        pinInput = findViewById(R.id.pin_input)
        deviceIdInput = findViewById(R.id.device_id_input)

        // Setup buttons
        setupButtons()

        // Display initial information
        displayMockedPaymentUsers()
    }

    private fun setupButtons() {
        // Show Mocked Users
        findViewById<Button>(R.id.btn_show_users).setOnClickListener {
            displayMockedPaymentUsers()
        }

        // Enforce Lock for Device 1 (ACTIVE)
        findViewById<Button>(R.id.btn_enforce_device1).setOnClickListener {
            enforceLockForDevice("device_001")
        }

        // Enforce Lock for Device 2 (OVERDUE)
        findViewById<Button>(R.id.btn_enforce_device2).setOnClickListener {
            enforceLockForDevice("device_002")
        }

        // Enforce Lock for Device 3 (DEFAULTED)
        findViewById<Button>(R.id.btn_enforce_device3).setOnClickListener {
            enforceLockForDevice("device_003")
        }

        // Enforce Lock for Device 4 (PAID)
        findViewById<Button>(R.id.btn_enforce_device4).setOnClickListener {
            enforceLockForDevice("device_004")
        }

        // Unlock with PIN
        findViewById<Button>(R.id.btn_unlock_pin).setOnClickListener {
            unlockWithPin()
        }

        // Unlock from Backend
        findViewById<Button>(R.id.btn_unlock_backend).setOnClickListener {
            unlockFromBackend()
        }

        // Show Lock Status
        findViewById<Button>(R.id.btn_show_status).setOnClickListener {
            showLockStatus()
        }

        // Show All Active Locks
        findViewById<Button>(R.id.btn_show_all_locks).setOnClickListener {
            showAllActiveLocks()
        }

        // Clear Output
        findViewById<Button>(R.id.btn_clear).setOnClickListener {
            outputTextView.text = ""
        }
    }

    private fun displayMockedPaymentUsers() {
        CoroutineScope(Dispatchers.Main).launch {
            val users = paymentLockManager.getMockedPaymentUsers()

            val output = buildString {
                append("═══════════════════════════════════════════════════════\n")
                append("MOCKED PAYMENT USERS - Feature 4.4 Demo\n")
                append("═══════════════════════════════════════════════════════\n\n")

                users.forEachIndexed { index, user ->
                    append("User ${index + 1}:\n")
                    append("  Device ID: ${user.deviceId}\n")
                    append("  User Ref: ${user.userRef}\n")
                    append("  Loan ID: ${user.loanId}\n")
                    append("  Loan Amount: ${String.format("%.0f", user.loanAmount)} ${user.currency}\n")
                    append("  Loan Status: ${user.loanStatus}\n")
                    append("  Payment Status: ${user.paymentStatus}\n")
                    append("  Days Overdue: ${user.daysOverdue}\n")
                    append("  Days Until Due: ${user.daysUntilDue}\n")
                    append("\n")
                }

                append("═══════════════════════════════════════════════════════\n")
                append("Lock Enforcement Rules:\n")
                append("  • ACTIVE (7 days to due): SOFT LOCK (warning overlay)\n")
                append("  • ACTIVE (due): HARD LOCK (device locked)\n")
                append("  • OVERDUE (< 30 days): HARD LOCK (device locked)\n")
                append("  • OVERDUE (≥ 30 days): PERMANENT LOCK (repossession)\n")
                append("  • DEFAULTED: PERMANENT LOCK (backend unlock only)\n")
                append("  • PAID: NO LOCK\n")
                append("═══════════════════════════════════════════════════════\n")
            }

            outputTextView.text = output
        }
    }

    private fun enforceLockForDevice(deviceId: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val output = buildString {
                append("═══════════════════════════════════════════════════════\n")
                append("ENFORCING LOCK FOR: $deviceId\n")
                append("═══════════════════════════════════════════════════════\n\n")

                val success = paymentLockManager.enforceLockForPaymentUser(deviceId)

                if (success) {
                    val lock = paymentLockManager.getPaymentLock(deviceId)
                    if (lock != null) {
                        append("✓ Lock Enforced Successfully\n\n")
                        append("Lock Details:\n")
                        append("  Lock ID: ${lock.lockId}\n")
                        append("  Lock Type: ${lock.lockType}\n")
                        append("  Status: ${lock.lockStatus}\n")
                        append("  Reason: ${lock.lockReason}\n")
                        append("  Message: ${lock.message}\n")
                        append("  PIN Required: ${lock.pinRequired}\n")
                        append("  Max Attempts: ${lock.maxAttempts}\n")
                        append("\n")

                        when (lock.lockType) {
                            LockType.SOFT -> {
                                append("PIN for unlock: 1234\n")
                            }
                            LockType.HARD -> {
                                append("PIN for unlock: 5678\n")
                            }
                            LockType.PERMANENT -> {
                                append("Backend unlock required (admin approval)\n")
                            }
                            else -> {}
                        }
                    } else {
                        append("✓ No lock needed for this device\n")
                    }
                } else {
                    append("✗ Failed to enforce lock\n")
                }

                append("\n═══════════════════════════════════════════════════════\n")
            }

            outputTextView.text = output
        }
    }

    private fun unlockWithPin() {
        CoroutineScope(Dispatchers.Main).launch {
            val deviceId = deviceIdInput.text.toString().trim()
            val pin = pinInput.text.toString().trim()

            if (deviceId.isEmpty() || pin.isEmpty()) {
                outputTextView.text = "Please enter Device ID and PIN"
                return@launch
            }

            val output = buildString {
                append("═══════════════════════════════════════════════════════\n")
                append("UNLOCK WITH PIN\n")
                append("═══════════════════════════════════════════════════════\n\n")
                append("Device ID: $deviceId\n")
                append("PIN: ${"*".repeat(pin.length)}\n\n")

                val success = paymentLockManager.unlockWithPin(deviceId, pin)

                if (success) {
                    append("✓ Device Unlocked Successfully\n\n")
                    append("The device has been unlocked.\n")
                    append("Lock has been removed from the system.\n")
                } else {
                    append("✗ Unlock Failed\n\n")
                    append("Invalid PIN or no active lock for device.\n")
                }

                append("\n═══════════════════════════════════════════════════════\n")
            }

            outputTextView.text = output
            pinInput.text.clear()
            deviceIdInput.text.clear()
        }
    }

    private fun unlockFromBackend() {
        CoroutineScope(Dispatchers.Main).launch {
            val deviceId = deviceIdInput.text.toString().trim()

            if (deviceId.isEmpty()) {
                outputTextView.text = "Please enter Device ID"
                return@launch
            }

            val output = buildString {
                append("═══════════════════════════════════════════════════════\n")
                append("BACKEND UNLOCK (Admin Approval)\n")
                append("═══════════════════════════════════════════════════════\n\n")
                append("Device ID: $deviceId\n\n")

                val success = paymentLockManager.unlockFromBackend(
                    deviceId,
                    "Admin approval - payment arrangement"
                )

                if (success) {
                    append("✓ Device Unlocked by Backend\n\n")
                    append("Admin has authorized unlock.\n")
                    append("Lock has been removed from the system.\n")
                } else {
                    append("✗ Backend Unlock Failed\n\n")
                    append("No active lock for device or error occurred.\n")
                }

                append("\n═══════════════════════════════════════════════════════\n")
            }

            outputTextView.text = output
            deviceIdInput.text.clear()
        }
    }

    private fun showLockStatus() {
        CoroutineScope(Dispatchers.Main).launch {
            val deviceId = deviceIdInput.text.toString().trim()

            if (deviceId.isEmpty()) {
                outputTextView.text = "Please enter Device ID"
                return@launch
            }

            val output = buildString {
                append("═══════════════════════════════════════════════════════\n")
                append("LOCK STATUS FOR: $deviceId\n")
                append("═══════════════════════════════════════════════════════\n\n")

                val lock = paymentLockManager.getPaymentLock(deviceId)

                if (lock != null) {
                    append("✓ Active Lock Found\n\n")
                    append("Lock ID: ${lock.lockId}\n")
                    append("Lock Type: ${lock.lockType}\n")
                    append("Status: ${lock.lockStatus}\n")
                    append("Reason: ${lock.lockReason}\n")
                    append("Message: ${lock.message}\n")
                    append("PIN Required: ${lock.pinRequired}\n")
                    append("Unlock Attempts: ${lock.unlockAttempts}/${lock.maxAttempts}\n")
                } else {
                    append("✗ No Active Lock\n\n")
                    append("Device is not currently locked.\n")
                }

                append("\n═══════════════════════════════════════════════════════\n")
            }

            outputTextView.text = output
            deviceIdInput.text.clear()
        }
    }

    private fun showAllActiveLocks() {
        CoroutineScope(Dispatchers.Main).launch {
            val locks = paymentLockManager.getAllPaymentLocks()

            val output = buildString {
                append("═══════════════════════════════════════════════════════\n")
                append("ALL ACTIVE PAYMENT LOCKS\n")
                append("═══════════════════════════════════════════════════════\n\n")

                if (locks.isEmpty()) {
                    append("No active locks\n")
                } else {
                    append("Total Active Locks: ${locks.size}\n\n")

                    locks.forEachIndexed { index, lock ->
                        append("Lock ${index + 1}:\n")
                        append("  Device ID: ${lock.deviceId}\n")
                        append("  Lock Type: ${lock.lockType}\n")
                        append("  Status: ${lock.lockStatus}\n")
                        append("  Reason: ${lock.lockReason}\n")
                        append("  PIN Required: ${lock.pinRequired}\n")
                        append("\n")
                    }
                }

                append("═══════════════════════════════════════════════════════\n")
            }

            outputTextView.text = output
        }
    }
}
