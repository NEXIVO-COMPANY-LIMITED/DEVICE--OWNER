package com.example.deviceowner.ui.activities

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.deviceowner.control.RemoteDeviceControlManager

/**
 * Test Activity to demonstrate different soft lock violation types
 * Shows how each violation type displays a customized message
 */
class SoftLockTestActivity : AppCompatActivity() {
    
    private lateinit var controlManager: RemoteDeviceControlManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        controlManager = RemoteDeviceControlManager(this)
        
        // Create UI programmatically
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        // Title
        val title = TextView(this).apply {
            text = "Soft Lock Violation Test"
            textSize = 20f
            setPadding(0, 0, 0, 32)
        }
        layout.addView(title)
        
        // Description
        val description = TextView(this).apply {
            text = "Test different violation types to see customized soft lock screens:"
            textSize = 14f
            setPadding(0, 0, 0, 24)
        }
        layout.addView(description)
        
        // Test buttons for different violation types
        createTestButton(layout, "Test App Uninstall Attempt", 
            "Unauthorized app uninstall attempt detected", "UNINSTALL_ATTEMPT")
        
        createTestButton(layout, "Test Data Clear Attempt", 
            "Unauthorized app data manipulation detected", "DATA_CLEAR_ATTEMPT")
        
        createTestButton(layout, "Test USB Debug Attempt", 
            "Unauthorized debug mode activation detected", "USB_DEBUG_ATTEMPT")
        
        createTestButton(layout, "Test Developer Mode Attempt", 
            "Unauthorized developer settings access detected", "DEVELOPER_MODE_ATTEMPT")
        
        createTestButton(layout, "Test Payment Overdue (HARD LOCK)", 
            "Payment overdue - device access restricted", "PAYMENT_OVERDUE")
        
        createTestButton(layout, "Test Security Violation", 
            "Unauthorized security modifications detected", "SECURITY_VIOLATION")
        
        createTestButton(layout, "Test Root Detection (HARD LOCK)", 
            "Device rooting detected and prohibited", "ROOT_DETECTION")
        
        createTestButton(layout, "Test Custom ROM (HARD LOCK)", 
            "Custom ROM installation detected", "CUSTOM_ROM_DETECTION")
        
        createTestButton(layout, "Test Data Mismatch (HARD LOCK)", 
            "Critical device data mismatches detected", "DATA_MISMATCH")
        
        // Direct hard lock test
        val hardLockButton = Button(this).apply {
            text = "Test Direct Hard Lock"
            setOnClickListener {
                controlManager.applyHardLock("Direct hard lock test - complete device lockdown")
            }
        }
        layout.addView(hardLockButton)
        
        // Unlock button
        val unlockButton = Button(this).apply {
            text = "UNLOCK DEVICE"
            setOnClickListener {
                controlManager.unlockDevice()
                finish()
            }
        }
        layout.addView(unlockButton)
        
        setContentView(layout)
    }
    
    private fun createTestButton(layout: LinearLayout, buttonText: String, reason: String, triggerAction: String) {
        val button = Button(this).apply {
            text = buttonText
            setOnClickListener {
                // Apply soft lock with specific trigger action
                controlManager.applySoftLock(reason, triggerAction)
            }
        }
        layout.addView(button)
    }
}