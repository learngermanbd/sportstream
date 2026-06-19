package com.sportstream.app

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.messaging.FirebaseMessaging
import com.sportstream.app.data.local.LocalModule
import com.sportstream.app.data.remote.NetworkModule
import com.sportstream.app.data.repository.RepositoryModule
import io.github.jan-tennert.supabase.SupabaseClient
import io.github.jan-tennert.supabase.createSupabaseClient
import io.github.jan-tennert.supabase.storage.Storage
import io.sentry.android.core.SentryAndroid

/**
 * SportStream Application. Phase 1 · Step 1.4 → Phase 2 · Step 2.3.
 *
 * Init order matters:
 *  1. Sentry first so init-time crashes are captured.
 *  2. FirebaseMessaging next (the ONLY Firebase service we use  Remote Config
 *     is replaced by RemoteConfigHelper calling /api/config in data/remote/).
 *  3. [NetworkModule] + [LocalModule]  owns OkHttpClient + ApiClient +
 *     ApiService + RemoteDataSource (lazy singletons via `app.network.*`) and
 *     AppDatabase + FavoriteDao + PlaylistDao + LocalDataSource
 *     (lazy singletons via `app.local.*`).
 */
class SportStreamApp : Application() {

    /** Remote-side DI seam. Lazy: OkHttpClient is built on first touch. */
    lateinit var network: NetworkModule
        private set

    /** Local-side DI seam. Lazy: Room database is built on first touch. */
    lateinit var local: LocalModule
        private set

    /** Repository-side DI seam. Lazy: built on first touch from the
     *  already-resolved network + local data sources. */
    lateinit var repository: RepositoryModule
        private set

    /**
     * Phase 1 · Step 1.4 — Supabase client (plan-compliance retro-fix).
     *
     * Exposes a `SupabaseClient` with the Storage module installed so
     * callers can access admin-uploaded assets via
     *   app.supabase.storage.from("sportstream-assets").publicDownloadUrl("events/foo.png")
     *
     * Plan Step 1.4 prompt box: "Initialize Supabase client for storage".
     * The URL + anon key are placeholder constants here — Phase 8 (admin
     * panel) ships the real Supabase project URL + anon key. Until then
     * any Storage call returns a network error rather than crashing.
     */
    lateinit var supabase: SupabaseClient
        private set

    override fun onCreate() {
        super.onCreate()

        // 1. Sentry  crash reporter (replaces Firebase Crashlytics).
        //    DSN is a placeholder; real DSN lands in Step 6.5 (release prep).
        SentryAndroid.init(this) { options ->
            options.dsn = SENTRY_DSN
        }

        // 2. Firebase Cloud Messaging  the only Firebase service.
        //    isAutoInitEnabled ensures FCM starts token registration on app launch.
        FirebaseMessaging.getInstance().isAutoInitEnabled = true

        // 3. Supabase client (Phase 1 · Step 1.4 retro-fix). Storage module
        //    pre-installed so admin-supplied assets (logos, thumbnails,
        //    banners — all in the sportstream-assets bucket per the Phase 8
        //    backend deep-dive) can be reached at app.supabase.storage. The
        //    URL + anon key are placeholder constants; Phase 8 (admin panel +
        //    Supabase project) replaces them with the project's real values.
        supabase = createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY
        ) {
            install(Storage)
        }

        // 4. DI seams  all 3 lazy, so they're cheap on cold start.
        network = NetworkModule(this)
        local = LocalModule(this)
        repository = RepositoryModule(
            remoteDataSource = network.remoteDataSource,
            localDataSource = local.localDataSource
        )
    }

    companion object {
        /** Sentry DSN. Replace at release time via gradle property / env. */
        private const val SENTRY_DSN = "https://examplePublicKey@o0.ingest.sentry.io/0"

        /**
         * Supabase project URL (Phase 1 · Step 1.4 retro-fix placeholder).
         * Phase 8 ships the real Supabase project URL; until then this
         * placeholder means Supabase Storage calls return network errors
         * instead of crashing the app.
         */
        private const val SUPABASE_URL = "https://example.supabase.co"

        /**
         * Supabase anon (publishable) key (Phase 1 · Step 1.4 retro-fix
         * placeholder). The real anon key lands with Phase 8 (admin panel).
         */
        private const val SUPABASE_ANON_KEY = "SUPABASE_ANON_KEY_PLACEHOLDER"

        /**
         * DataStore preferences extension property scoped to the Application context.
         * Backs the /api/config offline cache plus future Phase 5+ prefs (recent URLs,
         * last video quality, etc.).
         */
        val Context.remoteConfigDataStore by preferencesDataStore(
            name = "sportstream_remote_config"
        )
    }
}
