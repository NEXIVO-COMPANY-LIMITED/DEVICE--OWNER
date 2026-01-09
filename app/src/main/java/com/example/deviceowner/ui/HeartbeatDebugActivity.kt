package com.example.deviceowner.ui

import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.deviceowner.R
import com.example.deviceowner.utils.HeartbeatValidator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Debug activity to validate heartbeat data collection and sending
 * Use this to test heartbeat functionality without IMEI/Serial Number
 */
class HeartbeatDebugActivity : AppCompatActivity() {
    
    private lateinit var validator: HeartbeatValidator
    private lateinit var outputText: TextView
    private lateinit var scrollView: ScrollView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set status bar icons to black
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        
        setContentView(R.layout.activity_heartbeat_debug)
        
        validator = HeartbeatValidator(this)
        
        outputText = findViewById(R.id.output_text)
        scrollView = findViewById(R.id.scroll_view)
        
        // Validate data collection
        findViewById<Button>(R.id.btn_validate_data).setOnClickListener {
            validateHeartbeatData()
        }
        
        // Validate payload format
        findViewById<Button>(R.id.btn_validate_payload).setOnClickListener {
            validatePayload()
        }
        
        // Test heartbeat sending
        findViewById<Button>(R.id.btn_test_sending).setOnClickListener {
            testHeartbeatSending()
        }
        
        // View history
        findViewById<Button>(R.id.btn_view_history).setOnClickListener {
            viewHeartbeatHistory()
        }
        
        // Clear output
        findViewById<Button>(R.id.btn_clear).setOnClickListener {
            outputText.text = ""
        }
    }
    
    private fun validateHeartbeatData() {
        appendOutput("=== VALIDATING HEARTBEAT DATA ===\n")
        
        lifecycleScope.launch {
            val report = validator.validateHeartbeatData()
            
            appendOutput("Status: ${report.status}\n")
            appendOutput("All Checks Passed: ${report.allChecksPassed}\n\n")
            
            appendOutput("Validation Checks:\n")
            for (check in report.checks) {
                val status = if (check.passed) "✓" else "✗"
                val required = if (check.required) "[REQUIRED]" else "[OPTIONAL]"
                appendOutput("$status ${check.name} $required\n")
                appendOutput("   Value: ${check.value}\n")
            }
            
            if (report.error != null) {
                appendOutput("\nError: ${report.error}\n")
            }
            
            appendOutput("\n")
        }
    }
    
    private fun validatePayload() {
        appendOutput("=== VALIDATING HEARTBEAT PAYLOAD ===\n")
        
        lifecycleScope.launch {
            val report = validator.validateHeartbeatPayload()
            
            appendOutput("Status: ${report.status}\n")
            appendOutput("All Fields Valid: ${report.allFieldsValid}\n\n")
            
            appendOutput("Payload Fields:\n")
            for (check in report.checks) {
                val status = if (!check.isEmpty) "✓" else "✗"
                val required = if (check.required) "[REQUIRED]" else "[OPTIONAL]"
                appendOutput("$status ${check.field} $required\n")
                appendOutput("   Value: ${check.value}\n")
            }
            
            if (report.error != null) {
                appendOutput("\nError: ${report.error}\n")
            }
            
            appendOutput("\n")
        }
    }
    
    private fun testHeartbeatSending() {
        appendOutput("=== TESTING HEARTBEAT SENDING ===\n")
        appendOutput("Sending test heartbeat...\n\n")
        
        lifecycleScope.launch {
            val result = validator.testHeartbeatSending()
            
            appendOutput("Status: ${result.status}\n")
            appendOutput("HTTP Status Code: ${result.statusCode}\n")
            appendOutput("Is Successful: ${result.isSuccessful}\n")
            appendOutput("Data Matches: ${result.dataMatches}\n")
            appendOutput("Verified: ${result.verified}\n")
            
            if (result.message != null) {
                appendOutput("Message: ${result.message}\n")
            }
            
            if (result.error != null) {
                appendOutput("Error: ${result.error}\n")
            }
            
            appendOutput("\n")
        }
    }
    
    private fun viewHeartbeatHistory() {
        appendOutput("=== HEARTBEAT HISTORY ===\n")
        
        val history = validator.getHeartbeatHistory()
        
        if (history.isEmpty()) {
            appendOutput("No heartbeat history found\n\n")
            return
        }
        
        appendOutput("Total entries: ${history.size}\n\n")
        
        for ((index, entry) in history.takeLast(10).withIndex()) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val date = dateFormat.format(Date(entry.timestamp))
            
            appendOutput("Entry ${index + 1}:\n")
            appendOutput("  Timestamp: $date\n")
            appendOutput("  Device ID: ${entry.deviceId}\n")
            appendOutput("  Android ID: ${entry.androidId}\n")
            appendOutput("  Manufacturer: ${entry.manufacturer}\n")
            appendOutput("  Model: ${entry.model}\n")
            appendOutput("  Rooted: ${entry.isRooted}\n")
            appendOutput("  USB Debugging: ${entry.usbDebugging}\n")
            appendOutput("  Developer Mode: ${entry.developerMode}\n\n")
        }
    }
    
    private fun appendOutput(text: String) {
        runOnUiThread {
            outputText.append(text)
            scrollView.post {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }
}
