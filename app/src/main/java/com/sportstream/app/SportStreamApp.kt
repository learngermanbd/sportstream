package com.sportstream.app

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.messaging.FirebaseMessaging
import com.sportstream.app.data.remote.NetworkModule
import io.sentry.android.core.SentryAndroid

/**
 * SportStream Application. Phase 1 · Step 1.4 → Phase 2 · Step 2.2.
 *
 * Init order matters:
 *  1. Sentry first so init-time crashes are captured.
 *  2. FirebaseMessaging next (the ONLY Firebase service we use — Remote Config
 *     is replaced by RemoteConfigHelper calling /api/config in data/remote/).
 *  3. [NetworkModule] last — owns OkHttpClient + ApiClient + ApiService +
 *     RemoteDataSource as lazy singletons reachable via `app.network.*`.
 */
class SportStreamApp : Application() {

    /** Shared dependency-injection seam for the remote-side singletons. */
    lateinit var network: NetworkModule
        private set

    override fun onCreate() {
        super.onCreate()

        // 1. Sentry — crash reporter (replaces Firebase Crashlytics).
        //    DSN is a placeholder; real DSN lands in Step 6.5 (release prep).
        SentryAndroid.init(this) { options ->
            options.dsn = SENTRY_DSN
        }

        // 2. Firebase Cloud Messaging — the only Firebase service.
        //    isAutoInitEnabled ensures FCM starts token registration on app launch.
        FirebaseMessaging.getInstance().isAutoInitEnabled = true

        // 3. Remote-side DI seam (httpClient + apiClient + apiService + remoteDataSource).
        network = NetworkModule(this)
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
