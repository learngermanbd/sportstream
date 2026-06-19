package com.sportstream.app.data.local

import android.content.Context
import androidx.room.Room

/**
 * Phase 2 \u00b7 Step 2.3 \u2014 Local DI seam (parallel to NetworkModule).
 *
 * Built once on Application.onCreate and exposed via `app.local.*` so
 * ViewModels / Repositories can stay free of Room / Context plumbing.
 *
 *  app.local.database        \u2014 the singleton [AppDatabase]
 *  app.local.favoriteDao     \u2014 [FavoriteDao]
 *  app.local.playlistDao     \u2014 [PlaylistDao]
 *  app.local.localDataSource \u2014 [LocalDataSource] (high-level wrappers)
 */
class LocalModule(
    context: Context
) {

    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration() // v1 \u2192 v2 destructive until Step 2.7 adds real migrations
            .build()
    }

    val favoriteDao: FavoriteDao by lazy { database.favoriteDao() }
    val playlistDao: PlaylistDao by lazy { database.playlistDao() }

    val localDataSource: LocalDataSource by lazy {
        LocalDataSource(favoriteDao = favoriteDao, playlistDao = playlistDao)
    }
}
