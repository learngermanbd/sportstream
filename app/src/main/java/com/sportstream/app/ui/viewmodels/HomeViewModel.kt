package com.sportstream.app.ui.viewmodels

import com.sportstream.app.data.models.Event
import com.sportstream.app.data.remote.ApiResult
import com.sportstream.app.data.repository.MainRepository
import com.sportstream.app.ui.common.StateViewModel
import com.sportstream.app.ui.common.UiState

/**
 * Phase 2 · Step 2.5 — Home (LIVE + upcoming events) ViewModel.
 *
 * Single `List<Event>` snapshot so the Home adapter can render the
 * LIVE badge per-row without joining 2 collections at the adapter.
 * Live events come first, then scheduled.
 */
class HomeViewModel(
    private val mainRepository: MainRepository
) : StateViewModel<List<Event>>() {

    fun refresh() = launch {
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

        // Live first; upcoming follows.
        setState(UiState.Success(liveR.value + eventsR.value))
    }
}
