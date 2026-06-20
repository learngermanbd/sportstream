package com.sportstream.app.ui.activities

import android.content.Intent
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
 * Hosts the BottomNavigationView (4 tabs) + the Drawer menu (10 items).
 * Drawer items with real destinations navigate directly; the rest show
 * a Snackbar with `upcomingStepFor(itemId)`.
 *
 * As of Phase 5:
 *  - drawer_highlights      → R.id.highlightsFragment  (3.5)
 *  - drawer_playlists       → R.id.playlistsFragment   (5.2)
 *  - drawer_network_stream  → R.id.networkStreamFragment (5.3) — NEW
 *  - drawer_share / drawer_exit → system actions
 *  - drawer_floating_player / drawer_video_quality / drawer_notice
 *    / drawer_join_us / drawer_update → Snackbar placeholders
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav) { v, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = sysBars.bottom)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.navHostFragment) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(top = bars.top)
            insets
        }

        val navHost = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        val navController = navHost.navController
        binding.bottomNav.setupWithNavController(navController)

        binding.navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.drawer_highlights -> {
                    navController.navigate(R.id.highlightsFragment)
                    binding.drawerRoot.closeDrawers()
                    true
                }
                R.id.drawer_playlists -> {
                    navController.navigate(R.id.playlistsFragment)
                    binding.drawerRoot.closeDrawers()
                    true
                }
                // Phase 5 · Step 5.3 — Network Stream now has a real
                // destination (R.id.networkStreamFragment wired in
                // main_nav_graph.xml), so the drawer item navigates
                // there directly instead of showing the
                // "Coming in Step 5.3" placeholder Snackbar.
                R.id.drawer_network_stream -> {
                    navController.navigate(R.id.networkStreamFragment)
                    binding.drawerRoot.closeDrawers()
                    true
                }
                R.id.drawer_share -> {
                    binding.drawerRoot.closeDrawers()
                    shareAppLink()
                    true
                }
                R.id.drawer_exit -> {
                    binding.drawerRoot.closeDrawers()
                    finishAffinity()
                    true
                }
                else -> {
                    val msg = getString(
                        R.string.drawer_coming_soon_template,
                        item.title.toString(),
                        upcomingStepFor(item.itemId)
                    )
                    Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT)
                        .setAnchorView(binding.bottomNav)
                        .show()
                    binding.drawerRoot.closeDrawers()
                    true
                }
            }
        }

        if (mainVm.state.value is UiState.Idle) {
            mainVm.load()
        }

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

    /**
     * Map a placeholder Drawer item id to the plan-listed step where its
     * real screen ships (e.g. "Video Quality — Coming in Step 4.5").
     * Playlists (5.2) + Network Stream (5.3) have real destinations and
     * were removed from this map in their respective steps.
     */
    private fun upcomingStepFor(itemId: Int): String = when (itemId) {
        R.id.drawer_floating_player  -> "4.6"
        R.id.drawer_video_quality    -> "4.5"
        R.id.drawer_notice           -> "5.5"
        R.id.drawer_join_us          -> "8.x"
        R.id.drawer_update           -> "6.2"
        else                          -> "TBD"
    }

    private fun shareAppLink() {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, getString(R.string.drawer_share_message))
        }
        startActivity(Intent.createChooser(sendIntent, getString(R.string.drawer_share)))
    }
}
