package com.sportstream.app.data.remote

/**
 * Payload returned by `GET /api/config` on the SportStream admin backend.
 *
 * Phase 1 \u00b7 Step 1.4. The real backend endpoint is implemented in Phase 8 \u00b7 Step 8.2.
 * Until then, RemoteConfigHelper hits a placeholder URL and falls back to [defaults].
 */
data class AppConfig(
    /** Base URL for the content API. e.g. `https://api.example.com/api` */
    val apiBaseUrl: String,
    /** URL the same-version-check redirects to for app updates. */
    val updateUrl: String,
    /** Telegram channel link surfaced in the drawer's "Join Us" entry. */
    val telegramLink: String,
    /** Free-form banner notice text shown on app splash / home top bar. */
    val noticeText: String,
    /** When true, the app shows a blocking maintenance screen. */
    val maintenanceMode: Boolean,
    /** Minimum app version that may connect. Older clients get HTTP 426. */
    val minAppVersion: String,
    /** Experimental feature toggles (e.g. "premium_enabled", "chat_enabled"). */
    val featureFlags: Map<String, Boolean>
) {
    companion object {
        /**
         * Startup fallback used when /api/config cannot be fetched AND no cache exists.
         * All URLs point at the placeholder domain; the app surfaces a network-error
         * card (Step 3.1) instead of silently using offline defaults.
         */
        fun defaults() = AppConfig(
            apiBaseUrl      = "https://learngermanwith.fun/api",
            updateUrl       = "https://learngermanwith.fun/update",
            telegramLink    = "https://t.me/sportstream",
            noticeText      = "",
            maintenanceMode = false,
            minAppVersion   = "1.0.0",
            featureFlags    = emptyMap()
        )
    }
}
