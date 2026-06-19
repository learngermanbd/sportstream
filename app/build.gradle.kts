// Step 1.2: full Android app module — plugins + viewBinding + full dependency block from §1.2 of
// `sportzfy_build_plan.html`, sourced from `gradle/libs.versions.toml`.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)        // Room compiler + future annotation processors
    alias(libs.plugins.google.services)    // Firebase Messaging only (BoM)
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
        viewBinding = true   // Step 1.2 — enables type-safe view binding across all UI screens
        buildConfig = true   // Step 2.2 — opt in to AGP-generated BuildConfig (DEBUG, VERSION_NAME, APPLICATION_ID)
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
    // ── Media3 — video playback (Phase 4 surface; dep lands now so Step 1.3 build resolves) ──
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)

    // ── OkHttp — networking (used by ApiClient and Media3 HLS/DASH source) ──
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // ── Room — local database (Favorites, Playlists, CachedEvent) ──
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    // ── Firebase — messaging ONLY (FCM push notifications) ──
    // Remote Config intentionally replaced by our own /api/config endpoint (Phase 8).
    // Crashlytics intentionally replaced by Sentry (below).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // ── Sentry — crash reporting (replaces Firebase Crashlytics) ──
    implementation(libs.sentry.android)

    // ── Lottie — animations (splash loader, player overlays) ──
    implementation(libs.lottie)

    // ── Material Design 3 (components used from Phase 3 onward) ──
    implementation(libs.material)

    // ── Navigation (Phase 3 — Home / Categories / Highlights graph) ──
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)

    // ── Lifecycle (ViewModels, LiveData) ──
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)

    // ── DataStore — preferences (AppConfig cache, recent searches, video quality memory) ──
    implementation(libs.datastore.preferences)

    // ── Glide — image loading (team logos, channel logos, highlight thumbnails) ──
    implementation(libs.glide)

    // ── Coroutines — async primitives (Phase 2 ViewModels) ──
    implementation(libs.kotlinx.coroutines.android)

    // ── Gson — @SerializedName annotations on Phase 2 data models (parse via org.json for now) ──
    implementation(libs.gson)
}
