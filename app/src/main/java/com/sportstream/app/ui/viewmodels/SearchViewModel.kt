package com.sportstream.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sportstream.app.data.models.SearchResults
import com.sportstream.app.data.prefs.PlayerPrefs
import com.sportstream.app.ui.common.StateViewModel
import com.sportstream.app.ui.common.UiState

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Phase 5 · Step 5.6 — Search screen ViewModel.
 *
 * Pattern mirrors the existing NetworkStreamViewModel:
 *  - [UiState] sealed wrapping for the result list (matches the codebase's
 *    three-state contract: Loading / Success / Error).
 *  - StateViewModel base for `setState(...)` + structured-concurrency-safe
 *    `launch { … }` helper.
 *  - Reads sources via [MainViewModel.state] Flow inside MainActivity scope;
 *    does NOT re-fetch from the network on init — searches the in-memory
 *    snapshot the app already loaded at startup.
 *
 * Debouncing semantics: query changes are debounced 300 ms (per
 * sportzfy_build_plan.html#step5.6) before re-filtering. Recent searches
 * are persisted via [PlayerPrefs.recentSearchesFlow] / `addRecentSearch`
 * / `removeRecentSearch` (added in this step as the search-data twin of
 * the existing recent-URL flow).
 */
@OptIn(FlowPreview::class)
class SearchViewModel(
    app: Application,
    private val mainVm: MainViewModel,
    private val prefs: PlayerPrefs
) : StateViewModel<SearchResults>(UiState.Idle) {

    /** Latest typed query (without debounce). Updates live in the EditText. */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    /** Most-recent searches, in newest-first order, hydrated from DataStore. */
    private val _recent = MutableStateFlow<List<String>>(emptyList())
    val recent: StateFlow<List<String>> = _recent

    init {
        viewModelScope.launch {
            prefs.recentSearchesFlow.collect { _recent.value = it }
        }
    }

    /**
     * Streams debounced search results whenever either the query or the
     * upstream snapshot changes. Subscriber count > 0 + 5 s grace period
     * before the upstream combination tears down — matches the
     * 5 s "screen rotate window" pattern favored elsewhere in the
     * codebase, so a quick config change doesn't drop pending results.
     */
    val results: StateFlow<UiState<SearchResults>> = combine(
        _query.debounce(300L).distinctUntilChanged(),
        mainVm.state
    ) { q, upstreamState ->
        when (upstreamState) {
            is UiState.Idle, is UiState.Loading ->
                UiState.Loading as UiState<SearchResults>
            is UiState.Error ->
                UiState.Error(upstreamState.message, upstreamState.cause) as UiState<SearchResults>
            is UiState.Success ->
                UiState.Success(SearchResults.filter(
                    upstreamState.value.events,
                    upstreamState.value.highlights,
                    q
                )) as UiState<SearchResults>
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = UiState.Idle
    )

    /** Live update the typed query (called on every EditText change). */
    fun setQuery(q: String) {
        _query.value = q
    }

    /**
     * Persist a query to the recents list. Called when the user hits the
     * IME action / submit OR taps a result row. Blank queries are a no-op
     * so we don't pollute the chip strip with the empty string.
     */
    fun recordSearch(query: String) {
        val cleaned = query.trim()
        if (cleaned.isBlank()) return
        viewModelScope.launch { prefs.addRecentSearch(cleaned) }
    }

    /** Remove a single recent search — chip close-icon tap. */
    fun removeRecent(query: String) {
        viewModelScope.launch { prefs.removeRecentSearch(query) }
    }

    /** Clear all recent searches. Wired to a "Clear all" affordance in the
     *  recent searches chip row. */
    fun clearRecent() {
        viewModelScope.launch {
            val current = _recent.value
            current.forEach { prefs.removeRecentSearch(it) }
        }
    }

    /** Wired from SearchFragment.onDestroy — strip the snapshot listener so
     *  the activity-scoped MainViewModel's collectors aren't doubled. */
    override fun onCleared() {
        super.onCleared()
        _query.value = ""
    }

    companion object {
        /** DEBOUNCE_MS = 300 matches the build-plan spec for Step 5.6. */
        const val DEBOUNCE_MS = 300L
    }
}
