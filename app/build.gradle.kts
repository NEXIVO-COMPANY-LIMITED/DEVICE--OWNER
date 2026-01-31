import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.devtools.ksp") version "1.9.25-1.0.20"
    id("org.jetbrains.kotlin.plugin.parcelize") version "1.9.25"
}

android {
    namespace = "com.example.deviceowner"
    compileSdk = 36

    // Load keystore properties
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(keystorePropertiesFile.inputStream())
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.example.deviceowner"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
        
        // API Configuration - Using HTTPS for production server (Device Owner compatible)
        buildConfigField("String", "BASE_URL", "\"https://payoplan.com/\"")
        buildConfigField("Boolean", "ENABLE_LOGGING", "true")
        buildConfigField("String", "API_VERSION", "\"v1\"")
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    
    buildTypes {
        debug {
            // Development configuration - Using HTTPS for Device Owner compatibility
            buildConfigField("String", "BASE_URL", "\"https://payoplan.com/\"")
            buildConfigField("Boolean", "ENABLE_LOGGING", "true")
            buildConfigField("String", "API_VERSION", "\"v1\"")
            isDebuggable = true
        }
        
        release {
            // Production configuration - Using HTTPS for Device Owner compatibility
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            
            // Production API settings - Using HTTPS for Device Owner compatibility
            buildConfigField("String", "BASE_URL", "\"https://payoplan.com/\"")
            buildConfigField("Boolean", "ENABLE_LOGGING", "true") // Keep logging enabled for crash debugging
            buildConfigField("String", "API_VERSION", "\"v1\"")
        }
    }
    
    buildFeatures {
        compose = true
        buildConfig = true  // Enable BuildConfig generation
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    
    // Retrofit and OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    
    // Jetpack Compose
    implementation(libs.compose.ui)
    implementation(libs.compose.material)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.runtime)
    implementation(libs.compose.activity)
    implementation(libs.compose.icons)
    
    // Compose ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    
    // Room Database
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    ksp(libs.room.compiler)
    
    // WorkManager for background tasks
    implementation(libs.workmanager)
    
    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    
    // Lifecycle
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)
    
    // Google Play Services Location
    implementation(libs.playservices.location)
    
    // Test dependencies
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.inline)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlin.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
