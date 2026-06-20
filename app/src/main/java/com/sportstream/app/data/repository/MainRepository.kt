package com.sportstream.app.data.repository

import com.sportstream.app.data.demo.DemoDataProvider
import com.sportstream.app.data.models.Banner
import com.sportstream.app.data.models.Category
import com.sportstream.app.data.models.Channel
import com.sportstream.app.data.models.Event
import com.sportstream.app.data.models.Highlight
import com.sportstream.app.data.models.Playlist
import com.sportstream.app.data.remote.ApiResult
import com.sportstream.app.data.remote.RemoteDataSource

/**
 * Phase 2 · Step 2.4 — Main (network-side) Repository.
 *
 * Thin facade over [RemoteDataSource]. Holds the network API surface
 * for Step 2.5 ViewModels so the data layer is swappable without
 * touching the UI layer.
 *
 * CancellationException propagates cleanly through the suspend boundary.
 * [RemoteDataSource] maps non-cancel Throwables to [ApiResult.Failure]
 * inside `fetchAndParse`, so structured-concurrency cancellation still
 * propagates while the caller folds on [ApiResult.Success] / Failure.
 *
 * Phase 7 · Step 7.13 — Demo fallback: when the remote API fails (offline,
 * DNS blocked, TLS error), each fetchXxxWithFallback() returns bundled
 * demo content wrapped in ApiResult.Success so the UI renders meaningful
 * data instead of an empty error state.  Cancels still propagate (never
 * swallowed).
 */
class MainRepository(
    private val remoteDataSource: RemoteDataSource
) {
    suspend fun fetchEvents(): ApiResult<List<Event>> =
        remoteDataSource.fetchEvents()

    suspend fun fetchLive(): ApiResult<List<Event>> =
        remoteDataSource.fetchLive()

    suspend fun fetchChannels(): ApiResult<List<Channel>> =
        remoteDataSource.fetchChannels()

    suspend fun fetchCategories(): ApiResult<List<Category>> =
        remoteDataSource.fetchCategories()

    suspend fun fetchHighlights(): ApiResult<List<Highlight>> =
        remoteDataSource.fetchHighlights()

    suspend fun fetchPlaylists(ownerId: String): ApiResult<List<Playlist>> =
        remoteDataSource.fetchPlaylists(ownerId)

    /** Phase 3 · Step 3.3 — banner carousel slides. */
    suspend fun fetchBanners(): ApiResult<List<Banner>> =
        remoteDataSource.fetchBanners()

    // ── Demo-content fallbacks (Phase 7 · Step 7.13) ────────────────
    // These try the live API first; on network failure they return the
    // bundled demo content so the app stays usable offline.

    suspend fun fetchEventsWithFallback(): ApiResult<List<Event>> =
        withFallback(remoteDataSource.fetchEvents(), DemoDataProvider.events())

    suspend fun fetchLiveWithFallback(): ApiResult<List<Event>> =
        withFallback(remoteDataSource.fetchLive(),
            DemoDataProvider.events().filter { it.isLive })

    suspend fun fetchChannelsWithFallback(): ApiResult<List<Channel>> =
        withFallback(remoteDataSource.fetchChannels(), DemoDataProvider.channels())

    suspend fun fetchCategoriesWithFallback(): ApiResult<List<Category>> =
        withFallback(remoteDataSource.fetchCategories(), DemoDataProvider.categories())

    suspend fun fetchHighlightsWithFallback(): ApiResult<List<Highlight>> =
        withFallback(remoteDataSource.fetchHighlights(), DemoDataProvider.highlights())

    suspend fun fetchBannersWithFallback(): ApiResult<List<Banner>> =
        withFallback(remoteDataSource.fetchBanners(), DemoDataProvider.banners())

    /**
     * If [primary] is a Failure and demo mode is enabled, swap in the
     * [fallback] demo content.  Note: an empty `Success(emptyList())`
     * does **not** trigger the fallback — server-driven empties win
     * over demo content so prod builds honour admin-controlled empties.
     * CancellationException is handled upstream in
     * [RemoteDataSource.fetchAndParse] (re-thrown) before this helper
     * ever runs, so structured concurrency is not affected here.
     */
    private fun <T> withFallback(
        primary: ApiResult<List<T>>,
        fallback: List<T>
    ): ApiResult<List<T>> {
        if (primary is ApiResult.Success) return primary
        if (!DemoDataProvider.isEnabled) return primary
        return ApiResult.Success(fallback)
    }
}
