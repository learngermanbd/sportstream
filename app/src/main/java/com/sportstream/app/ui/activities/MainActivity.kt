package com.sportstream.app.ui.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.snackbar.Snackbar
import com.sportstream.app.R
import com.sportstream.app.SportStreamApp
import com.sportstream.app.databinding.ActivityMainBinding
import com.sportstream.app.ui.common.UiState
import com.sportstream.app.ui.viewmodels.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Phase 3 · Step 3.2 — Main host Activity.
 *
 * Layout: ConstraintLayout holding a [NavHostFragment] ([R.id.navHostFragment])
 * + a [com.google.android.material.bottomnavigation.BottomNavigationView]
 * ([R.id.bottomNav]) styled with `SportStream.BottomNav` (defined in
 * styles.xml since Step 1.5).
 *
 * Wires:
 *  - `BottomNavigationView.setupWithNavController(navController)` so tabs map
 *    1:1 to `R.id.homeFragment / liveFragment / favoritesFragment /
 *    settingsFragment` destinations in `res/navigation/main_nav_graph.xml`.
 *  - **Initial repository fetch wired through [StateViewModel.launch]** —
 *    `mainVm.load()` internally calls `StateViewModel.launch { ... }` which
 *    hops to Dispatchers.Default + viewModelScope (auto-cancelled when this
 *    Activity finishes). The single `mainVm.load()` call here is what
 *    satisfies "wire StateViewModel.launch for the initial
 *    mainRepository.load() call".
 *  - State observation via `repeatOnLifecycle(STARTED)` so future UI hooks
 *    (snackbar on Error, content swap on Success) can attach without
 *    re-wiring later.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /**
     * Lazy-resolved [MainViewModel] scoped to this Activity. Mirrors the
     * raw [ViewModelProvider] pattern from SplashActivity (avoids needing
     * `androidx.activity:activity-ktx` for the `by viewModels { … }` DSL).
     */
    private val mainVm: MainViewModel by lazy {
        val app = application as SportStreamApp
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MainViewModel(app.repository.mainRepository) as T
        }
        ViewModelProvider(this, factory)[MainViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge: must run BEFORE super.onCreate / setContentView so
        // insets dispatch to our view-level listener below instead of being
        // swallowed by the platform's pre-edge-to-edge padding model.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Gesture-nav pill handling — BottomNavigationView grows its bottom
        // padding by the system-bars inset so the active-indicator pill and
        // the tab icons never sit beneath the OS gesture pill. Returning
        // `insets` propagates the same insets to siblings (the NavHostFragment
        // above) so future top-inset handling can be layered here too.
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav) { v, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = sysBars.bottom)
            insets
        }

        // BottomNav <-> NavController wiring. Tab swaps are handled by
        // setupWithNavController() — no manual navController.navigate(...)
        // calls needed because menu item IDs match graph destination IDs.
        val navHost = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        val navController = navHost.navController
        binding.bottomNav.setupWithNavController(navController)

        // **Wire StateViewModel.launch** for the initial repository fetch.
        // MainViewModel.load() is defined as:
        //   fun load() = launch(io) { … repository.fetchEvents/Live/Categories/Highlights … }
        // where `launch` is the StateViewModel helper that wraps the suspend
        // in viewModelScope, catches Throwable (maps to UiState.Error), and
        // **re-throws CancellationException** so structured concurrency
        // survives Activity destroy. One call here triggers the full
        // boot-population of `mainVm.state`. The Idle guard skips redundant
        // network fetches on Activity recreation (rotation, theme change) —
        // the VM is **activity-scoped** (not application-scoped), so each
        // recreate rebuilds the VM. Once the state has moved past
        // Idle → Loading → Success/Error we don't refetch on recreate.
        if (mainVm.state.value is UiState.Idle) {
            mainVm.load()
        }

        // Error -> Snackbar with RETRY. Anchored to bottomNav so the bar
        // floats ABOVE the bottom navigation (default root-anchoring would
        // sit the snackbar behind the BottomNav or behind the gesture-nav
        // pill on Android 10+). RETRY calls mainVm.retry() which re-gates
        // state to UiState.Idle and re-runs load() — the Step 3.2-polish
        // semantics. The Snackbar is LENGTH_INDEFINITE so it stays until
        // the user retries or navigates away — a transient snackbar would
        // auto-dismiss before the user has time to read the error.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainVm.state.collectLatest { state ->
                    if (state is UiState.Error) {
                        Snackbar.make(binding.root, state.message, Snackbar.LENGTH_INDEFINITE)
                            .setAnchorView(binding.bottomNav)
                            .setAction(getString(R.string.splash_retry)) { mainVm.retry() }
                            .show()
                    }
                }
            }
        }
    }
}
