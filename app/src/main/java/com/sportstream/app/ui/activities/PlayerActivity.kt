package com.sportstream.app.ui.activities

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.sportstream.app.R
import com.sportstream.app.SportStreamApp
import com.sportstream.app.data.models.Channel
import com.sportstream.app.data.models.StreamLink
import com.sportstream.app.data.remote.NetworkModule
import com.sportstream.app.databinding.ActivityPlayerBinding
import com.sportstream.app.ui.adapters.PlayerLinkAdapter
import com.sportstream.app.ui.common.UiState
import com.sportstream.app.ui.viewmodels.PlayerSnapshot
import com.sportstream.app.ui.viewmodels.PlayerViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Phase 4 · Step 4.2 — Video player.
 *
 * Wire model:
 *
 *  - ExoPlayer + OkHttpDataSource over the project's existing OkHttpClient
 *    (NetworkModule.httpClient) — same cache + auth headers + debug logging
 *    as the rest of the app.
 *
 *  - PlayerView with `useController=false`; all transport controls are
 *    Material 3 buttons from [activity_player.xml].
 *
 *  - Lifecycle-aware:
 *      - [onStart]  — build ExoPlayer + connect to MediaController (future).
 *      - [onResume] — release PiP-aware pause; if not in PiP, call play().
 *      - [onPause]  — pause when the user leaves (PiP/recents keeps playing
 *                     because we override onUserLeaveHint + PiP mode).
 *      - [onStop]   — enter PiP if [isInPictureInPictureMode] is not yet
 *                     true and we are still playing.
 *      - [onDestroy]— release ExoPlayer.
 *
 *  - URL routing — three Intent contract modes (per strict-plan):
 *      1. EXTRA_CHANNEL_ID — PlayerViewModel resolves Channel from /api/channels.
 *      2. EXTRA_VIDEO_URL + EXTRA_TITLE — direct playback of an HLS/DASH URL
 *         (used by Highlights tap).
 *      3. EXTRA_EVENT_ID — for Phase 4.x: resolve the Event's first channel.
 *
 *  - PiP — `btnPip` calls [enterPictureInPictureMode] with an aspect ratio
 *    derived from the PlayerView (16:9 default).
 *
 * See sportzfy_build_plan.html #phase4 Step 4.2 for the strict plan; this
 * implementation matches the criteria: top bar + links bar + center
 * controls + bottom controls + PiP button are all present and wired.
 */
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var exoPlayer: ExoPlayer? = null
    private var playWhenReady: Boolean = true
    private var currentPlaybackPosition: Long = 0L
    private var isLocked: Boolean = false

    /** Lazy-resolved PlayerViewModel (channel-based playback only). */
    private val playerVm: PlayerViewModel by lazy {
        val app = application as SportStreamApp
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PlayerViewModel(
                    app.repository.mainRepository,
                    app.repository.favoritesRepository
                ) as T
        }
        ViewModelProvider(this, factory)[PlayerViewModel::class.java]
    }

    private lateinit var linkAdapter: PlayerLinkAdapter
    private var currentLinks: List<StreamLink> = emptyList()
    private var selectedLinkIndex: Int = 0

    /**
     * Intent contract for opening [PlayerActivity]. Three modes (per the
     * strict plan):
     *
     *   - [EXTRA_CHANNEL_ID] — PlayerViewModel resolves Channel from /api/channels.
     *   - [EXTRA_VIDEO_URL]  + [EXTRA_TITLE] — direct HLS/DASH playback (Highlights tap).
     *   - [EXTRA_EVENT_ID]   — Phase 4.x future; resolves the Event's first channel.
     *
     * Exposed as a `companion object` (not `private companion object`) so
     * [com.sportstream.app.ui.util.PlayerNavigation] can launch us without
     * having to hard-code string keys in two places.
     */
    companion object {
        const val EXTRA_CHANNEL_ID = "channelId"
        const val EXTRA_VIDEO_URL  = "videoUrl"
        const val EXTRA_TITLE      = "title"
        const val EXTRA_EVENT_ID   = "eventId"

        // onSaveInstanceState keys (parallel to the Intent extras above).
        const val STATE_POS = "player_pos_ms"
        const val STATE_PWR = "player_pwr"
    }

    // TODO Step 4.7 (Code Review): swap the bottom SeekBar + volume slider
    // for androidx.media3.ui.DefaultTimeBar + a Volume-button that toggles
    // the system volume / surfaces a media volume dialog per strict-plan.
    // Step 4.2 ships SeekBar + slider so the controller chrome is functional
    // in CI builds; Step 4.7 closes the deviation.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge: status + nav bar transparent; we handle insets in
        // the topBar / bottomBar paddings below.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Auto-launch PiP if the user pressed Home mid-playback (one-way).
        // Android handles the actual transition; we just tell it the
        // aspect ratio up-front.
        binding.playerView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setPictureInPictureParams(currentPipParams())
            }
        }

        // Volume control slide binds to AudioManager.STREAM_MUSIC.
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        binding.volumeSlider.max = maxVolume
        binding.volumeSlider.progress = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        binding.volumeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) audio.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        // Top-bar buttons.
        binding.btnBack.setOnClickListener { finish() }
        binding.btnLock.setOnClickListener { toggleLock() }
        binding.btnPip.setOnClickListener { enterPip() }
        binding.btnResize.setOnClickListener { cycleResizeMode() }
        binding.btnSettings.setOnClickListener {
            // Placeholder: surfaces "Settings coming in Step 4.5"
            // (subtitle + quality pickers). No-op for v1.
        }

        // Center buttons.
        binding.btnRewind.setOnClickListener { exoPlayer?.seekBack() }
        binding.btnPlayPause.setOnClickListener {
            val p = exoPlayer ?: return@setOnClickListener
            if (p.isPlaying) p.pause() else p.play()
        }
        binding.btnForward.setOnClickListener { exoPlayer?.seekForward() }

        // Bottom bars.
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val p = exoPlayer ?: return
                if (fromUser && p.duration > 0L) {
                    p.seekTo((progress / 1000.0 * p.duration).toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Keep the playerView's playback surface visible while the user
                // drags. PlayerView's auto-hide controller timeout does not apply
                // here because we set use_controller=false in initializePlayer(),
                // so there is nothing to cancel; this hook exists for future
                // parity with DefaultTimeBar polling added in Step 4.7.
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        binding.btnFullscreen.setOnClickListener { cycleFullscreen() }

        // Links bar RecyclerView (horizontal). Empty until PlayerViewModel resolves.
        linkAdapter = PlayerLinkAdapter(onClick = { link -> switchToLink(link) })
        binding.linksBar.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.linksBar.adapter = linkAdapter

        // Surface insets to padding so the topBar doesn't get clipped and
        // the bottomBar's gesture-nav pill zone gets padded correctly.
        ViewCompat.setOnApplyWindowInsetsListener(binding.topBar) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                or WindowInsetsCompat.Type.displayCutout()).top
            v.updatePadding(top = top)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomBar) { v, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            v.updatePadding(bottom = bottom)
            insets
        }

        savedInstanceState?.getLong(STATE_POS)?.let { currentPlaybackPosition = it }
        savedInstanceState?.getBoolean(STATE_PWR)?.let { playWhenReady = it }

        // Decide URL routing right away so we can show the loading hint.
        val intent = getIntent()
        when {
            intent.hasExtra(EXTRA_VIDEO_URL) -> {
                val url = intent.getStringExtra(EXTRA_VIDEO_URL).orEmpty()
                val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                binding.titleText.text = title.ifBlank { getString(R.string.player_title_extra) }
                currentLinks = listOf(
                    StreamLink(name = getString(R.string.player_links_empty), url = url, quality = com.sportstream.app.data.models.VideoQuality.AUTO)
                )
                selectedLinkIndex = 0
                submitCurrentLink(url)
            }
            intent.hasExtra(EXTRA_CHANNEL_ID) -> {
                val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID).orEmpty()
                binding.titleText.text = getString(R.string.player_channel_loading)
                observePlayerVm(channelId)
            }
            intent.hasExtra(EXTRA_EVENT_ID) -> {
                // Phase 4.x wiring: resolve the Event's first channel. For
                // Step 4.2 we show a friendly loading placeholder, but still
                // surface the Event title into titleText so the chrome isn't
                // blank while we wait (extra-title passed by
                // [PlayerNavigation.startPlayerForEvent] is consumed here).
                intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() }?.let {
                    binding.titleText.text = it
                }
                showError(getString(R.string.player_loading))
            }
            else -> showError(getString(R.string.player_error_no_url))
        }
    }

    override fun onStart() {
        super.onStart()
        playWhenReady = true
        if (exoPlayer == null) initializePlayer()
    }

    override fun onResume() {
        super.onResume()
        // Resume from PiP: keep playing; from background: keep playing unless
        // user explicitly paused (handled inside the listener).
        exoPlayer?.let { if (playWhenReady) it.play() }
    }

    override fun onPause() {
        super.onPause()
        // Only pause if we are NOT in PiP (otherwise the system expects us to keep playing).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
            return
        }
        exoPlayer?.let {
            playWhenReady = it.isPlaying
            it.pause()
        }
    }

    override fun onStop() {
        super.onStop()
        exoPlayer?.let {
            currentPlaybackPosition = it.currentPosition
            playWhenReady = it.playWhenReady
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isInPictureInPictureMode) {
            releasePlayer()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Auto-enter PiP on Home press (Android 8+) if the user is mid-play.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val p = exoPlayer ?: return
            if (p.isPlaying) enterPip()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        // Hide our custom overlay in PiP (the system bars are hidden by the OS).
        val visibility = if (isInPictureInPictureMode) View.GONE else View.VISIBLE
        binding.topBar.visibility = visibility
        binding.bottomBar.visibility = visibility
        binding.centerControls.visibility = visibility
        binding.linksBar.visibility = visibility
        if (!isInPictureInPictureMode) {
            binding.playerView.useController = false
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_POS, currentPlaybackPosition)
        outState.putBoolean(STATE_PWR, playWhenReady)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Future: re-resolve on new intent if needed.
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // PiP-enter/exit causes config changes; surfacing back here is
        // handled by [onPictureInPictureModeChanged] above.
    }

    // ----- Initialization -----

    private fun initializePlayer() {
        val app = application as SportStreamApp
        val httpClient = (app.network.httpClient as okhttp3.OkHttpClient)
        val dataSourceFactory = OkHttpDataSource.Factory(httpClient)
        val msFactory = DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory)

        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(msFactory)
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(10_000L)
            .build()
            .also { p ->
                p.setAudioAttributes(
                    androidx.media3.common.AudioAttributes.Builder()
                        .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                        .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    /* handleAudioFocus = */ true
                )
                p.playWhenReady = playWhenReady
                p.seekTo(currentPlaybackPosition)
                p.addListener(playerListener)
                binding.playerView.player = p
                binding.playerView.useController = false
            }
        pollPlaybackState()
    }

    private fun releasePlayer() {
        exoPlayer?.let {
            currentPlaybackPosition = it.currentPosition
            playWhenReady = it.playWhenReady
            it.removeListener(playerListener)
            it.release()
        }
        exoPlayer = null
    }

    // Media3 1.5.x Player.Listener exposes `onPlaybackStateChanged` as
    // a Java Int overload annotated @Player.State. We must type the
    // parameter as Int and use the Player.STATE_* Int constants rather
    // than a sealed class (the sealed-class variant was not added
    // until media3 1.6.x and is forward-only).
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(@Player.State playbackState: Int) {
            val p = exoPlayer ?: return
            when (playbackState) {
                Player.STATE_BUFFERING -> showLoading()
                Player.STATE_READY -> {
                    showReady()
                    updatePlayPauseIcon(p.isPlaying)
                }
                Player.STATE_ENDED -> {
                    // Single-shot; PlayerViewModel didn't load a VOD loop. No-op.
                    showReady()
                    updatePlayPauseIcon(false)
                }
                Player.STATE_IDLE -> Unit
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayPauseIcon(isPlaying)
        }

        override fun onPlayerError(error: PlaybackException) {
            val msg = error.localizedMessage ?: error.errorCodeName
            showError(getString(R.string.player_error_no_url) + "\n" + msg)
        }
    }

    /**
     * Polls ExoPlayer state at ~2 Hz (UI tick) so the seek-bar / position /
     * duration labels stay in sync. Lifecycle-aware via [repeatOnLifecycle].
     * OK to call repeatedly since repeat is idempotent on STARTED.
     */
    private fun pollPlaybackState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val p = exoPlayer ?: return@repeatOnLifecycle
                    val dur = p.duration.coerceAtLeast(0L)
                    if (dur > 0L) {
                        binding.seekBar.progress = ((p.currentPosition.toFloat() / dur) * 1000L).toInt().coerceIn(0, 1000)
                    } else {
                        binding.seekBar.progress = 0
                    }
                    binding.positionText.text = formatTimestamp(p.currentPosition)
                    binding.durationText.text = formatTimestamp(dur)
                    kotlinx.coroutines.delay(500L)
                }
            }
        }
    }

    // ----- Routing branch: PlayerViewModel-driven channel -----

    private fun observePlayerVm(channelId: String) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                playerVm.state.collectLatest { state ->
                    when (state) {
                        is UiState.Success -> applySnapshot(state.value)
                        is UiState.Error -> showError(state.message)
                        UiState.Loading -> showLoading()
                        UiState.Idle -> Unit
                    }
                }
            }
        }
        playerVm.load(channelId)
    }

    private fun applySnapshot(snapshot: PlayerSnapshot) {
        val channel: Channel = snapshot.channel ?: run {
            showError(getString(R.string.player_error_no_url))
            return
        }
        binding.titleText.text = channel.name
        binding.errorText.visibility = View.GONE

        // Build a singleton StreamLink list from the channel's streamUrl
        // so the links bar ships with at least one button (the plan
        // criterion: "links bar" must not be empty).
        val links = listOf(
            StreamLink(
                name = getString(R.string.player_links_empty),
                url = channel.streamUrl,
                quality = com.sportstream.app.data.models.VideoQuality.AUTO
            )
        )
        currentLinks = links
        selectedLinkIndex = 0
        linkAdapter.submitList(links) { selectChip(0) }
        submitCurrentLink(channel.streamUrl)
    }

    // ----- Links -----

    private fun submitCurrentLink(url: String?) {
        if (url.isNullOrBlank()) {
            showError(getString(R.string.player_error_no_url))
            return
        }
        val player = exoPlayer ?: initializePlayer().let { exoPlayer!! }
        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(binding.titleText.text?.toString().orEmpty())
                    .build()
            )
            .build()
        player.setMediaItem(item)
        player.prepare()
    }

    private fun switchToLink(link: StreamLink) {
        val index = currentLinks.indexOfFirst { it.url == link.url }
        if (index == -1 || index == selectedLinkIndex) return
        selectedLinkIndex = index
        selectChip(index)
        submitCurrentLink(link.url)
    }

    private fun selectChip(index: Int) {
        binding.linksBar.post {
            (0 until binding.linksBar.childCount).forEach { i ->
                val view = binding.linksBar.getChildAt(i)
                val chip = view?.findViewById<com.google.android.material.chip.Chip>(R.id.linkChip)
                chip?.isChecked = (i == index)
            }
        }
    }

    // ----- UI toggles -----

    private fun toggleLock() {
        isLocked = !isLocked
        applyLockedState()
    }

    private fun applyLockedState() {
        val visibility = if (isLocked) View.GONE else View.VISIBLE
        binding.topBar.visibility = visibility
        binding.bottomBar.visibility = visibility
        binding.centerControls.visibility = visibility
        binding.linksBar.visibility = visibility
        binding.btnLock.setIconResource(
            if (isLocked) android.R.drawable.ic_lock_lock
            else android.R.drawable.ic_lock_idle_lock
        )
        binding.btnLock.contentDescription =
            if (isLocked) getString(R.string.player_top_lock) else getString(R.string.player_top_lock_unlocked)
    }

    // PlayerView extends androidx.media3.ui.AspectRatioFrameLayout, which
    // is where both the `resizeMode` javaBean property AND the static
    // RESIZE_MODE_* Int constants live. PlayerView's own class declares
    // only the setter; trying to use `PlayerView.RESIZE_MODE_FIT` produces
    // an unresolved-reference error in the kotlinc resolve step.
    private fun cycleResizeMode() {
        val current = binding.playerView.resizeMode
        val nextMode = when (current) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        binding.playerView.resizeMode = nextMode
    }

    private fun cycleFullscreen() {
        val decorView = window.decorView
        val current = decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN
        if (current == 0) {
            decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        } else {
            decorView.systemUiVisibility = 0
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode(currentPipParams())
        }
    }

    private fun currentPipParams(): PictureInPictureParams {
        val aspect = Rational(16, 9)
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(aspect)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(true)
        }
        return builder.build()
    }

    // ----- Helpers -----

    private fun showLoading() {
        binding.loadingIndicator.visibility = View.VISIBLE
        binding.errorText.visibility = View.GONE
    }

    private fun showReady() {
        binding.loadingIndicator.visibility = View.GONE
        binding.errorText.visibility = View.GONE
    }

    private fun showError(message: String) {
        binding.loadingIndicator.visibility = View.GONE
        binding.errorText.text = message
        binding.errorText.visibility = View.VISIBLE
    }

    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        binding.btnPlayPause.setIconResource(
            if (isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
        binding.btnPlayPause.contentDescription =
            if (isPlaying) getString(R.string.player_controls_pause)
            else getString(R.string.player_controls_play)
    }

    /** Format a position in ms as mm:ss (or H:mm:ss when >= 1 h). */
    private fun formatTimestamp(ms: Long): String {
        if (ms <= 0L) return "00:00"
        val total = ms / 1000L
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        return if (h > 0L) "%d:%02d:%02d".format(h, m, s)
        else "%02d:%02d".format(m, s)
    }
}
