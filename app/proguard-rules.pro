# Preserve line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable

# 1. Preserve signatures for Retrofit and Gson reflection (CRITICAL)
-keepattributes Signature, InnerClasses, EnclosingMethod, Exceptions
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

# 2. Gson specific rules
-dontwarn com.google.gson.**
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements com.google.gson.TypeAdapterFactory
-keep public class * implements com.google.gson.JsonSerializer
-keep public class * implements com.google.gson.JsonDeserializer

# 3. Tink / Google Crypto - Fix for "Missing class" errors
-dontwarn com.google.api.client.http.**
-dontwarn com.google.api.client.json.**
-dontwarn com.google.crypto.tink.util.KeysDownloader
-dontwarn org.joda.time.**
-dontwarn com.google.protobuf.**

# 4. Keep your specific models
-keep class com.example.deviceowner.data.models.** { *; }
-keep class com.example.deviceowner.data.remote.models.** { *; }
-keep class com.example.deviceowner.utils.storage.PaymentDataManager$PaymentRecord { *; }

# 5. Keep Retrofit and OkHttp
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keep interface com.example.deviceowner.data.remote.ApiService { *; }
-keep class com.example.deviceowner.data.remote.ApiClient { *; }
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**

# 6. Android System Components
-keep class com.example.deviceowner.receivers.** { *; }
-keep class * extends android.app.admin.DeviceAdminReceiver { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.app.Service { *; }

# 7. Room Database
-keep class * extends androidx.room.RoomDatabase
-keep class com.example.deviceowner.data.local.database.dao.** { *; }
-keep class com.example.deviceowner.data.local.database.entities.** { *; }
-keep class * extends androidx.room.TypeConverter
-keepclassmembers class ** {
    @androidx.room.TypeConverter <methods>;
}

# 8. Coroutines and Compose
-keep class kotlinx.coroutines.** { *; }
-keep class kotlin.Metadata { *; }

# 9. Firmware security (JNI stubs)
-keep class com.example.deviceowner.security.firmware.FirmwareSecurity { *; }

# 10. BuildConfig
-keep class com.example.deviceowner.BuildConfig { *; }
