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
 * Hosts the BottomNavigationView (4 tabs) + the Drawer menu (10 items) +
 * (Phase 5 · Step 5.6) the MaterialToolbar with a Search action.
 *
 * Drawer items with real destinations navigate directly; the rest show
 * a Snackbar with `upcomingStepFor(itemId)`. The Toolbar's Search icon
 * navigates to R.id.searchFragment on every tap (single destination).
 *
 * As of Phase 5:
 *  - drawer_highlights      → R.id.highlightsFragment  (3.5)
 *  - drawer_playlists       → R.id.playlistsFragment   (5.2)
 *  - drawer_network_stream  → R.id.networkStreamFragment (5.3)
 *  - drawer_notice          → R.id.noticeFragment      (5.5)
 *  - drawer_share / drawer_exit → system actions
 *  - drawer_floating_player / drawer_video_quality /
 *    drawer_join_us / drawer_update → Snackbar placeholders
 *  - main_toolbar_action_search (toolbar menu) → R.id.searchFragment (5.6)
 *
 * Window-inset policy: the toolbar absorbs the top system-bar inset
 * (status bar) so its title sits below the camera notch / cutout; the
 * NavHost no longer pads itself for top because the toolbar now sits
 * above it. The bottom-nav still absorbs the bottom system-bar inset.
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
        // Toolbar absorbs the status-bar inset; NavHost then sits flush
        // against the toolbar with no extra top padding (Phase 5 · Step 5.6).
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainToolbar) { v, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = sysBars.top)
            insets
        }

        val navHost = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        val navController = navHost.navController
        binding.bottomNav.setupWithNavController(navController)

        // Phase 5 · Step 5.6 — Toolbar search-action wiring. Menu is
        // inflated declaratively via activity_main.xml `app:menu=`, so the
        // listener only routes the click to navController.
        binding.mainToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.main_toolbar_action_search -> {
                    navController.navigate(R.id.searchFragment)
                    true
                }
                else -> false
            }
        }

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
                R.id.drawer_network_stream -> {
                    navController.navigate(R.id.networkStreamFragment)
                    binding.drawerRoot.closeDrawers()
                    true
                }
                R.id.drawer_notice -> {
                    navController.navigate(R.id.noticeFragment)
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
     * Playlists (5.2) + Network Stream (5.3) + Notice (5.5) have real
     * destinations and were removed from this map in their respective
     * steps.
     */
    private fun upcomingStepFor(itemId: Int): String = when (itemId) {
        R.id.drawer_floating_player  -> "4.6"
        R.id.drawer_video_quality    -> "4.5"
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
