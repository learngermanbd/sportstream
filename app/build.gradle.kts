// Step 1.1: minimal Android app module — plugin coordinate + android{}+kotlinOptions only.
// Step 1.2 will add buildFeatures.viewBinding = true and the full dependency list.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Step 1.2 will add: alias(libs.plugins.google.services) for FCM.
}

android {
    namespace = "com.sportstream.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sportstream.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        // Step 1.2 will set viewBinding = true.
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Step 6.5 will fill proguardFiles(...) for release shrink/obfuscate.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
