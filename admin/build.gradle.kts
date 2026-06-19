// Phase 8 * Step 8.13 -- Android Admin app module.
//
//   applicationId = "com.sportstream.admin"
//   orange Material3 theme (distinct from the cyan user app)
//   ViewBinding on, but NO google-services plugin (no FCM for admins),
//   NO kapt (no Room yet), NO Media3 (no player UI in admin).
//
// Reuses the SAME `libs.versions.toml` as the user app for hygienic multi-
// module dependency management. Compose is deliberately NOT used here so we
// don't need to add compose-bom + 6 compose-* aliases to libs.versions.toml
// yet -- the admin UI is straightforward and XML+ViewBinding stays consistent
// with the user app's stack.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.sportstream.admin"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sportstream.admin"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        viewBinding = true   // Same pattern as :app
        buildConfig = true   // Needed for BuildConfig.DEBUG in AdminApi (mirrors :app)
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

dependencies {
    // -- Material 3 (orange theme for admin) --
    implementation(libs.material)

    // -- Sentry (same as :app -- admins crash too) --
    implementation(libs.sentry.android)

    // -- ViewModel + LiveData (login state, dashboard state) --
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)

    // -- Networking (mirrors :app's ApiClient) --
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // -- Coroutines --
    implementation(libs.kotlinx.coroutines.android)

    // -- DataStore Preferences (token persistence) --
    implementation(libs.datastore.preferences)

    // -- Gson (admin response payload mirrors :app) --
    implementation(libs.gson)

    // -- Framgement + Navigation (admin uses single-activity + fragments) --
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)
}
