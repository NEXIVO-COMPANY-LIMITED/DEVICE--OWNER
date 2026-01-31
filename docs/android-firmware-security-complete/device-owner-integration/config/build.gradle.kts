/**
 * build.gradle.kts Configuration for Device Owner + Firmware Security
 * ===================================================================
 * 
 * PURPOSE: Configure your Device Owner app to build native library
 * LOCATION: app/build.gradle.kts
 * 
 * INSTRUCTIONS:
 * 1. Merge this with your existing build.gradle.kts
 * 2. Add the android.externalNativeBuild section
 * 3. Add the ndk.abiFilters to defaultConfig
 * 4. Rebuild project
 */

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yourcompany.deviceowner"
    compileSdk = 34
    
    defaultConfig {
        applicationId = "com.yourcompany.deviceowner"
        minSdk = 26  // Android 8.0 minimum
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        
        // IMPORTANT: Add NDK ABI filters
        ndk {
            // Support 64-bit ARM (most modern phones)
            // and 32-bit ARM (older devices)
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            // Don't strip native library symbols in release
            // (they're needed for JNI calls)
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
        debug {
            isDebuggable = true
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }
    
    // CRITICAL: Enable CMake for native builds
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // Ensure native libraries are included
            useLegacyPackaging = false
        }
    }
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // Lifecycle & ViewModel
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Networking (Retrofit)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // JSON
    implementation("com.google.code.gson:gson:2.10.1")
    
    // WorkManager for background tasks
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

// Task to verify native library after build
tasks.register("verifyNativeLibrary") {
    doLast {
        val apkFile = file("build/outputs/apk/debug/app-debug.apk")
        if (apkFile.exists()) {
            println("✓ APK built: ${apkFile.absolutePath}")
            
            // Verify native library is included
            val zipFile = java.util.zip.ZipFile(apkFile)
            val hasArm64 = zipFile.getEntry("lib/arm64-v8a/libfirmware_security.so") != null
            val hasArm32 = zipFile.getEntry("lib/armeabi-v7a/libfirmware_security.so") != null
            
            if (hasArm64 || hasArm32) {
                println("✓ Native library included:")
                if (hasArm64) println("  - lib/arm64-v8a/libfirmware_security.so")
                if (hasArm32) println("  - lib/armeabi-v7a/libfirmware_security.so")
            } else {
                println("✗ WARNING: Native library NOT found in APK!")
                println("  Check CMake build output for errors")
            }
            
            zipFile.close()
        } else {
            println("✗ APK not found. Run './gradlew assembleDebug' first")
        }
    }
}
