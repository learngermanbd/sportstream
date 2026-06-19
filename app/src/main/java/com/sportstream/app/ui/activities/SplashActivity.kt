package com.sportstream.app.ui.activities

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sportstream.app.R
import com.sportstream.app.SportStreamApp
import com.sportstream.app.databinding.ActivitySplashBinding
import com.sportstream.app.ui.viewmodels.MainViewModel

/**
 * Phase 3 · Step 3.1 — Splash screen.
 *
 * Visual stack: glass logo card + circular loader + status text + version
 * number at the bottom. The network-error card structure is in the layout
 * but initially gone; Step 3.5 wires it to the actual MainViewModel.Error
 * branch.
 *
 * Auto-navigates to [MainActivity] after [navDelayMs] (2 s). While the user
 * stares at the splash, we also kick off [MainViewModel.load] so the home
 * screen has a populated UiState on the very first frame — no flash of
 * `UiState.Loading`.
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    /**
     * Lazy-resolved [MainViewModel] scoped to this Activity. Uses raw
     * [ViewModelProvider] (not the `by viewModels { … }` Kotlin DSL) so
     * we don't have to add `androidx.activity:activity-ktx` to deps just
     * for this one call site.
     */
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Version string pulled from PackageManager so it reflects whatever
        // versionCode/name Gradle sets in app/build.gradle.kts. Hard-coded
        // "1.0.0" only fires if the package can't be resolved (sandbox / test).
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
            "1.0.0"
        }
        binding.versionText.text = getString(R.string.splash_version_template, version)

        // RETRY button re-hides the error card + restarts the 2 s nav.
        binding.retryButton.setOnClickListener {
            binding.errorCard.visibility = View.GONE
            binding.loader.visibility = View.VISIBLE
            binding.statusText.text = getString(R.string.splash_loading)
            handler.removeCallbacks(navigateRunnable)
            handler.postDelayed(navigateRunnable, navDelayMs)
        }

        // WiFi button — plan Step 3.1 says the offline card offers
        // WiFi/Mobile/Retry. Open the system WiFi settings so the user
        // can flip a network on without leaving the app for the Settings
        // launcher. ACTION_WIFI_SETTINGS is API 1+, available on every
        // device we ship on (minSdk 23). We catch ActivityNotFoundException
        // (rare OEM stripping) and fall back to the main Settings screen.
        binding.wifiButton.setOnClickListener { openSettingsOrFallback(Settings.ACTION_WIFI_SETTINGS) }

        // Mobile button — same idea; open the data usage settings. The
        // alias is API 16+ (we ship minSdk 23) and the OS routes to the
        // equivalent Network & Internet page on Android 11+ where the
        // raw alias is omitted.
        binding.mobileButton.setOnClickListener { openSettingsOrFallback(Settings.ACTION_DATA_USAGE_SETTINGS) }

        // Kick off the home-screen data fetch so Main's first frame already
        // has data. The viewModelScope cancels automatically when this
        // Activity finishes via [navigateRunnable].
        mainVm.load()

        // Auto-navigate after the splash delay.
        handler.postDelayed(navigateRunnable, navDelayMs)
    }

    override fun onDestroy() {
        // Structured cancellation — never navigate a destroyed Activity.
        handler.removeCallbacks(navigateRunnable)
        super.onDestroy()
    }

    /**
     * Open a system Settings screen by action; if the OEM has stripped the
     * specific alias (rare), fall back to the main Settings page so the
     * user can still recover. Both branches use FLAG_ACTIVITY_NEW_TASK
     * because Settings are launched from an Activity context and the new
     * task keeps them outside our own back-stack.
     */
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
