package com.sportstream.app.ui.activities

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sportstream.app.R
import com.sportstream.app.SportStreamApp
import com.sportstream.app.databinding.ActivitySplashBinding
import com.sportstream.app.services.NotificationHelper
import com.sportstream.app.ui.viewmodels.MainViewModel

/**
 * Phase 3 · Step 3.1 — Splash screen.
 *
 * Phase 5 · Step 5.4 — On API 33+, the splash gate also requests
 * `POST_NOTIFICATIONS` permission so FCM-driven Live Events
 * (`NotificationHelper.showLiveEventNotification`) actually render.
 * On API < 33 the permission is implied at install; no request
 * needed. Idempotent — calls when already granted are silent no-ops.
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    private val mainVm: MainViewModel by lazy {
        val app = application as SportStreamApp
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(app.repository.mainRepository) as T
            }
        }
        ViewModelProvider(this, factory)[MainViewModel::class.java]
    }

    private val handler = Handler(Looper.getMainLooper())
    private val navDelayMs = 2_000L

    private val navigateRunnable = Runnable {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    /**
     * Phase 5 · Step 5.4 — POST_NOTIFICATIONS runner. We use the
     * modern Activity Result API to avoid `onRequestPermissionsResult`
     * boilerplate.
     */
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result ignored — UI only */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Phase 5 · Step 5.4 — Pre-create the FCM notification channels
        // before any push arrives. Idempotent and cheap.
        NotificationHelper.ensureChannels(applicationContext)

        // Phase 5 · Step 5.4 — runtime ask for POST_NOTIFICATIONS on
        // API 33+ so future Live Events render. The system dialog
        // shows once; if denied, we silently continue — notifications
        // simply won't surface, but the rest of the app works fine.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
            "1.0.0"
        }
        binding.versionText.text = getString(R.string.splash_version_template, version)

        binding.retryButton.setOnClickListener {
            binding.errorCard.visibility = View.GONE
            binding.loader.visibility = View.VISIBLE
            binding.statusText.text = getString(R.string.splash_loading)
            handler.removeCallbacks(navigateRunnable)
            handler.postDelayed(navigateRunnable, navDelayMs)
        }

        binding.wifiButton.setOnClickListener { openSettingsOrFallback(Settings.ACTION_WIFI_SETTINGS) }
        binding.mobileButton.setOnClickListener { openSettingsOrFallback(Settings.ACTION_DATA_USAGE_SETTINGS) }

        mainVm.load()

        handler.postDelayed(navigateRunnable, navDelayMs)
    }

    override fun onDestroy() {
        handler.removeCallbacks(navigateRunnable)
        super.onDestroy()
    }

    private fun openSettingsOrFallback(action: String) {
        try {
            startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            startActivity(
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
