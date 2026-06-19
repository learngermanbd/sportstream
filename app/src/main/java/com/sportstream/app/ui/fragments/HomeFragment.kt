package com.sportstream.app.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.sportstream.app.R
import com.sportstream.app.SportStreamApp
import com.sportstream.app.databinding.FragmentHomeBinding
import com.sportstream.app.ui.adapters.EventAdapter
import com.sportstream.app.ui.common.UiState
import com.sportstream.app.ui.viewmodels.HomeViewModel
import com.sportstream.app.ui.viewmodels.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Phase 3 · Step 3.3 — Home tab Fragment.
 *
 * Real content: a SwipeRefresh wrapping a RecyclerView of [Event] rows
 * (each row renders via [com.sportstream.app.ui.adapters.EventAdapter]).
 *
 * Data flow (two collectors, both `repeatOnLifecycle(STARTED)`):
 *
 *   1. `mainVm.state` (activity-scoped, owned by MainActivity) is
 *      observed. On `UiState.Success<MainSnapshot>` we call
 *      `homeVm.bindFromSnapshot(snapshot)` which re-emits the home list
 *      on `homeVm.state`.
 *
 *   2. `homeVm.state` (fragment-scoped) is observed. Each emission
 *      triggers one of:
 *        - `submitList(events)` to the [EventAdapter],
 *        - show/hide empty / loading overlays,
 *        - clear `swipeRefresh.isRefreshing`.
 *
 * Swipe-to-refresh calls `mainVm.load()` (\u2014 the activity-scoped boot
 * fetch) so the entire repository graph re-fires through the live + events
 * fetches and the home list re-derives naturally.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    /**
     * Fragment-scoped HomeViewModel. We use raw `ViewModelProvider` (not
     * the `by viewModels { \u2026 }` Kotlin DSL) to avoid adding
     * `androidx.fragment:fragment-ktx` for this single call site, mirroring
     * the pattern already established by [SplashActivity] and
     * [com.sportstream.app.ui.activities.MainActivity].
     */
    private val homeVm: HomeViewModel by lazy {
        val app = requireActivity().application as SportStreamApp
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(app.repository.mainRepository) as T
        }
        ViewModelProvider(this, factory)[HomeViewModel::class.java]
    }

    /**
     * Activity-scoped MainViewModel. Same instance MainActivity already
     * observes; the Home tab is just another consumer of that snapshot.
     */
    private val mainVm: MainViewModel by lazy {
        val activity = requireActivity()
        val app = activity.application as SportStreamApp
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MainViewModel(app.repository.mainRepository) as T
        }
        @Suppress("UNCHECKED_CAST")
        ViewModelProvider(activity, factory)[MainViewModel::class.java]
    }

    private lateinit var adapter: EventAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = EventAdapter(onClick = { event ->
            // Step 4.2 wires the PlayerActivity intent here. Until then
            // taps are a no-op so the row doesn't visually respond.
        })
        binding.homeRv.layoutManager = LinearLayoutManager(requireContext())
        binding.homeRv.adapter = adapter

        // Swipe-to-refresh re-triggers the activity-scoped boot fetch.
        // The downstream HomeViewModel re-derives automatically when
        // mainVm.state transitions back to Success.
        binding.swipeRefresh.setColorSchemeResources(R.color.primary, R.color.live_red)
        binding.swipeRefresh.setOnRefreshListener { mainVm.load() }

        // Collector #1 — wire mainVm.state -> homeVm derived state.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainVm.state.collectLatest { state ->
                    if (state is UiState.Success) {
                        // UiState.Success<T> where T = MainSnapshot.
                        // `state.value` is `Any` after the smart-cast (the
                        // sealed-class success variant is generic but Kotlin
                        // can't reify it), so we unchecked-cast back to
                        // MainSnapshot. Safe because MainViewModel only ever
                        // emits Success<MainSnapshot>.
                        @Suppress("UNCHECKED_CAST")
                        val snapshot = state.value as com.sportstream.app.ui.viewmodels.MainSnapshot
                        homeVm.bindFromSnapshot(snapshot)
                    }
                }
            }
        }

        // Collector #2 — wire homeVm.state -> Adapter + overlays.
        // This is the canonical "mainVm.state.Success -> homeRv.adapter
        // swap" wiring the user requested.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                homeVm.state.collectLatest { state ->
                    when (state) {
                        is UiState.Success -> {
                            binding.loadingIndicator.isVisible = false
                            binding.swipeRefresh.isRefreshing = false
                            adapter.submitList(state.value)
                            binding.emptyState.isVisible = state.value.isEmpty()
                        }
                        is UiState.Error -> {
                            binding.loadingIndicator.isVisible = false
                            binding.swipeRefresh.isRefreshing = false
                            binding.emptyState.isVisible = true
                            binding.emptyState.text = state.message
                        }
                        UiState.Loading -> {
                            binding.loadingIndicator.isVisible = true
                            binding.emptyState.isVisible = false
                        }
                        UiState.Idle -> {
                            binding.loadingIndicator.isVisible = false
                            binding.swipeRefresh.isRefreshing = false
                            binding.emptyState.isVisible = adapter.itemCount == 0
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        // Memory hygiene: drop the adapter reference (so the RecyclerView's
        // internal ViewHolder pool can be GC'd) and reset the binding.
        binding.homeRv.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
