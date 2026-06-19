package com.sportstream.app

import android.app.Application

/**
 * Application class.
 * Step 1.4 will initialize FirebaseMessaging (FCM only) and Sentry here, and register
 * RemoteConfigHelper for the /api/config endpoint (replaces Firebase Remote Config).
 */
class SportStreamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // TODO: Step 1.4 — FirebaseMessaging + Sentry + RemoteConfigHelper prefill.
    }
}
