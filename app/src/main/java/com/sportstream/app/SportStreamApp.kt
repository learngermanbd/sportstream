package com.sportstream.app

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.messaging.FirebaseMessaging
import io.sentry.android.core.SentryAndroid
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * SportStream Application. Phase 1 \u00b7 Step 1.4.
 *
 * Init order matters:
 *  1. Sentry first so init-time crashes are captured.
 *  2. FirebaseMessaging next (the ONLY Firebase service we use \u2014 Remote Config
 *     is replaced by RemoteConfigHelper calling /api/config in data/remote/).
 *  3. OkHttpClient + DataStore<Preferences> exposed as singletons below.
 */
class SportStreamApp : Application() {

    /** Shared OkHttp client. Reused by ApiClient (Phase 2) and Media3 (Phase 4). */
    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        // 1. Sentry \u2014 crash reporter (replaces Firebase Crashlytics).
        //    DSN is a placeholder; real DSN lands in Step 6.5 (release prep).
        SentryAndroid.init(this) { options ->
            options.dsn = SENTRY_DSN
        }

        // 2. Firebase Cloud Messaging \u2014 the only Firebase service.
        //    isAutoInitEnabled ensures FCM starts token registration on app launch.
        FirebaseMessaging.getInstance().isAutoInitEnabled = true
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
