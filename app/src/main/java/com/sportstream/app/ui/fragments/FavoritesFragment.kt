package com.sportstream.app.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.sportstream.app.databinding.FragmentFavoritesBinding

/**
 * Phase 3 · Step 3.2 — Favorites tab Fragment (placeholder).
 *
 * Step 5.1 swaps this for a Swipe-to-delete list backed by
 * [com.sportstream.app.data.repository.FavoritesRepository] (`observeFavorites`)
 * + undo snackbar + heart-toggle animation. Until then this just shows the
 * "FAVORITES — Step 5.1 incoming" placeholder.
 */
class FavoritesFragment : Fragment() {

    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
