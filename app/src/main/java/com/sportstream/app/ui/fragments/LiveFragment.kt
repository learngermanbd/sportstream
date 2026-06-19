package com.sportstream.app.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.sportstream.app.databinding.FragmentLiveBinding

/**
 * Phase 3 · Step 3.2 — Live tab Fragment (placeholder).
 *
 * Step 3.x will replace this with the live-channels grid driven by
 * [com.sportstream.app.ui.viewmodels.CategoriesViewModel] /
 * [com.sportstream.app.ui.viewmodels.HighlightsViewModel] — depending on
 * which feed the Live tab ends up surfacing.
 */
class LiveFragment : Fragment() {

    private var _binding: FragmentLiveBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLiveBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
