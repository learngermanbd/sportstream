package com.sportstream.admin

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import io.sentry.android.core.SentryAndroid
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Phase 8 \u00b7 Step 8.13 \u2014 SportStream Admin Application.
 *
 * Init order matters:
 *  1. Sentry first \u2014 admin crashes still get logged (placeholder DSN
 *     until Step 6.5 wires the real one).
 *  2. Lazy `httpClient` (30 s timeouts) is exposed for the [data.AdminApi]
 *     singleton.
 *  3. DataStore<Preferences> extension backs the JWT token persistence.
 *
 * Distinct from `com.sportstream.app.SportStreamApp` (the user app) \u2014 sharing
 * would tangle two unrelated code-bases. Each module has its own Application.
 */
class SportStreamAdminApp : Application() {

    /** Shared OkHttp client for admin API calls (mirrors :app's NetworkModule). */
    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        // 1. Sentry \u2014 same DSN until release prep swaps it; admins need crash
        //    logging too so we can spot field failures.
        SentryAndroid.init(this) { options ->
            options.dsn = SENTRY_DSN
        }
    }

    companion object {
        /** Placeholder Sentry DSN (mirrors user app's). Real DSN \u2192 Step 6.5. */
        private const val SENTRY_DSN = "https://examplePublicKey@o0.ingest.sentry.io/0"

        /** Backend base URL. Real admin DNS lands in Phase 8 \u00b7 Step 8.10. */
        const val ADMIN_API_BASE_URL = "https://sportstream.example.com"

        /**
         * DataStore for the admin JWT + cached credentials.
         * distinct from user app's `sportstream_remote_config` store so the
         * two apps stay independent.
         */
        val Context.adminTokenStore by preferencesDataStore(
            name = "sportstream_admin_tokens"
        )
    }
}
