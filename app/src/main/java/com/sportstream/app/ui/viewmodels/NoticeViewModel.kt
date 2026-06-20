package com.sportstream.app.ui.viewmodels

import android.app.Application
import android.util.Log
import com.sportstream.app.data.remote.AppConfig
import com.sportstream.app.data.remote.RemoteConfigHelper
import com.sportstream.app.ui.common.StateViewModel
import com.sportstream.app.ui.common.UiState
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient

/**
 * Phase 5 · Step 5.5 — ViewModel for the Notice screen.
 *
 * The admin backend ([com.sportstream.app.data.remote.RemoteConfigHelper])
 * already caches `GET /api/config` in DataStore with a 30-minute
 * freshness window. This VM is a thin wrapper that exposes the latest
 * [AppConfig] as [UiState] so the Fragment renders [AppConfig.noticeText]
 * (or an empty-state when the admin hasn't set one).
 *
 * Refreshing: `refresh(force = false)` honors the cache; `force = true`
 *     bypasses it (wiring mapped to the FAB tap).
 *
 * Cancellation: structured-concurrency safe. We rethrow
 *     [CancellationException] so a Fragment destroyed mid-fetch
 *     unwinds cleanly rather than getting swallowed as a generic error
 *     (Phase 2 audit SHOULD-FIX carried forward).
 */
class NoticeViewModel(
    private val app: Application,
    private val httpClient: OkHttpClient
) : StateViewModel<AppConfig>() {

    /**
     * Fetch the current [AppConfig].
     *
     * @param force when true, requires a network round-trip even if the
     *              30-minute cached copy is still fresh. Used by the
     *              manual-refresh FAB.
     */
    fun refresh(force: Boolean = false) = launch {
        // Show progress before the network round-trip so the user gets
        // explicit feedback (the cached-config fast path is <1 ms but
        // the cold-cache network path can take seconds). Without this
        // emit, state goes Idle -> Success and the Loading branch in
        // NoticeFragment.render() is never reached.
        setState(UiState.Loading)
        try {
            val cfg = RemoteConfigHelper.fetchConfig(
                context = app,
                force = force,
                httpClient = httpClient
            )
            setState(UiState.Success(cfg))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w("NoticeVM", "fetchConfig failed (force=$force)", e)
            setState(UiState.Error(e.message ?: "Couldn't load notice", e))
        }
    }
}
