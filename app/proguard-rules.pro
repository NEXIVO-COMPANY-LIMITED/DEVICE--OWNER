# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable

# 1. Preserve signatures for Retrofit's reflection (CRITICAL FIX)
-keepattributes Signature, InnerClasses, EnclosingMethod, Exceptions
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

# 2. Keep your specific models (Based on your package: com.example.deviceowner)
-keep class com.example.deviceowner.data.models.** { *; }
-keep class com.example.deviceowner.data.remote.models.** { *; }

# 3. Keep your Retrofit Service and Client
-keep interface com.example.deviceowner.data.remote.ApiService { *; }
-keep class com.example.deviceowner.data.remote.ApiClient { *; }

# 4. Keep Retrofit and OkHttp internals
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**

# 5. Keep Gson @SerializedName fields
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 6. Keep Android System Components (Admin and Receivers)
-keep class com.example.deviceowner.receivers.** { *; }
-keep class * extends android.app.admin.DeviceAdminReceiver { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.app.Service { *; }

# 7. Keep Room Database and Entities
-keep class * extends androidx.room.RoomDatabase
-keep class com.example.deviceowner.data.local.database.dao.** { *; }
-keep class com.example.deviceowner.data.local.database.entities.** { *; }

# 8. Keep Coroutines and Compose metadata
-keep class kotlinx.coroutines.** { *; }
-keep class kotlin.Metadata { *; }

# 9. Firmware security (JNI) - docs/android-firmware-security-complete, deviceowner-firmware-integration-complete
-keep class com.example.deviceowner.security.firmware.FirmwareSecurity { *; }
-keep class com.example.deviceowner.security.firmware.FirmwareSecurity$* { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
