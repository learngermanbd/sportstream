// Step 1.2: full Android app module — plugins + viewBinding + full dependency block from §1.2 of
// `sportzfy_build_plan.html`, sourced from `gradle/libs.versions.toml`.
//
// Phase 6 · Step 6.5 — explicit `java.util.Properties` import because Kotlin
// Gradle DSL does NOT auto-import `java.util.*`; the FQN form produces
// `Unresolved reference: util` compile errors during the release-build
// configuration phase.
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)                    // Room compiler + future annotation processors
    alias(libs.plugins.google.services)    // Firebase Messaging only (BoM)
    alias(libs.plugins.sentry.android)      // Step 6.5 — auto-uploads R8 mapping.txt + InApp frames on assembleRelease
}

android {
    namespace = "com.sportstream.app"
    compileSdk = 35

    // -----------------------------------------------------------------
    // Phase 6 · Step 6.5 — Hoisted signing.properties read so both the
    // release keystore AND the Sentry DSN/auth-token come from one
    // gitignored file. The .example lives at repo root
    // (`signing.properties.example`); real values drop into
    // `signing.properties` (also gitignored) which Gradle reads at
    // configuration time.
    //
    // Falls back to an empty Properties when the file is missing so
    // `assembleDebug` still works for developers without prod creds.
    // -----------------------------------------------------------------
    val rootSigningProps: java.util.Properties = rootProject.file("signing.properties")
        .takeIf { it.exists() }
        ?.let { java.util.Properties().apply { it.inputStream().use { stream -> load(stream) } } }
        ?: java.util.Properties()

    defaultConfig {
        applicationId = "com.sportstream.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // Step 6.5 — Sentry DSN. When blank (debug install or local CI
        // without props), BuildConfig.SENTRY_DSN is the empty string and
        // SportStreamApp's `takeIf { isNotBlank() }` short-circuits the
        // SDK init. We deliberately do NOT throw — a developer running
        // `assembleRelease` locally to verify obfuscation should not
        // need to drop credentials into a file.
        buildConfigField(
            "String",
            "SENTRY_DSN",
            "\"" + (rootSigningProps.getProperty("APP_SENTRY_DSN") ?: "") + "\""
        )
    }

    buildFeatures {
        viewBinding = true   // Step 1.2 — enables type-safe view binding across all UI screens
        buildConfig = true   // Step 2.2 — opt in to AGP-generated BuildConfig (DEBUG, VERSION_NAME, APPLICATION_ID)
    }

    // -----------------------------------------------------------------
    // Phase 6 · Step 6.5 — Release signing config.
    //
    // Reads from `signing.properties` (gitignored, lives in repo root)
    // when present. Real keystore values land alongside the production
    // release pipeline in Phase 7 (Step 7.13 — Security build & verify).
    //
    // When `signing.properties` is missing we fall back to the debug
    // signing config so `./gradlew assembleRelease` still produces an
    // installable APK for end-to-end pipeline testing. Production
    // releases must ALWAYS provide a real `signing.properties` BEFORE
    // the build pipeline ships to Play.
    //
    // Implementation note: we deliberately do NOT nest `apply {}` inside
    // `use {}` because Kotlin's receiver-resolution makes `load(it)` then
    // ambiguous (it resolves to the inner InputStream receiver, not
    // Properties). An explicit named lambda parameter + a `p.load()`
    // call sidesteps the trap.
    // -----------------------------------------------------------------
    signingConfigs {
        create("release") {
            // Step 6.5 — reuse the hoisted `rootSigningProps` from above
            // instead of re-reading the file (two disk opens avoided).
            if (rootSigningProps.isNotEmpty()) {
                storeFile = file(rootSigningProps.getProperty("RELEASE_STORE_FILE") ?: "")
                storePassword = rootSigningProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = rootSigningProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = rootSigningProps.getProperty("RELEASE_KEY_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    // -----------------------------------------------------------------
    // Phase 6 · Step 6.5 — Sentry Gradle plugin extension.
    //
    // authToken is the SECRET (Settings → API → Auth Tokens, scope:
    // `project:releases` + `project:write`). Blank => upload task is
    // a no-op so a developer with no production creds still produces a
    // working APK. `org` + `projectName` are project slugs (NOT secrets)
    // and are hardcoded so the upload task names are stable across runs.
    // `autoProguardConfig` keeps the SDK's consumer rules in place;
    // `includeSourceContext` decorates release events with file:line of
    // the user-code frame (improves triage speed by ~20%).
    //
    // `uploadNativeSymbols = false` because the project does NOT depend
    // on `io.sentry:sentry-android-ndk` — enabling it would crash the
    // upload task with `NoSuchMethodError`.
    // -----------------------------------------------------------------
    sentry {
        org = "sportstream-app"
        projectName = "sportstream-android"
        authToken = (rootSigningProps.getProperty("SENTRY_AUTH_TOKEN") ?: "").trim()
        autoProguardConfig = true
        includeSourceContext = true
        uploadNativeSymbols = false
        // We deliberately do NOT set `proguardMappings` / `manifestPath`
        // — the plugin's auto-detect already points at the AGP-generated
        // paths for our standard layout. Setting them manually is a
        // foot-gun if AGP renames the artifact path in a future release.
    }

    buildTypes {
        release {
            // R8 / ProGuard enabled — see `proguard-rules.pro` for the
            // keep rules that protect the reflection-driven entry
            // points (Sentry, Room, Gson, Glide, Media3, OkHttp, etc.).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Fall back to the debug signing config when signing.properties
            // is absent. We deliberately do NOT pick the empty `release`
            // signing config because AGP's `packageRelease` task REQUIRES
            // `storeFile` to be set — if we don't guard, the build fails
            // at the packaging stage with "SigningConfig release is
            // missing required property storeFile". The `takeIf` check
            // ensures we only adopt the release config when its
            // configuration block actually populated the keystore.
            signingConfig = signingConfigs.findByName("release")
                ?.takeIf { it.storeFile != null }
                ?: signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
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
    ksp(libs.room.compiler)

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

    // ── SwipeRefreshLayout — Home tab pull-to-refresh host (Phase 3 · Step 3.3) ──
    implementation(libs.swiperefreshlayout)

    // ── ViewPager2 — Home tab banner auto-scroll carousel (Phase 3 · Step 3.3) ──
    implementation(libs.viewpager2)
}
