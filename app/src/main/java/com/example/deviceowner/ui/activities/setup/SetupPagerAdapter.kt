package com.example.deviceowner.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.deviceowner.ui.fragments.UserInfoFragment
import com.example.deviceowner.ui.fragments.DeviceInfoFragment
import com.example.deviceowner.ui.fragments.VerificationFragment

class SetupPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
    
    override fun getItemCount(): Int = 3
    
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> UserInfoFragment()
            1 -> DeviceInfoFragment()
            2 -> VerificationFragment()
            else -> UserInfoFragment()
        }
    }
}
