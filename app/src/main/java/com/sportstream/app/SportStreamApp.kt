package com.sportstream.app

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.messaging.FirebaseMessaging
import com.sportstream.app.data.local.LocalModule
import com.sportstream.app.data.remote.NetworkModule
import com.sportstream.app.data.repository.RepositoryModule
import io.sentry.android.core.SentryAndroid
import android.util.Log

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

        // 3. DI seams  all 3 lazy, so they're cheap on cold start.
        network = NetworkModule(this)
        local = LocalModule(this)
        repository = RepositoryModule(
            remoteDataSource = network.remoteDataSource,
            localDataSource = local.localDataSource
        )
            Log.i("SportStreamApp", "Resolved API Base URL: ${com.sportstream.app.data.remote.AppConfig.defaults().apiBaseUrl}")
}

    companion object {
        /** Sentry DSN. Replace at release time via gradle property / env. */
        private const val SENTRY_DSN = "https://examplePublicKey@o0.ingest.sentry.io/0"

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
