package com.sportstream.admin

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.sportstream.admin.BuildConfig
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Phase 8 \u00b7 Step 8.13 \u2014 SportStream Admin Application. Phase 6 \u00b7 Step 6.5
 * since added the real Sentry DSN wiring.
 *
 * Init order matters:
 *  1. Sentry first \u2014 admin crashes get reported in real time. DSN flows
 *     from [BuildConfig.SENTRY_DSN] (populated by `admin/build.gradle.kts`
 *     from `signing.properties \u2192 ADMIN_SENTRY_DSN`). Distinct from the
 *     user app's APP_SENTRY_DSN so admin events end up under a separate
 *     Sentry project + don't pollute user-app release health metrics.
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

        // Step 6.5 \u2014 admin uses the SAME hardened Sentry config as the
        // user app (sampleRate=1, PII scrub, debug-toggled session
        // tracking, thread dump attach) so an admin who clicks a 500
        // error in the dashboard gets the same forensic context as a
        // user-app crash.
        SentryAndroid.init(this) { options ->
            options.dsn = BuildConfig.SENTRY_DSN.takeIf { it.isNotBlank() }
            options.isDebug = BuildConfig.DEBUG
            options.sampleRate = 1.0
            options.tracesSampleRate = 0.0
            options.isSessionTrackingEnabled = !BuildConfig.DEBUG
            options.environment = if (BuildConfig.DEBUG) "debug" else "release"
            options.release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
            options.isAttachStacktrace = true
            options.isAttachThreads = true
            options.isAttachViewHierarchy = false
            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ -> scrubPii(event) }
            options.setTag("versionName", BuildConfig.VERSION_NAME)
            options.setTag("versionCode", BuildConfig.VERSION_CODE.toString())
            options.setTag("process", "admin-main")
        }
    }

    /**
     * Step 6.5 \u2014 same PII scrub rules as :app (Bearer / JWT / FCM /
     * Android-ID / Cookie). Admin events have AUTH/AUTHORIZATION
     * headers from the JWT we send on every API call \u2014 those MUST be
     * redacted before they reach Sentry's servers, otherwise a Sentry
     * dashboard screenshot posted to a bug tracker leaks the admin
     * session token.
     */
    private fun scrubPii(event: SentryEvent): SentryEvent? {
        val piiPatterns = listOf(
            Regex("(?i)(Bearer\\s+)[A-Za-z0-9\\-._~+/]+=*"),
            Regex("(?i)(FCM[_-]?Token[:= ]?)[A-Za-z0-9_\\-]{140,}"),
            Regex("(?i)(android[_-]?id[:= ]?)[A-Fa-f0-9\\-]{8,}"),
            Regex("(?i)(Cookie[:= ]?)[^\\s;]+"),
            // Admin-specific: JWT we send on every AdminApi call
            Regex("(?i)(Authorization[:= ]?)[A-Za-z0-9\\-._~+/]+=*"),
        )
        fun redact(input: String?): String? {
            if (input.isNullOrBlank()) return input
            var scrubbed = input
            piiPatterns.forEach { scrubbed = scrubbed.replace(it, "$1[Filtered]") }
            return scrubbed
        }
        event.message?.message?.let { event.message?.message = redact(it) }
        event.breadcrumbs?.forEach { bc ->
            bc.message?.let { bc.message = redact(it) }
            bc.data?.keys?.toList()?.forEach { k ->
                bc.data?.get(k)?.let { v ->
                    bc.data?.put(k, redact(v?.toString()) ?: v)
                }
            }
        }
        event.request?.headers?.let { headers ->
            headers.keys.toList().forEach { k ->
                if (k.equals("Authorization", true) ||
                    k.contains("Token", true) ||
                    k.contains("Cookie", true)
                ) {
                    headers[k] = "[Filtered]"
                }
            }
        }
        return event
    }

    companion object {
        // Step 6.5 \u2014 Sentry DSN flows from BuildConfig.SENTRY_DSN
        // (populated by admin/build.gradle.kts from
        // `signing.properties \u2192 ADMIN_SENTRY_DSN`). The companion's
        // old `private const val SENTRY_DSN = "https://examplePublicKey..."`
        // placeholder is gone \u2014 BuildConfig is the single source of
        // truth and ships empty when the developer doesn't have prod
        // creds (SDK no-ops cleanly).

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
