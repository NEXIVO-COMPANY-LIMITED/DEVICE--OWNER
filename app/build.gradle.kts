import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    id("com.google.devtools.ksp") version "1.9.25-1.0.20"
    id("org.jetbrains.kotlin.plugin.parcelize") version "1.9.25"
}

android {
    namespace = "com.example.deviceowner"
    compileSdk = 35

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
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        buildConfigField("String", "BASE_URL", "\"https://payoplan.com/\"")
        buildConfigField("Boolean", "ENABLE_LOGGING", "true")
        buildConfigField("Boolean", "DEBUG", "true")
        buildConfigField("String", "API_VERSION", "\"v1\"")
        buildConfigField("String", "DEVICE_API_KEY", "\"8f3d2c9a7b1e4f6d5a9c2b3e7f1d4a6c9b8e0f2a1d3c4b5e6f7a8b9c0d1e2f3a\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"https://payoplan.com/\"")
            buildConfigField("Boolean", "ENABLE_LOGGING", "true")
            buildConfigField("Boolean", "DEBUG", "true")
            buildConfigField("String", "API_VERSION", "\"v1\"")
            buildConfigField("String", "DEVICE_API_KEY", "\"8f3d2c9a7b1e4f6d5a9c2b3e7f1d4a6c9b8e0f2a1d3c4b5e6f7a8b9c0d1e2f3a\"")
            isDebuggable = true
        }
        
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            
            buildConfigField("String", "BASE_URL", "\"https://payoplan.com/\"")
            buildConfigField("Boolean", "ENABLE_LOGGING", "true")
            buildConfigField("Boolean", "DEBUG", "false")
            buildConfigField("String", "API_VERSION", "\"v1\"")
            buildConfigField("String", "DEVICE_API_KEY", "\"8f3d2c9a7b1e4f6d5a9c2b3e7f1d4a6c9b8e0f2a1d3c4b5e6f7a8b9c0d1e2f3a\"")
        }
    }
    
    buildFeatures {
        compose = true
        buildConfig = true
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

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:-deprecation")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.runtime)
    implementation(libs.compose.activity)
    implementation(libs.compose.icons)
    
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    
    implementation(libs.workmanager)
    
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)
    
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    
    implementation(libs.playservices.location)
    implementation(libs.androidx.security.crypto)
    implementation(libs.coil.compose)
    
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.inline)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlin.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
