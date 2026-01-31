package com.example.deviceowner.ui.activities

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.deviceowner.R
import com.example.deviceowner.utils.ConnectionTester
import com.example.deviceowner.utils.EndpointDiagnostic
import kotlinx.coroutines.launch

/**
 * Network Diagnostic Activity
 * Use this to test connectivity issues directly on your device
 */
class NetworkDiagnosticActivity : AppCompatActivity() {
    
    private lateinit var connectionTester: ConnectionTester
    private lateinit var endpointDiagnostic: EndpointDiagnostic
    private lateinit var resultsTextView: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var loanIdEditText: EditText
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create layout programmatically since we don't have the layout file
        createLayout()
        
        connectionTester = ConnectionTester(this)
        endpointDiagnostic = EndpointDiagnostic(this)
        
        // Auto-run basic diagnostics on start
        runBasicDiagnostics()
    }
    
    private fun createLayout() {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        // Title
        val titleTextView = TextView(this).apply {
            text = "Network Diagnostics"
            textSize = 20f
            setPadding(0, 0, 0, 32)
        }
        layout.addView(titleTextView)
        
        // Basic diagnostics button
        val basicDiagnosticsButton = Button(this).apply {
            text = "Run Basic Diagnostics"
            setOnClickListener { runBasicDiagnostics() }
        }
        layout.addView(basicDiagnosticsButton)
        
        // Loan ID input for registration test
        val loanIdLabel = TextView(this).apply {
            text = "Loan ID (for registration test):"
            setPadding(0, 32, 0, 8)
        }
        layout.addView(loanIdLabel)
        
        loanIdEditText = EditText(this).apply {
            hint = "Enter loan ID"
            setPadding(16, 16, 16, 16)
        }
        layout.addView(loanIdEditText)
        
        // Clear button
        val clearButton = Button(this).apply {
            text = "Clear Results"
            setOnClickListener { 
                resultsTextView.text = "Results will appear here..."
            }
        }
        layout.addView(clearButton)
        
        // Results text view in scroll view
        resultsTextView = TextView(this).apply {
            text = "Results will appear here..."
            textSize = 12f
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFF000000.toInt())
            setTextColor(0xFF00FF00.toInt()) // Green text on black background
            typeface = android.graphics.Typeface.MONOSPACE
        }
        
        scrollView = ScrollView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f // Take remaining space
            )
            setPadding(0, 32, 0, 0)
            addView(resultsTextView)
        }
        layout.addView(scrollView)
        
        setContentView(layout)
    }
    
    private fun runBasicDiagnostics() {
        resultsTextView.text = "Running diagnostics...\n"
        
        lifecycleScope.launch {
            try {
                val results = StringBuilder()
                
                // Run connection test
                results.appendLine("=== CONNECTION TEST ===")
                val connectionResult = connectionTester.performFullConnectionTest()
                connectionResult.details.forEach { detail ->
                    results.appendLine(detail)
                }
                
                results.appendLine("\n=== API ENDPOINT TEST ===")
                val apiResult = connectionTester.testApiEndpoint()
                results.appendLine("URL: ${apiResult.url}")
                results.appendLine("Response Code: ${apiResult.responseCode}")
                results.appendLine("Content Type: ${apiResult.contentType}")
                results.appendLine("Reachable: ${apiResult.isReachable}")
                if (apiResult.error != null) {
                    results.appendLine("Error: ${apiResult.error}")
                }
                if (apiResult.responsePreview.isNotEmpty()) {
                    results.appendLine("Response Preview: ${apiResult.responsePreview}")
                }
                
                results.appendLine("\n=== ENDPOINT DIAGNOSTIC REPORT ===")
                val diagnosticReport = endpointDiagnostic.generateDiagnosticReport()
                results.appendLine(diagnosticReport)
                
                resultsTextView.text = results.toString()
                
                // Scroll to bottom
                scrollView.post {
                    scrollView.fullScroll(ScrollView.FOCUS_DOWN)
                }
            } catch (e: Exception) {
                resultsTextView.text = "Diagnostic failed: ${e.message}\n${e.stackTraceToString()}"
            }
        }
    }
    
}