package com.sportstream.app.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sportstream.app.R
import com.sportstream.app.SportStreamApp
import com.sportstream.app.data.remote.AppConfig
import com.sportstream.app.databinding.FragmentNoticeBinding
import com.sportstream.app.ui.common.UiState
import com.sportstream.app.ui.viewmodels.NoticeViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Phase 5 · Step 5.5 — Notice screen.
 *
 * Renders [AppConfig.noticeText] (free-form banner content the admin
 * sets via the Phase 8 backend) in a scrollable card. The data source
 * is [com.sportstream.app.data.remote.RemoteConfigHelper.fetchConfig]
 * which already caches for 30 minutes; the FAB forces a fresh network
 * fetch when the user wants the absolute latest.
 *
 * Three render branches map to [UiState]:
 *  - [UiState.Loading] → progress bar only
 *  - [UiState.Success] + noticeText non-empty → body card
 *  - [UiState.Success] + noticeText empty   → "No active notice" empty state
 *  - [UiState.Error]                         → error card with Retry button
 *
 * `safeBinding` lets helper methods (`render(...)`, click-handlers
 * invoked from coroutine-collected state flows) safely no-op once the
 * Fragment view is destroyed — mirrors the Step 5.3 + Step 5.2 pattern.
 */
class NoticeFragment : Fragment() {

    private var _binding: FragmentNoticeBinding? = null
    private val binding get() = _binding!!

    /**
     * Convenience view-binding accessor that's safe to call from helper
     * methods invoked AFTER [onDestroyView] has nulled [_binding].
     * Use this everywhere outside [onCreateView] / [onViewCreated]'s
     * direct binding access. See Phase 3 [Step 5.3] precedent.
     */
    private val safeBinding: FragmentNoticeBinding?
        get() = _binding

    private val noticeVm: NoticeViewModel by viewModels {
        val app = requireActivity().application as SportStreamApp
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                NoticeViewModel(app, app.network.httpClient) as T
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNoticeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.noticeRefreshFab.setOnClickListener {
            noticeVm.refresh(force = true)
        }
        binding.noticeRetryButton.setOnClickListener {
            noticeVm.refresh()
        }
        observeState()
        if (noticeVm.state.value is UiState.Idle) {
            noticeVm.refresh()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                noticeVm.state.collectLatest { render(it) }
            }
        }
    }

    private fun render(state: UiState<AppConfig>) {
        val b = safeBinding ?: return
        when (state) {
            is UiState.Idle, is UiState.Loading -> showOnly(b.noticeProgress)
            is UiState.Success -> {
                val notice = state.value.noticeText.trim()
                if (notice.isEmpty()) {
                    showOnly(b.noticeEmptyState)
                } else {
                    b.noticeBodyText.text = notice
                    showOnly(b.noticeBodyCard)
                }
            }
            is UiState.Error -> {
                b.noticeErrorText.text = state.message
                showOnly(b.noticeErrorCard)
            }
        }
    }

    /**
     * Make `visible` the only visible major surface (body / empty /
     * error / progress). FAB and header chrome stay visible -- those
     * are persistent across all states so the user can always refresh
     * or see what screen they're on.
     */
    private fun showOnly(visible: View) {
        val b = safeBinding ?: return
        listOf(
            b.noticeProgress,
            b.noticeBodyCard,
            b.noticeEmptyState,
            b.noticeErrorCard
        ).forEach { it.visibility = if (it === visible) View.VISIBLE else View.GONE }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
