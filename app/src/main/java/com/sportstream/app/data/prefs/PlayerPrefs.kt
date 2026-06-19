package com.sportstream.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Step 4.5 — DataStore-backed preferences for player settings.
 *
 * Two keys today:
 *  - KEY_VIDEO_QUALITY ("video_quality_mode")  → [VideoQualityMode.name] (AUTO / FHD / HD / SD / LD)
 *  - KEY_SUBTITLE_LANG  ("subtitle_track_id")  → opaque track id or empty / OFF
 *
 * Scope: GLOBAL (one value applies to every video player session). Per-URL override
 * maps (URL → quality) are deferred to Step 4.7 polish per the strict-plan
 * deviation list — the user explicitly preferred per-URL persistence noted in the plan
 * but the v1 code keeps it simple.
 *
 * Reads are suspend (use [first] for one-shot reads inside coroutines); writes use [edit].
 */
val Context.playerDataStore: DataStore<Preferences> by preferencesDataStore(name = "player_prefs")

class PlayerPrefs(private val context: Context) {

    /** Latest persisted video quality. Defaults to [VideoQualityMode.AUTO]. */
    val videoQualityModeFlow: Flow<VideoQualityMode>
        get() = context.playerDataStore.data.map { prefs ->
            val saved = prefs[KEY_VIDEO_QUALITY]
            VideoQualityMode.fromStorageKey(saved) ?: VideoQualityMode.AUTO
        }

    /** Latest persisted subtitle track id (or empty/Off). */
    val subtitleTrackIdFlow: Flow<String>
        get() = context.playerDataStore.data.map { prefs ->
            prefs[KEY_SUBTITLE_LANG].orEmpty()
        }

    suspend fun setVideoQualityMode(mode: VideoQualityMode) {
        context.playerDataStore.edit { prefs ->
            prefs[KEY_VIDEO_QUALITY] = mode.storageKey
        }
    }

    suspend fun setSubtitleTrackId(id: String) {
        context.playerDataStore.edit { prefs ->
            prefs[KEY_SUBTITLE_LANG] = id
        }
    }

    /** One-shot read for the startup-apply step. */
    suspend fun readVideoQualityMode(): VideoQualityMode = videoQualityModeFlow.first()
    suspend fun readSubtitleTrackId(): String = subtitleTrackIdFlow.first()

    companion object {
        val KEY_VIDEO_QUALITY = stringPreferencesKey("video_quality_mode")
        val KEY_SUBTITLE_LANG = stringPreferencesKey("subtitle_track_id")
    }
}

/**
 * Resolution ladder for [VideoQualityManager].
 *
 *  - [AUTO]  — leave Media3 adaptive selection enabled. No TrackSelectionOverride.
 *  - [FHD]   — ≤ 1080p and > 720p track wins if present.
 *  - [HD]    — ≤ 720p  and > 480p track wins if present.
 *  - [SD]    — ≤ 480p  and > 360p track wins if present.
 *  - [LD]    — ≤ 360p  track wins if present.
 */
enum class VideoQualityMode(val storageKey: String, val maxHeightInclusive: Int) {
    AUTO("auto", Int.MAX_VALUE),
    FHD("fhd", 1080),
    HD("hd", 720),
    SD("sd", 480),
    LD("ld", 360);

    companion object {
        fun fromStorageKey(key: String?): VideoQualityMode? = values().firstOrNull { it.storageKey == key }
    }
}
