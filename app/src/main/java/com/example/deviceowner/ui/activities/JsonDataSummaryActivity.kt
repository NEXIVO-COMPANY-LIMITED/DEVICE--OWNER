package com.example.deviceowner.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.deviceowner.R
import com.example.deviceowner.utils.logging.LogManager
import com.example.deviceowner.utils.logging.LogViewer
import com.example.deviceowner.utils.CustomToast
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * JSON Data Summary Activity
 * Provides a comprehensive overview of all JSON data sent to server
 */
class JsonDataSummaryActivity : AppCompatActivity() {
    private lateinit var summaryTextView: TextView
    private lateinit var refreshButton: Button
    private lateinit var viewAllButton: Button
    
    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, JsonDataSummaryActivity::class.java)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize LogManager if not already done
        LogManager.initialize(this)
        
        createLayout()
        loadSummaryData()
    }
    
    private fun createLayout() {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        
        // Title
        val titleView = TextView(this).apply {
            text = "📊 JSON Data Summary"
            textSize = 24f
            setPadding(0, 0, 0, 16)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        mainLayout.addView(titleView)
        
        // Description
        val descView = TextView(this).apply {
            text = "All data sent to server is automatically saved in JSON format for debugging and analysis."
            textSize = 14f
            setPadding(0, 0, 0, 16)
            setTextColor(android.graphics.Color.GRAY)
        }
        mainLayout.addView(descView)
        
        // Action buttons
        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }
        
        refreshButton = Button(this).apply {
            text = "🔄 Refresh"
            setOnClickListener { loadSummaryData() }
        }
        buttonLayout.addView(refreshButton)
        
        viewAllButton = Button(this).apply {
            text = "📄 View All Logs"
            setOnClickListener { 
                startActivity(Intent(this@JsonDataSummaryActivity, LogViewerActivity::class.java))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = 16
            }
        }
        buttonLayout.addView(viewAllButton)
        
        mainLayout.addView(buttonLayout)
        
        // Summary content
        summaryTextView = TextView(this).apply {
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            text = "Loading summary..."
            setPadding(16, 16, 16, 16)
            setBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"))
        }
        
        val scrollView = ScrollView(this).apply {
            addView(summaryTextView)
        }
        
        mainLayout.addView(scrollView)
        setContentView(mainLayout)
    }
    
    private fun loadSummaryData() {
        lifecycleScope.launch {
            try {
                summaryTextView.text = "Loading summary..."
                
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                
                val summary = buildString {
                    appendLine("📊 LOG DATA SUMMARY")
                    appendLine("=" * 50)
                    appendLine()
                    
                    appendLine("📅 Generated: ${dateFormat.format(Date())}")
                    appendLine("📁 Log Directory: ${LogManager.getLogDirectoryPath()}")
                    appendLine()
                    
                    // Get statistics for all log categories
                    val stats = LogViewer.getLogStatistics()
                    appendLine("📋 LOG OVERVIEW:")
                    appendLine("-" * 30)
                    appendLine("📄 Total Files: ${stats["totalFiles"]}")
                    appendLine("💾 Total Size: ${formatFileSize(stats["totalSize"] as Long)}")
                    appendLine()
                    
                    appendLine("📂 LOG CATEGORIES:")
                    appendLine("-" * 30)
                    
                    LogManager.LogCategory.values().forEach { category ->
                        val files = LogManager.getLogFiles(category)
                        val categorySize = files.sumOf { it.length() }
                        
                        appendLine("${getCategoryIcon(category)} ${category.name}:")
                        appendLine("   Files: ${files.size}")
                        appendLine("   Size: ${formatFileSize(categorySize)}")
                        
                        if (files.isNotEmpty()) {
                            val latestFile = files.maxByOrNull { it.lastModified() }
                            latestFile?.let {
                                appendLine("   Latest: ${dateFormat.format(Date(it.lastModified()))}")
                            }
                        }
                        appendLine()
                    }
                    
                    appendLine("🔍 RECENT ACTIVITY:")
                    appendLine("-" * 30)
                    
                    // Show recent errors
                    val recentErrors = LogViewer.getRecentErrors(24)
                    if (recentErrors.isNotBlank()) {
                        appendLine("⚠️ Recent Errors (24h):")
                        appendLine(recentErrors.take(500)) // Limit to first 500 chars
                        if (recentErrors.length > 500) {
                            appendLine("... (truncated, view full logs for more)")
                        }
                        appendLine()
                    } else {
                        appendLine("✅ No recent errors found")
                        appendLine()
                    }
                    
                    appendLine("🔧 ACTIONS AVAILABLE:")
                    appendLine("-" * 30)
                    appendLine("• Tap 'View All Logs' to see complete log files")
                    appendLine("• Use Log Viewer for filtering and sharing")
                    appendLine("• All device activities are automatically logged")
                    appendLine("• Logs include registration, heartbeat, security, and API calls")
                }
                
                summaryTextView.text = summary
                
            } catch (e: Exception) {
                summaryTextView.text = "Error loading summary: ${e.message}\n\n${e.stackTraceToString()}"
                CustomToast.showError(this@JsonDataSummaryActivity, "Error loading summary: ${e.message}")
            }
        }
    }
    
    private fun getCategoryIcon(category: LogManager.LogCategory): String {
        return when (category) {
            LogManager.LogCategory.DEVICE_REGISTRATION -> "📝"
            LogManager.LogCategory.HEARTBEAT -> "💓"
            LogManager.LogCategory.API_CALLS -> "🌐"
            LogManager.LogCategory.SECURITY -> "🔒"
            LogManager.LogCategory.DEVICE_OWNER -> "👑"
            LogManager.LogCategory.DEVICE_INFO -> "📱"
            LogManager.LogCategory.PROVISIONING -> "⚙️"
            LogManager.LogCategory.SYNC -> "🔄"
            LogManager.LogCategory.ERRORS -> "❌"
            LogManager.LogCategory.GENERAL -> "📋"
        }
    }
    
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
    
    private operator fun String.times(n: Int): String = this.repeat(n)
}