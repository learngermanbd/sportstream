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
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.chip.Chip
import com.sportstream.app.R
import com.sportstream.app.SportStreamApp
import com.sportstream.app.databinding.FragmentCategoriesBinding
import com.sportstream.app.ui.adapters.CategoryAdapter
import com.sportstream.app.ui.common.UiState
import com.sportstream.app.ui.viewmodels.CategoriesViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Phase 3 · Step 3.4 \u2014 Categories fragment.
 *
 * Filter-by-chip + 3-col grid of [com.sportstream.app.data.models.Channel]
 * cards. Pipe-managed via the existing Step 2.5 [CategoriesViewModel]:
 *
 *   1. On `onViewCreated` the fragment calls `vm.refresh()` to drive the
 *      initial repo fetch (categories + channels).
 *   2. We observe `vm.state` in `repeatOnLifecycle(STARTED)` so when it
 *      transitions:
 *        - Success -> rebuild the ChipGroup from `snapshot.categories`
 *          (preserving the current selection, if still in the list),
 *          set currentSelectionVisibleChip, and re-bind the RecyclerView
 *          to `snapshot.visibleChannels`.
 *        - Loading -> show progress + empty-state off.
 *        - Error   -> empty-state on, message set.
 *        - Idle     -> reset overlays, empty-state mirrors itemCount.
 *   3. Each chip's `setOnCheckedStateChangeListener` calls
 *      `vm.selectCategory(categoryId)` to drive the O(n) re-filter inside
 *      the VM. The VM re-emits a new `CategoriesSnapshot` with updated
 *      `visibleChannels` so the RecyclerView rebinds via DiffUtil.
 *
 * Category id `null` is the special "All" filter; we model it as a
 * dedicated dynamic chip prefixed to the data-driven chip list.
 */
class CategoriesFragment : Fragment() {

    private var _binding: FragmentCategoriesBinding? = null
    private val binding get() = _binding!!

    /**
     * Fragment-scoped CategoriesViewModel. We use raw `ViewModelProvider`
     * (not the `by viewModels { ... }` Kotlin DSL) to avoid adding
     * `androidx.fragment:fragment-ktx` for this single call site, mirroring
     * the pattern established by HomeFragment and MainActivity.
     */
    private val vm: CategoriesViewModel by lazy {
        val app = requireActivity().application as SportStreamApp
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CategoriesViewModel(app.repository.mainRepository) as T
        }
        ViewModelProvider(this, factory)[CategoriesViewModel::class.java]
    }

    private lateinit var adapter: CategoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategoriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = CategoryAdapter(onClick = { channel ->
            // Step 4.2 wires the PlayerActivity intent here. For now this
            // is a no-op so taps don't crash.
        })
        binding.channelsRv.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.channelsRv.adapter = adapter

        // Initial fetch \u2014 idempotent: CategoriesViewModel.refresh is a
        // suspending coroutine so calling it from a STARTED-fragment is
        // safe even if the VM already populated state from a previous
        // view lifetime (rotation etc.).
        vm.refresh()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collectLatest { state ->
                    when (state) {
                        is UiState.Success -> {
                            binding.loadingIndicator.isVisible = false
                            applySnapshot(state.value)
                            binding.emptyState.isVisible = state.value.visibleChannels.isEmpty()
                        }
                        is UiState.Error -> {
                            binding.loadingIndicator.isVisible = false
                            binding.emptyState.isVisible = true
                            binding.emptyState.text = state.message
                        }
                        UiState.Loading -> {
                            binding.loadingIndicator.isVisible = true
                            binding.emptyState.isVisible = false
                        }
                        UiState.Idle -> {
                            binding.loadingIndicator.isVisible = false
                            binding.emptyState.isVisible = adapter.itemCount == 0
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        binding.channelsRv.adapter = null
        _binding = null
        super.onDestroyView()
    }

    /**
     * Rebuild the chip group from `snapshot.categories` and bind the
     * RecyclerView to `snapshot.visibleChannels`. Preserves the current
     * chip selection when possible.
     *
     * Listener attachment is the standard pattern: temporarily clear
     * the listener while we mutate the chips, then re-attach once so
     * the rebuild doesn't fire a synthetic selectCategory(...) per chip.
     */
    private fun applySnapshot(snap: com.sportstream.app.ui.viewmodels.CategoriesSnapshot) {
        // 1) Rebuild the chip group.
        binding.categoryChips.removeAllViews()
        binding.categoryChips.setOnCheckedStateChangeListener(null)

        // First chip \u2014 the special "All" filter (id = null).
        val allChip = Chip(requireContext()).apply {
            text = getString(R.string.fragment_categories_chip_all)
            isCheckable = true
            tag = ALL_CHIP_TAG
            isChecked = snap.selectedCategoryId == null
        }
        binding.categoryChips.addView(allChip)

        // One chip per Category returned by the API.
        snap.categories.forEach { category ->
            val chip = Chip(requireContext()).apply {
                text = category.name
                isCheckable = true
                tag = category.id
                isChecked = snap.selectedCategoryId == category.id
            }
            binding.categoryChips.addView(chip)
        }

        // Re-attach the listener once so subsequent taps fire selectCategory.
        binding.categoryChips.setOnCheckedStateChangeListener { group, checkedIds ->
            // Single-selection ChipGroup guarantees at most one checked id.
            val rawId = checkedIds.firstOrNull()
            val resolvedId: String? = when {
                rawId == null -> null                       // toggled off -> null
                rawId == View.NO_ID -> null                 // safety net
                else -> group.findViewById<Chip>(rawId)?.tag as? String
            }
            vm.selectCategory(resolvedId)
        }

        // 2) Bind the grid.
        adapter.submitList(snap.visibleChannels)
    }

    companion object {
        /** Sentinel tag used for the "All" chip (distinct from any Category.id). */
        private const val ALL_CHIP_TAG = "__all__"
    }
}
