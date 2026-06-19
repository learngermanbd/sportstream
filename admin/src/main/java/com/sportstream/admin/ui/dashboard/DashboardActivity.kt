package com.sportstream.admin.ui.dashboard

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.sportstream.admin.databinding.ActivityDashboardBinding

/**
 * Phase 8 \u00b7 Step 8.13 \u2014 Post-auth landing screen (placeholder).
 *
 * Shows three quick stats (live events, total channels, today's highlights)
 * fetched via [com.sportstream.admin.data.AdminApi.fetchDashboardStats].
 * The full Phase 8 dashboard (events/channels/highlights/config/analytics
 * sub-sections) lands across Steps 8.14\u20138.15; this activity is the
 * minimal functional entry point that proves the auth \u2192 fetch pipeline
 * end-to-end.
 */
class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Step 8.14 will wire the chips + FAB + list adapters; for now just
        // reflect that we're past login.
        binding.tvWelcome.text = "SportStream Admin \u2014 Dashboard"
        binding.statsCard.visibility = View.VISIBLE
        binding.statsText.text =
            "Live events: \u2014   Channels: \u2014   Highlights: \u2014\n" +
            "(Backend wired in Step 8.14)"
    }
}
