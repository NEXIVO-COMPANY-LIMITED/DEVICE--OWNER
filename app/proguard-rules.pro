# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\abuu\AppData\Local\Android\sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.

# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard/index.html

# Keep the entity classes used in the Room database
-keep class com.microspace.payo.data.local.database.entities.** { *; }

# Keep the DAO interfaces
-keep class com.microspace.payo.data.local.database.dao.** { *; }

# Keep Retrofit and related API classes
-keep interface com.microspace.payo.data.remote.ApiService { *; }
-keep class com.microspace.payo.data.remote.ApiClient { *; }
-keep class com.microspace.payo.data.remote.models.** { *; }
-keep class com.microspace.payo.data.models.** { *; }

# Keep the Device Admin Receiver
-keep class com.microspace.payo.receivers.admin.** { *; }
-keep class com.microspace.payo.receivers.boot.** { *; }
-keep class com.microspace.payo.receivers.payment.** { *; }
-keep class com.microspace.payo.receivers.security.** { *; }
-keep class com.microspace.payo.receivers.system.** { *; }

# Keep the Firmware Security logic
-keep class com.microspace.payo.security.firmware.FirmwareSecurity { *; }

# Keep GSON models
-keep class com.microspace.payo.utils.storage.PaymentDataManager$PaymentRecord { *; }

# Keep BuildConfig
-keep class com.microspace.payo.BuildConfig { *; }

# General Compose keep rules
-keep class androidx.compose.ui.platform.** { *; }

# Hilt/Dagger rules (if using)
-keep class * extends android.app.Service
-keep class * extends android.app.Application
-keep class * extends android.app.Activity
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.content.ContentProvider
