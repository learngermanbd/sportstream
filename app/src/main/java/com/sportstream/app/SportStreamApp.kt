package com.sportstream.app

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.preferences.preferencesDataStore
import com.sportstream.app.data.local.LocalModule
import com.sportstream.app.data.prefs.ThemePrefs
import com.sportstream.app.data.prefs.UpdatePrefs
import com.sportstream.app.data.remote.AppConfig
import com.sportstream.app.data.remote.NetworkModule
import com.sportstream.app.data.remote.RemoteConfigHelper
import com.sportstream.app.data.repository.RepositoryModule
import com.sportstream.app.data.update.AppUpdateManager
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.runBlocking

/**
 * Application entry-point. Initialised once per process.
 *
 * Phase 0 ✓ — environment wired (JDK 17 · Android SDK 35 · Gradle 8.11.1).
 * Phase 1 ✓ — Project scaffold, dependencies, theme, FCM+Sentry init.
 * Phase 2 ✓ — Network + Local + Repository DI seams.
 * Phase 3 ✓ — Activities + Fragments + Adapters + UI flows.
 * Phase 4 ✓ — ExoPlayer + PiP + gestures + SubTitle/Quality selectors.
 * Phase 5 ✓ — Favorites + Playlists + Network Stream + Push + Notice v2 + Search.
 * Phase 6 · Step 6.2 — exposes [updateManager] + [updatePrefs] for the
 *   auto-update flow. Phase 6.2 review-pass MAJOR: `network`, `local`,
 *   `repository` are now LAZY (replacing the previous `lateinit var` form)
 *   so the [com.sportstream.app.services.UpdateDownloadReceiver] does
 *   NOT race [Application.onCreate] when a queued
 *   `ACTION_DOWNLOAD_COMPLETE` broadcast fires before the assignment runs.
 */
class SportStreamApp : Application() {

    /**
     * 1. Sentry first — captures initialization crashes of downstream steps.
     */
    private fun initSentry() {
        SentryAndroid.init(this) { options ->
            options.dsn = SENTRY_DSN.takeIf { it.isNotBlank() }
            options.isDebug = false
            options.tracesSampleRate = 0.1
        }
    }

    override fun onCreate() {
        super.onCreate()
        initSentry()
        // We deliberately do NOT initialize Firebase here — FirebaseApp
        // auto-initializes from the `google-services.json` plugin and we
        // only rely on FirebaseMessaging (Phase 5 · Step 5.4).
        Log.i(TAG, "Resolved API Base URL: ${AppConfig.defaults().apiBaseUrl}")

        // Phase 6 · Step 6.4 — apply persisted theme BEFORE any
        // Activity inflates so the first Activity launched after a
        // cold start holds the correct uiMode (no dark→light flash
        // on launch). DataStore.fist() returns from a tiny on-disk
        // blob (~10 ms typical, never blocks more than ~50 ms).
        //
        // TODO(stephand): if the team enables Android StrictMode
        // `detectDiskReads()` later, move this read into a
        // ContentProvider.onCreate (which fires before
        // Application.onCreate, before StrictMode policies activate
        // at attachBaseContext()). For v1 we accept the main-thread
        // DataStore read since no StrictMode policy is active.
        val themeMode = runBlocking { themePrefs.currentMode() }
        AppCompatDelegate.setDefaultNightMode(themePrefs.toNightModeFlag(themeMode))

        // Touch the lazy seams so onCreate's cold start still gets the
        // same observable behaviour as the previous lateinit version
        // (any FCM push that arrives during cold launch lands with the
        // cache + auth interceptor already wired). Lazy access in
        // onCreate is equivalent to the old explicit assignment but
        // doesn't expose an uninitialised-property window to the
        // UpdateDownloadReceiver.
        network
        local
        repository
    }

    /**
     * Phase 6 · Step 6.2 — exposed DI seams are LAZY (replacing the
     * previous `lateinit var` form) so the [UpdateDownloadReceiver]
     * doesn't race [Application.onCreate]. First access initialises the
     * module; subsequent accesses reuse the same instance.
     */
    val network: NetworkModule by lazy { NetworkModule(this) }
    val local: LocalModule by lazy { LocalModule(this) }
    val repository: RepositoryModule by lazy {
        RepositoryModule(
            remoteDataSource = network.remoteDataSource,
            localDataSource = local.localDataSource,
            httpClient = network.httpClient,
            noticeDaoProvider = { local.noticeDao },
        )
    }

    /**
     * Phase 6 · Step 6.2 — Coordinator object for the auto-update
     * pipeline. Lazy; tied to the [NetworkModule.httpClient] so we
     * share the cache + auth interceptor stack.
     */
    val updateManager: AppUpdateManager by lazy {
        AppUpdateManager(applicationContext, network.httpClient)
    }

    /**
     * Phase 6 · Step 6.2 — Persisted storage for the "Last dismissed
     * optional update" guard. Lazy; cheap to construct because DataStore
     * wraps the file handler on first read.
     */
    val updatePrefs: UpdatePrefs by lazy {
        UpdatePrefs(applicationContext)
    }

    /**
     * Phase 6 · Step 6.4 — Persisted storage for the user's theme
     * preference (System / Light / Dark). Lazily constructed; the first
     * access happens during [onCreate] to push the stored uiMode into
     * [androidx.appcompat.app.AppCompatDelegate] BEFORE any Activity
     * inflates. The DataStore read (via [ThemePrefs.currentMode]) is
     * resolved with [runBlocking] from [onCreate] — acceptable
     * because the read returns from a tiny on-disk blob in a few ms.
     */
    val themePrefs: ThemePrefs by lazy {
        ThemePrefs(applicationContext)
    }

    companion object {
        private const val TAG = "SportStreamApp"

        /**
         * Remote-config DataStore extension. Used by [RemoteConfigHelper]
         * to persist /api/config payloads across launches.
         */
        val Context.remoteConfigDataStore by preferencesDataStore(
            name = "sportstream_remote_config"
        )

        /** Sentry DSN — left blank intentionally; we'll wire a real one in Phase 7. */
        private const val SENTRY_DSN = ""
    }
}
