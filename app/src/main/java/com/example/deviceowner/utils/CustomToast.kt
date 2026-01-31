package com.example.deviceowner.utils

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * Custom Toast utility with different colors for different message types
 */
object CustomToast {
    
    enum class ToastType {
        SUCCESS,    // Green - for successful operations
        ERROR,      // Red - for errors and failures
        WARNING,    // Orange/Yellow - for warnings
        INFO        // Blue - for information
    }
    
    /**
     * Show a custom colored toast message with icon
     */
    fun show(context: Context, message: String, type: ToastType, duration: Int = Toast.LENGTH_LONG) {
        try {
            val toast = Toast(context)
            
            // Create container layout
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(24, 20, 24, 20)
                gravity = Gravity.CENTER_VERTICAL
                background = createToastBackground(type)
            }
            
            // Add icon
            val icon = ImageView(context).apply {
                val iconRes = when (type) {
                    ToastType.SUCCESS -> android.R.drawable.ic_dialog_info // You can replace with custom icons
                    ToastType.ERROR -> android.R.drawable.ic_dialog_alert
                    ToastType.WARNING -> android.R.drawable.ic_dialog_info
                    ToastType.INFO -> android.R.drawable.ic_dialog_info
                }
                setImageResource(iconRes)
                setColorFilter(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(48, 48).apply {
                    setMargins(0, 0, 16, 0)
                }
            }
            
            // Create text view
            val textView = TextView(context).apply {
                text = message
                textSize = 16f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            
            container.addView(icon)
            container.addView(textView)
            
            toast.view = container
            toast.duration = duration
            toast.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 200)
            toast.show()
            
        } catch (e: Exception) {
            // Fallback to regular toast if custom toast fails
            Toast.makeText(context, message, duration).show()
        }
    }
    
    /**
     * Show success toast (green) - for registration success, etc.
     */
    fun showSuccess(context: Context, message: String, duration: Int = Toast.LENGTH_LONG) {
        show(context, message, ToastType.SUCCESS, duration)
    }
    
    /**
     * Show error toast (red) - for registration failures, network errors, etc.
     */
    fun showError(context: Context, message: String, duration: Int = Toast.LENGTH_LONG) {
        show(context, message, ToastType.ERROR, duration)
    }
    
    /**
     * Show warning toast (orange) - for validation warnings, etc.
     */
    fun showWarning(context: Context, message: String, duration: Int = Toast.LENGTH_LONG) {
        show(context, message, ToastType.WARNING, duration)
    }
    
    /**
     * Show info toast (blue) - for general information
     */
    fun showInfo(context: Context, message: String, duration: Int = Toast.LENGTH_LONG) {
        show(context, message, ToastType.INFO, duration)
    }
    
    /**
     * Create background drawable with appropriate color and styling
     */
    private fun createToastBackground(type: ToastType): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE
        drawable.cornerRadius = 28f
        
        // Set color based on toast type with better colors
        val color = when (type) {
            ToastType.SUCCESS -> Color.parseColor("#4CAF50")  // Material Green
            ToastType.ERROR -> Color.parseColor("#F44336")    // Material Red
            ToastType.WARNING -> Color.parseColor("#FF9800")  // Material Orange
            ToastType.INFO -> Color.parseColor("#2196F3")     // Material Blue
        }
        
        drawable.setColor(color)
        
        // Add subtle shadow effect with stroke
        drawable.setStroke(1, Color.parseColor("#40000000"))
        
        return drawable
    }
    
    /**
     * Quick methods for common registration scenarios
     */
    object Registration {
        fun success(context: Context) {
            showSuccess(context, "✅ Device registered successfully!")
        }
        
        fun failed(context: Context, reason: String = "Registration failed") {
            showError(context, "❌ $reason")
        }
        
        fun networkError(context: Context) {
            showError(context, "🌐 Network error. Check your connection and try again.")
        }
        
        fun invalidLoan(context: Context) {
            showError(context, "📋 Invalid loan number. Please check and try again.")
        }
        
        fun serverError(context: Context) {
            showError(context, "🔧 Server error. Please try again later.")
        }
        
        fun alreadyRegistered(context: Context) {
            showWarning(context, "⚠️ Device already registered with this loan number.")
        }
    }
    
    /**
     * Demo function to test all toast types - for development/testing only
     */
    object Demo {
        fun showAllToastTypes(context: Context) {
            showSuccess(context, "✅ Registration successful!")
            
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                showError(context, "❌ Registration failed - network error")
            }, 1000)
            
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                showWarning(context, "⚠️ Invalid loan number format")
            }, 2000)
            
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                showInfo(context, "ℹ️ Processing your request...")
            }, 3000)
        }
    }
    
    /**
     * Device validation specific methods
     */
    object DeviceValidation {
        fun failed(context: Context, errors: List<String>) {
            val message = if (errors.size == 1) {
                "❌ Device validation failed: ${errors.first()}"
            } else {
                "❌ Device validation failed:\n${errors.joinToString("\n• ", "• ")}"
            }
            showError(context, message)
        }
        
        fun missingImei(context: Context) {
            showError(context, "📱 IMEI number is not available - cannot register device")
        }
        
        fun missingSerial(context: Context) {
            showError(context, "🔢 Serial number is not available - cannot register device")
        }
        
        fun missingCriticalInfo(context: Context) {
            showError(context, "⚠️ Critical device information is missing - registration blocked")
        }
        
        fun passed(context: Context) {
            showSuccess(context, "✅ Device validation passed - ready for registration")
        }
    }
}