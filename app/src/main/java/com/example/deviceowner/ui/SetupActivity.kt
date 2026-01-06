package com.example.deviceowner.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.deviceowner.R
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class SetupActivity : AppCompatActivity() {
    
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private val TAG = "SetupActivity"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_setup)
            
            viewPager = findViewById(R.id.setup_viewpager)
            tabLayout = findViewById(R.id.setup_tabs)
            
            // Null safety check
            if (viewPager == null || tabLayout == null) {
                Log.e(TAG, "Critical: ViewPager or TabLayout not found in layout")
                finish()
                return
            }
            
            // Create adapter with setup steps
            val adapter = SetupPagerAdapter(this)
            viewPager.adapter = adapter
            
            // Connect tabs with viewpager
            TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                tab.text = when (position) {
                    0 -> "User Info"
                    1 -> "Device Info"
                    2 -> "Verification"
                    else -> "Step ${position + 1}"
                }
            }.attach()
            
            // Disable tab clicking - only swipe
            tabLayout.isEnabled = false
        } catch (e: Exception) {
            Log.e(TAG, "Error in SetupActivity.onCreate: ${e.message}", e)
            finish()
        }
    }
}
