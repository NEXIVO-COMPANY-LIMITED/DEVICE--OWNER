package com.microspace.payo.utils.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Custom Toast utility with different colors for different message types
 */
object CustomToast {

    enum class ToastType {
        SUCCESS, ERROR, WARNING, INFO
    }

    fun show(context: Context, message: String, type: ToastType, duration: Int = Toast.LENGTH_LONG) {
        try {
            val toast = Toast(context)
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(24, 20, 24, 20)
                gravity = Gravity.CENTER_VERTICAL
                background = createToastBackground(type)
            }
            val icon = ImageView(context).apply {
                val iconRes = when (type) {
                    ToastType.SUCCESS -> android.R.drawable.ic_dialog_info
                    ToastType.ERROR -> android.R.drawable.ic_dialog_alert
                    ToastType.WARNING -> android.R.drawable.ic_dialog_info
                    ToastType.INFO -> android.R.drawable.ic_dialog_info
                }
                setImageResource(iconRes)
                setColorFilter(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(48, 48).apply { setMargins(0, 0, 16, 0) }
            }
            val textView = TextView(context).apply {
                text = message
                textSize = 16f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            container.addView(icon)
            container.addView(textView)
            toast.view = container
            toast.duration = duration
            toast.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 200)
            toast.show()
        } catch (e: Exception) {
            Toast.makeText(context, message, duration).show()
        }
    }

    fun showSuccess(context: Context, message: String, duration: Int = Toast.LENGTH_LONG) {
        show(context, message, ToastType.SUCCESS, duration)
    }

    fun showError(context: Context, message: String, duration: Int = Toast.LENGTH_LONG) {
        show(context, message, ToastType.ERROR, duration)
    }

    fun showWarning(context: Context, message: String, duration: Int = Toast.LENGTH_LONG) {
        show(context, message, ToastType.WARNING, duration)
    }

    fun showInfo(context: Context, message: String, duration: Int = Toast.LENGTH_LONG) {
        show(context, message, ToastType.INFO, duration)
    }

    private fun createToastBackground(type: ToastType): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE
        drawable.cornerRadius = 28f
        val color = when (type) {
            ToastType.SUCCESS -> Color.parseColor("#4CAF50")
            ToastType.ERROR -> Color.parseColor("#F44336")
            ToastType.WARNING -> Color.parseColor("#FF9800")
            ToastType.INFO -> Color.parseColor("#2196F3")
        }
        drawable.setColor(color)
        drawable.setStroke(1, Color.parseColor("#40000000"))
        return drawable
    }

    object Registration {
        fun success(context: Context) = showSuccess(context, "Device registered successfully!")
        fun failed(context: Context, reason: String = "Registration failed") = showError(context, reason)
        fun networkError(context: Context) = showError(context, "Network error. Check your connection and try again.")
        fun invalidLoan(context: Context) = showError(context, "Invalid loan number. Please check and try again.")
        fun serverError(context: Context) = showError(context, "Server error. Please try again later.")
        fun alreadyRegistered(context: Context) = showWarning(context, "Device already registered with this loan number.")
    }

    object DeviceValidation {
        fun failed(context: Context, errors: List<String>) {
            val message = if (errors.size == 1) "Device validation failed: ${errors.first()}"
            else "Device validation failed:\n${errors.joinToString("\n- ", "- ")}"
            showError(context, message)
        }
        fun missingImei(context: Context) = showError(context, "IMEI number is not available - cannot register device")
        fun missingSerial(context: Context) = showError(context, "Serial number is not available - cannot register device")
        fun missingCriticalInfo(context: Context) = showError(context, "Critical device information is missing - registration blocked")
        fun passed(context: Context) = showSuccess(context, "Device validation passed - ready for registration")
    }
}
