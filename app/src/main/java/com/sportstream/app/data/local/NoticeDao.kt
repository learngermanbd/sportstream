package com.sportstream.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Phase 5 · Step 5.5 v2 — Notice DAO.
 *
 * Expiration filtering happens client-side (`.map { it.filter(active) }`
 * downstream) instead of in the SQL query. Reasoning:
 *   1. `now()` is dynamic so a Flow with a parameterised query would
 *      restart the observer every second; filtering in Kotlin emits
 *      only when the underlying row set changes AND keeps the wire
 *      declaration readable.
 *   2. Re-evaluation when the device clock changes is a non-feature
 *      for this screen (notices with `expiresAt` in the past are
 *      dormant, not deleted).
 *
 * `prunePushSourced(olderThanMillis)` is house-keeping — pushed
 * notices that arrived >7 days ago are swept so the table doesn't
 * grow unbounded. Server-fetched notices are kept indefinitely.
 */
@Dao
interface NoticeDao {

    /**
     * All rows ordered by section-then-creator DESC.
     * NoticeFragment's adapter sorts again at the section level using
     * priority DESC + createdAt DESC.
     */
    @Query("""
        SELECT * FROM notices
        ORDER BY
            CASE section
                WHEN 'ALERT' THEN 0
                WHEN 'INFO' THEN 1
                WHEN 'PROMO' THEN 2
                ELSE 3
            END ASC,
            priority DESC,
            createdAt DESC
    """)
    fun observeAll(): Flow<List<NoticeEntity>>

    @Query("SELECT * FROM notices WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): NoticeEntity?

    /** INSERT-OR-REPLACE so push re-deliveries collapse to one row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(notice: NoticeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(notices: List<NoticeEntity>)

    @Query("DELETE FROM notices WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Sweep push-sourced notices older than [olderThan] millis.
     * Returns the row count so the caller can log a housekeeping trace.
     * NOT public — invoked from [com.sportstream.app.data.repository.NoticeRepository].
     */
    @Query("DELETE FROM notices WHERE isPushSourced = 1 AND createdAt < :olderThan")
    suspend fun prunePushSourced(olderThan: Long): Int

    @Query("DELETE FROM notices")
    suspend fun deleteAll()
}
