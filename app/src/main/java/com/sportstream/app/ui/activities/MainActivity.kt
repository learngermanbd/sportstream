package com.sportstream.app.ui.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
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
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        // Observe state for the STARTED window. Currently a no-op sink — Step 3.x
        // will attach a Snackbar handler on UiState.Error and a global loader
        // toggle on UiState.Loading. The collection machinery is already in
        // place so the future change is one statement.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainVm.state.collectLatest {
                    /* Step 3.x / 3.5 will wire Error → Snackbar here. */
                }
            }
        }
    }
}
