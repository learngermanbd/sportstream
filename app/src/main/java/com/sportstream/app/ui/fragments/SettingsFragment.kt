package com.sportstream.app.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.sportstream.app.databinding.FragmentSettingsBinding

/**
 * Phase 3 · Step 3.2 — Settings tab Fragment (placeholder).
 *
 * Step 5.5 wraps the whole MainActivity in a DrawerLayout hosting a
 * Navigation Drawer with 9 items (Network Stream, Playlists, Floating
 * Player, Video Quality, Notice, Join Us, Share, Update, Exit). Step 5.6
 * adds a Toolbar with debounced search. This fragment stays as-is — the
 * drawer + toolbar are added on top of the existing BottomNav.
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
