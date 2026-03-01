package com.microspace.payo.ui.activities.provisioning.compatibility.screens

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.microspace.payo.R

/**
 * Polished Compatibility Failure Screen.
 * Explains requirements and provides support links.
 */
class CompatibilityFailureActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_compatibility_check)

        val issues = intent.getStringArrayExtra("issues") ?: emptyArray()
        
        val tvIssues = findViewById<TextView>(R.id.tv_failure_details)
        tvIssues?.text = if (issues.isNotEmpty()) {
            issues.joinToString("\n") { "â€¢ $it" }
        } else {
            "Device security standards not met."
        }

        findViewById<Button>(R.id.btn_exit_app)?.setOnClickListener {
            finishAffinity()
        }

        findViewById<TextView>(R.id.btn_security_standards)?.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://support.payo.com/requirements"))
            runCatching { startActivity(intent) }
        }
    }

    override fun onBackPressed() {
        finishAffinity()
    }
}




