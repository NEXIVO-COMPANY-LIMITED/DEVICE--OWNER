package com.example.deviceowner.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.deviceowner.managers.*
import com.example.deviceowner.models.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Demo activity for Feature 4.4: Remote Lock/Unlock
 * Tests lock types, PIN verification, offline queueing, and loan integration
 */
class LockDemoActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LockDemoActivity"
    }

    private lateinit var remoteLockManager: RemoteLockManager
    private lateinit var loanManager: LoanManager
    private lateinit var lockCommandHandler: LockCommandHandler
    private lateinit var logView: TextView
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set status bar icons to black
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        
        // Initialize managers
        remoteLockManager = RemoteLockManager(this)
        loanManager = LoanManager(this)
        lockCommandHandler = LockCommandHandler(this)

        // Create UI
        createUI()
        
        // Log initialization
        addLog("✓ Lock Demo Activity initialized")
        displayLoanData()
    }

    private fun createUI() {
        val scrollView = ScrollView(this)
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        // Title
        mainLayout.addView(TextView(this).apply {
            text = "Feature 4.4: Remote Lock/Unlock Demo"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        // Log view
        logView = TextView(this).apply {
            text = "Logs:\n"
            textSize = 12f
            setBackgroundColor(android.graphics.Color.parseColor("#f0f0f0"))
            setPadding(8, 8, 8, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                400
            )
        }
        mainLayout.addView(logView)

        // Buttons
        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Soft Lock Test
        buttonLayout.addView(createButton("Test Soft Lock") {
            testSoftLock()
        })

        // Hard Lock Test
        buttonLayout.addView(createButton("Test Hard Lock") {
            testHardLock()
        })

        // Permanent Lock Test
        buttonLayout.addView(createButton("Test Permanent Lock") {
            testPermanentLock()
        })

        // Loan-Based Lock Test
        buttonLayout.addView(createButton("Test Loan-Based Lock") {
            testLoanBasedLock()
        })

        // PIN Unlock Test
        buttonLayout.addView(createButton("Test PIN Unlock") {
            testPinUnlock()
        })

        // Queue Lock Test
        buttonLayout.addView(createButton("Test Queue Lock") {
            testQueueLock()
        })

        // Process Queued Commands
        buttonLayout.addView(createButton("Process Queued Commands") {
            processQueuedCommands()
        })

        // Show Loan Status
        buttonLayout.addView(createButton("Show Loan Status") {
            displayLoanData()
        })

        // Clear All Locks
        buttonLayout.addView(createButton("Clear All Locks") {
            clearAllLocks()
        })

        mainLayout.addView(buttonLayout)
        scrollView.addView(mainLayout)
        setContentView(scrollView)
    }

    private fun createButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 8)
            }
        }
    }

    private fun testSoftLock() {
        addLog("Testing Soft Lock...")
        val lock = DeviceLock(
            lockId = "SOFT_LOCK_${System.currentTimeMillis()}",
            deviceId = "device_001",
            lockType = LockType.SOFT,
            lockStatus = LockStatus.ACTIVE,
            lockReason = LockReason.LOAN_OVERDUE,
            message = "Your loan payment is overdue. Please make payment to continue using the device.",
            pinRequired = false
        )
        
        val success = remoteLockManager.applyLock(lock)
        addLog(if (success) "✓ Soft lock applied" else "✗ Soft lock failed")
    }

    private fun testHardLock() {
        addLog("Testing Hard Lock...")
        val lock = DeviceLock(
            lockId = "HARD_LOCK_${System.currentTimeMillis()}",
            deviceId = "device_002",
            lockType = LockType.HARD,
            lockStatus = LockStatus.ACTIVE,
            lockReason = LockReason.PAYMENT_DEFAULT,
            message = "Device locked due to payment default. Contact support.",
            pinRequired = true,
            pinHash = hashPin("1234")
        )
        
        val success = remoteLockManager.applyLock(lock)
        addLog(if (success) "✓ Hard lock applied" else "✗ Hard lock failed")
    }

    private fun testPermanentLock() {
        addLog("Testing Permanent Lock...")
        val lock = DeviceLock(
            lockId = "PERM_LOCK_${System.currentTimeMillis()}",
            deviceId = "device_003",
            lockType = LockType.PERMANENT,
            lockStatus = LockStatus.ACTIVE,
            lockReason = LockReason.LOAN_OVERDUE,
            message = "Device locked for repossession. Backend unlock required.",
            backendUnlockOnly = true
        )
        
        val success = remoteLockManager.applyLock(lock)
        addLog(if (success) "✓ Permanent lock applied" else "✗ Permanent lock failed")
    }

    private fun testLoanBasedLock() {
        addLog("Testing Loan-Based Lock...")
        val result = lockCommandHandler.handleLoanLock("device_002")
        addLog("Loan lock result: ${result.status} - ${result.message}")
    }

    private fun testPinUnlock() {
        addLog("Testing PIN Unlock...")
        
        // First apply a lock with PIN
        val lock = DeviceLock(
            lockId = "PIN_LOCK_${System.currentTimeMillis()}",
            deviceId = "device_004",
            lockType = LockType.HARD,
            lockStatus = LockStatus.ACTIVE,
            lockReason = LockReason.MANUAL_LOCK,
            message = "Device locked with PIN",
            pinRequired = true,
            pinHash = hashPin("5678")
        )
        
        remoteLockManager.applyLock(lock)
        addLog("Lock applied with PIN: 5678")
        
        // Try to unlock with correct PIN
        val unlockSuccess = remoteLockManager.unlockWithPin(lock.lockId, "5678")
        addLog(if (unlockSuccess) "✓ PIN unlock successful" else "✗ PIN unlock failed")
    }

    private fun testQueueLock() {
        addLog("Testing Queue Lock (Offline)...")
        
        val command = LockCommand(
            commandId = "QUEUE_LOCK_${System.currentTimeMillis()}",
            deviceId = "device_005",
            lockType = LockType.SOFT,
            reason = LockReason.LOAN_OVERDUE,
            message = "Queued lock for offline execution"
        )
        
        val success = remoteLockManager.queueLockCommand(command)
        addLog(if (success) "✓ Lock command queued" else "✗ Queue failed")
        
        // Show queue status
        val queue = remoteLockManager.getActiveLocks()
        addLog("Active locks: ${queue.size}")
    }

    private fun processQueuedCommands() {
        addLog("Processing Queued Commands...")
        remoteLockManager.processQueuedLockCommands()
        remoteLockManager.processQueuedUnlockCommands()
        addLog("✓ Queued commands processed")
    }

    private fun displayLoanData() {
        addLog("\n=== LOAN DATA ===")
        val loans = loanManager.getAllLoans()
        
        for (loan in loans) {
            addLog("\nLoan: ${loan.loanId}")
            addLog("  Device: ${loan.deviceId}")
            addLog("  Amount: ${loan.loanAmount} ${loan.currency}")
            addLog("  Status: ${loan.loanStatus}")
            addLog("  Due: ${formatDate(loan.dueDate)}")
            
            if (loan.loanStatus == "OVERDUE" || loan.loanStatus == "DEFAULTED") {
                val daysOverdue = loanManager.getDaysOverdue(loan.loanId)
                addLog("  Days Overdue: $daysOverdue")
            }
        }
        
        // Show overdue loans
        val overdueLoans = loanManager.getOverdueLoans()
        addLog("\nOverdue Loans: ${overdueLoans.size}")
        for (loan in overdueLoans) {
            addLog("  - ${loan.loanId}: ${loan.loanStatus}")
        }
    }

    private fun clearAllLocks() {
        addLog("Clearing all locks...")
        val locks = remoteLockManager.getActiveLocks()
        for ((lockId, _) in locks) {
            remoteLockManager.removeLock(lockId)
        }
        addLog("✓ All locks cleared (${locks.size} removed)")
    }

    private fun hashPin(pin: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(pin.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun formatDate(timestamp: Long): String {
        return dateFormat.format(Date(timestamp))
    }

    private fun addLog(message: String) {
        Log.d(TAG, message)
        val timestamp = dateFormat.format(Date())
        logView.append("[$timestamp] $message\n")
    }
}
