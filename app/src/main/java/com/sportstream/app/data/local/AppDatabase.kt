package com.sportstream.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Phase 2 \u00b7 Step 2.3 \u2014 AppDatabase (v1).
 *
 * Aggregates the two tables added in this step:
 *  \u2022 favorites  \u2014 [FavoriteEntity] / [FavoriteDao]
 *  \u2022 playlists  \u2014 [PlaylistEntity] / [PlaylistDao]
 *
 * `exportSchema = false` because no migration tests yet. Once Step 2.7
 * (Room instrumentation tests) lands we'll flip this on and queue the
 * generated JSON under app/schemas/.
 *
 * Database name is shared across the application via [DATABASE_NAME] so
 * [LocalModule] can resolve `Room.databaseBuilder(context, AppDatabase::class.java, ...)`.
 */
@Database(
    entities = [
        FavoriteEntity::class,
        PlaylistEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun favoriteDao(): FavoriteDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        const val DATABASE_NAME = "sportstream.db"
    }
}
