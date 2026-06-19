package com.sportstream.app.ui.viewmodels

import com.sportstream.app.data.models.Category
import com.sportstream.app.data.models.Event
import com.sportstream.app.data.models.Highlight
import com.sportstream.app.data.remote.ApiResult
import com.sportstream.app.data.repository.MainRepository
import com.sportstream.app.ui.common.StateViewModel
import com.sportstream.app.ui.common.UiState

/**
 * Phase 2 · Step 2.5 — single snapshot the Splash → Main screen renders.
 *
 * Combines the 4 sub-fetches MainViewModel orchestrates so the UI can
 * observe one StateFlow instead of wiring 4 separate subscriptions.
 *
 * Sequential fetches because the backend applies a per-IP RateLimit
 * (Phase 8) and parallel calls would race the limiter; the helper
 * early-returns on the first Failure so Refresh() doesn't burn tokens
 * after a 401/429 already proved the call would fail.
 */
data class MainSnapshot(
    val live: List<Event> = emptyList(),
    val events: List<Event> = emptyList(),
    val categories: List<Category> = emptyList(),
    val highlights: List<Highlight> = emptyList()
)

class MainViewModel(
    private val mainRepository: MainRepository
) : StateViewModel<MainSnapshot>() {

    fun load() = launch {
        setState(UiState.Loading)

        val liveR = mainRepository.fetchLive()
        if (liveR !is ApiResult.Success) {
            val ex = liveR.exceptionOrNull()
            setState(UiState.Error(ex?.message ?: "Couldn’t load live events", ex))
            return@launch
        }
        val eventsR = mainRepository.fetchEvents()
        if (eventsR !is ApiResult.Success) {
            val ex = eventsR.exceptionOrNull()
            setState(UiState.Error(ex?.message ?: "Couldn’t load events", ex))
            return@launch
        }
        val catR = mainRepository.fetchCategories()
        if (catR !is ApiResult.Success) {
            val ex = catR.exceptionOrNull()
            setState(UiState.Error(ex?.message ?: "Couldn’t load categories", ex))
            return@launch
        }
        val hlR = mainRepository.fetchHighlights()
        if (hlR !is ApiResult.Success) {
            val ex = hlR.exceptionOrNull()
            setState(UiState.Error(ex?.message ?: "Couldn’t load highlights", ex))
            return@launch
        }

        setState(
            UiState.Success(
                MainSnapshot(
                    live     = liveR.value,
                    events   = eventsR.value,
                    categories = catR.value,
                    highlights  = hlR.value
                )
            )
        )
    }

    /**
     * Phase 3 · Step 3.2 polish — RETRY entry point.
     *
     * Resets the VM state to [UiState.Idle] so the next [load] call observes
     * a clean gate — caller-side `if (state is Idle) load()` checks pass on
     * a subsequent onCreate (Activity recreation scenario), and the visible
     * state sequence is Error -> Idle -> Loading -> Success/Error, which
     * makes the gate logic testable end-to-end. UI side: MainActivity's
     * Snackbar RETRY action calls this function — bypassing the onCreate
     * Idle gate (boot-only) but presenting a meaningful "back-to-Idle" UX
     * to anyone observing `state`.
     */
    fun retry() {
        setState(UiState.Idle)
        load()
    }
}
