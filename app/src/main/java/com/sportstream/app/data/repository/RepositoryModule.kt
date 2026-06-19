package com.sportstream.app.data.repository

import com.sportstream.app.data.local.LocalDataSource
import com.sportstream.app.data.remote.RemoteDataSource

/**
 * Phase 2 · Step 2.4 — Repository DI seam.
 *
 * Lazy singletons over the data sources, parallel to [com.sportstream
 * .app.data.remote.NetworkModule] and [com.sportstream.app.data.local
 * .LocalModule]. Step 2.5 ViewModels reach these via
 * `app.repository.mainRepository` / `favoritesRepository` /
 * `playlistRepository`.
 *
 * Construction order: the constructor takes the data sources already
 * resolved, so [com.sportstream.app.SportStreamApp] only needs to
 * instantiate this once after `network` + `local` are wired up.
 */
class RepositoryModule(
    remoteDataSource: RemoteDataSource,
    localDataSource: LocalDataSource
) {
    val mainRepository: MainRepository by lazy { MainRepository(remoteDataSource) }
    val favoritesRepository: FavoritesRepository by lazy { FavoritesRepository(localDataSource) }
    val playlistRepository: PlaylistRepository by lazy { PlaylistRepository(localDataSource) }
}
