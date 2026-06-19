package com.sportstream.app.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.sportstream.app.databinding.FragmentHomeBinding

/**
 * Phase 3 · Step 3.2 — Home tab Fragment (placeholder).
 *
 * Step 3.3 will swap this for a SwipeRefresh + auto-scroll banners + LIVE
 * Events list with VS team-logo layout + animated LIVE badge + EventAdapter
 * with DiffUtil, driven by [com.sportstream.app.ui.viewmodels.HomeViewModel].
 *
 * ViewBinding-managed (`FragmentHomeBinding`) to keep null-safety on
 * `_binding` between [onCreateView] and [onDestroyView].
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        // Memory hygiene: clear the binding reference so the inflated view
        // hierarchy can be garbage-collected when the fragment view is
        // detached (tab swap, back-stack pop).
        _binding = null
        super.onDestroyView()
    }
}
