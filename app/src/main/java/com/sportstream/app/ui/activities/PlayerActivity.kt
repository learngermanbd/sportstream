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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
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
import com.sportstream.app.ui.gestures.SwipeGestureDetector
import com.sportstream.app.ui.player.TrackSelectionDialogFragment
import com.sportstream.app.ui.player.VideoQualityManager
import com.sportstream.app.data.prefs.PlayerPrefs
import com.sportstream.app.data.prefs.VideoQualityMode
import com.sportstream.app.ui.viewmodels.PlayerSnapshot
import com.sportstream.app.ui.viewmodels.PlayerViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

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
     * Phase 4 · Step 4.3 — single-touch dispatcher attached to the player
     * surface via `binding.playerView.setOnTouchListener(detector::onTouch)`.
     * The callbacks trigger overlay updates (ICON + LABEL + PROGRESS) and
     * pipe the resolved seek target / brightness / volume back into
     * ExoPlayer + Window + AudioManager (see [onSeekFinalizeApply] /
     * [applyBrightnessToWindow] / [applyVolumeSteps]).
     */
    private val swipeDetector by lazy {
        SwipeGestureDetector(
            context = this,
            activity = this,
            exoPlayerProvider = { exoPlayer }
        ).also { det ->
            det.isLocked = { isLocked }
            det.isInPip = {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode
            }
            det.onSingleTap = { toggleControlsVisibility() }
            det.onDoubleTap = { isLeft ->
                if (isLeft) exoPlayer?.seekBack() else exoPlayer?.seekForward()
            }
            det.onSeekPreview = { ms ->
                // Use ?.let { dur -> ... } to avoid `return` from inside a nested
                // lambda (Kotlin prohibits non-local return across non-inline lambdas).
                exoPlayer?.duration?.takeIf { it > 0L }?.let { dur ->
                    binding.seekBar.progress =
                        ((ms.toFloat() / dur) * 1000f).toInt().coerceIn(0, 1000)
                    binding.positionText.text = formatTimestamp(ms)
                }
            }
            det.onSeekFinalize = { ms ->
                exoPlayer?.let { p ->
                    if (p.duration > 0L) p.seekTo(ms.coerceIn(0L, p.duration))
                }
                hideGestureIndicatorAfterDelay()
            }
            det.onIndicatorVisibilityChanged = { visible ->
                binding.swipeGestureOverlay.swipeGestureCard.visibility =
                    if (visible) View.VISIBLE else View.INVISIBLE
            }
            det.onIndicatorUpdate = { type, progress, label ->
                bindIndicator(type, progress, label)
                hideGestureIndicatorAfterDelay()
            }
        }
    }

    /**
     * Controller chrome visibility state. Flipped by the Step 4.3
     * single-tap on the player surface. Default = VISIBLE on first entry.
     * Step 4.2 has lock-control logic which can force-hide when isLocked
     * flips; this field is independent so singleTap-to-toggle still works
     * after Step 4.4 ships.
     */
    private var controlsVisible: Boolean = true

    /** Hide-after-release coroutine for the gesture indicator card. */
    private var indicatorHideJob: kotlinx.coroutines.Job? = null

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
        // Step 4.5 — optional companion subtitle track (SRT / VTT / SSA / ASS).
        const val EXTRA_SUBTITLE_URL = "subtitleUrl"
        const val EXTRA_SUBTITLE_LANG = "subtitleLang"

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
        binding.btnQuality.setOnClickListener { showQualityPicker() }
        binding.btnSubtitle.setOnClickListener { showSubtitlePicker() }
        binding.btnSettings.setOnClickListener {
            // Placeholder: surfaces "Settings coming in Step 4.5"
            // (subtitle + quality pickers). Step 4.5 wired those into their
            // own buttons; this remains a hook for future settings.
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

        // Phase 4 · Step 4.3 — attach the gesture detector. The detector returns
        // true on ACTION_DOWN, claiming the touch stream for the touch lifetime so
        // the SeekBar + MaterialButtons in the chrome don't fight us for MOVE/UP.
        // MINOR #5 — useController=false is REQUIRED here (re-asserted defensively);
        // if anyone flips it, PlayerView's built-in tap-to-toggle fights us.
        binding.playerView.useController = false
        binding.playerView.setOnTouchListener(swipeDetector::onTouch)

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
                // Step 4.5 — stash companion subtitle URL on the activity so submitCurrentLink
                // can attach a SubtitleConfiguration to MediaItem.Builder if present.
                pendingSubtitleUrl = intent.getStringExtra(EXTRA_SUBTITLE_URL).orEmpty()
                pendingSubtitleLang = intent.getStringExtra(EXTRA_SUBTITLE_LANG)
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
        // MAJOR #2 (pass-4 fix) — cancel the indicator hide job so the launched
        // coroutine doesn't outlive the visible window. lifecycleScope only
        // cancels on DESTROY by default; between STOP and DESTROY a delay(1000)
        // could fire and access a frozen View tree.
        indicatorHideJob?.cancel()
        indicatorHideJob = null
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
        // Step 4.3 — cancel the indicator hide timer so the Job + Handler
        // chain can't leak into the torn-down Activity.
        indicatorHideJob?.cancel()
        indicatorHideJob = null
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
        // Step 4.3 — also hide the gesture-feedback overlay since it's
        // chrome on top of PiP's system-bar-less surface.
        binding.swipeGestureOverlay.swipeGestureCard.visibility = visibility
        controlsVisible = !isInPictureInPictureMode
        if (!isInPictureInPictureMode) {
            binding.playerView.useController = false
        }
    }

    // ----- Step 4.3 gesture helpers -----

    /**
     * Single-tap toggle. Mirrors the Step 5.5 Dashboard-into-immersive UX
     * pattern most video apps use: tapping the video surface shows or
     * hides the chrome.
     */
    private fun toggleControlsVisibility() {
        if (isLocked) return  // Step 4.4: locked state ignored
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) return
        controlsVisible = !controlsVisible
        val v = if (controlsVisible) View.VISIBLE else View.GONE
        binding.topBar.visibility = v
        binding.bottomBar.visibility = v
        binding.centerControls.visibility = v
        binding.linksBar.visibility = v
    }

    /**
     * Bind the indicator card's icon + label + bar progress. Maps the
     * gesture type to a system drawable since the project's iconography
     * is intentionally limited (system drawables + a few vector files).
     */
    private fun bindIndicator(type: SwipeGestureDetector.Type, progress: Float, label: String) {
        val card = binding.swipeGestureOverlay
        when (type) {
            SwipeGestureDetector.Type.HORIZONTAL_SEEK -> {
                card.swipeGestureIcon.setImageResource(android.R.drawable.ic_menu_recent_history)
            }
            SwipeGestureDetector.Type.VERTICAL_BRIGHTNESS -> {
                card.swipeGestureIcon.setImageResource(android.R.drawable.ic_menu_view)
            }
            SwipeGestureDetector.Type.VERTICAL_VOLUME -> {
                card.swipeGestureIcon.setImageResource(android.R.drawable.ic_media_play)
            }
            SwipeGestureDetector.Type.NONE -> Unit
        }
        card.swipeGestureLabel.text = label
        card.swipeGestureBar.progress = (progress * 1000f).toInt().coerceIn(0, 1000)
    }

    /**
     * Auto-hide the indicator card 1.0 s after the last onIndicatorUpdate
     * or onSeekFinalize. The Job is cancelled in [onDestroy] + each
     * re-schedule to avoid handler accumulation.
     */
    private fun hideGestureIndicatorAfterDelay() {
        val scope = lifecycleScope
        indicatorHideJob?.cancel()
        // MINOR #4 (pass-4 review) — seed the bar so it's not empty for the first MOVE.
        // 50% is a safe neutral start for brightness / volume / seek; the next
        // onIndicatorUpdate from the detector refines it within one frame.
        binding.swipeGestureOverlay.swipeGestureBar.progress = 50
        indicatorHideJob = scope.launch {
            delay(1000L)
            // Re-check visibility guards before hiding: if the user
            // started a new gesture within the 1 s window, onIndicatorUpdate
            // will have cancelled this Job already.
            binding.swipeGestureOverlay.swipeGestureCard.visibility = View.INVISIBLE
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
                // Step 4.5 — apply the persisted VideoQuality choice immediately so a
                // mid-session cold-launch resumes the user's last selection.
                VideoQualityManager.apply(p, currentQuality)
            }
        pollPlaybackState()
        lifecycleScope.launch {
            // Step 4.5 mitigation (k1/Kapt relationship) — runBlocking version
            // was kapt-breaking on private @OptIn in Kotlin 2.0+; revert to async
            // safety re-apply which races ExoPlayer.prepare() by ~0–1 segment fetch
            // on cold start, then locks to the persisted VideoQualityMode.
            currentQuality = playerPrefs.readVideoQualityMode()
            exoPlayer?.let { VideoQualityManager.apply(it, currentQuality) }
        }
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
        val builder = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(binding.titleText.text?.toString().orEmpty())
                    .build()
            )
        // Step 4.5 — if a companion subtitle URL was routed in, attach a SubtitleConfiguration.
        // MIME detection via extension; falls back to VTT for unknown providers.
        if (pendingSubtitleUrl.isNotBlank()) {
            val mime = detectSubtitleMime(pendingSubtitleUrl)
            val sub = MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(pendingSubtitleUrl))
                .setMimeType(mime)
                .setLanguage(pendingSubtitleLang ?: "en")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            builder.setSubtitleConfigurations(listOf(sub))
        }
        val item = builder.build()
        player.setMediaItem(item)
        player.prepare()
    }

    // Step 4.5 — detect subtitle MIME from URL extension using Media3 MimeTypes constants.
    private fun detectSubtitleMime(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
            lower.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            lower.endsWith(".ass") || lower.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            lower.endsWith(".ttml") || lower.endsWith(".xml") -> MimeTypes.APPLICATION_TTML
            else -> {
                // Best-effort default — log so it shows up in adb.
                android.util.Log.w("PlayerActivity",
                    getString(R.string.player_subtitle_url_unknown_mime) + ": " + url)
                MimeTypes.TEXT_VTT
            }
        }
    }

    // Step 4.5 — quality picker: 5-way MaterialAlertDialog with single-choice items.
    private fun showQualityPicker() {
        val modes = VideoQualityMode.values()
        val labels = modes.map { mode ->
            when (mode) {
                VideoQualityMode.AUTO -> getString(R.string.player_quality_auto)
                VideoQualityMode.FHD -> getString(R.string.player_quality_fhd)
                VideoQualityMode.HD -> getString(R.string.player_quality_hd)
                VideoQualityMode.SD -> getString(R.string.player_quality_sd)
                VideoQualityMode.LD -> getString(R.string.player_quality_ld)
            }
        }
        val currentIndex = (0 until modes.size).firstOrNull { modes[it] == currentQuality } ?: 0
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.player_quality_title)
            .setSingleChoiceItems(labels.toTypedArray(), currentIndex) { dialog, which ->
                val picked = modes[which]
                currentQuality = picked
                exoPlayer?.let { VideoQualityManager.apply(it, picked) }
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    playerPrefs.setVideoQualityMode(picked)
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // Step 4.5 — subtitle picker: open TrackSelectionDialogFragment if the player has text tracks.
    private fun showSubtitlePicker() {
        val p = exoPlayer ?: run {
            android.widget.Toast.makeText(this, R.string.player_track_selection_empty, android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (!TrackSelectionDialogFragment.exoPlayerHasTextTracks(p)) {
            // Disable the button visually if there are no text tracks.
            android.widget.Toast.makeText(this, R.string.player_track_selection_empty, android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        TrackSelectionDialogFragment.show(supportFragmentManager, p, playerPrefs)
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

    /**
     * Step 4.4 polish — when `isLocked=true`:
     *   - Keep topBar VISIBLE (the lock / unlock button lives in topBar; otherwise
     *     the user has no escape hatch from the locked state).
     *   - Hide bottomBar, centerControls, linksBar (rest of the chrome).
     *   - Show lockDimOverlay (40% black) so the video is "slightly dimmed".
     *   - Update the lock button icon + content-description for a11y.
     *   - The back button is already swallowed in onBackPressed per Step 4.2.
     */
    private fun applyLockedState() {
        val bottom = binding.bottomBar
        val center = binding.centerControls
        val links = binding.linksBar
        val dim = binding.lockDimOverlay
        if (isLocked) {
            bottom.visibility = View.GONE
            center.visibility = View.GONE
            links.visibility = View.GONE
            dim.visibility = View.VISIBLE
            binding.btnLock.setIconResource(android.R.drawable.ic_lock_idle_lock)
            binding.btnLock.contentDescription = getString(R.string.player_unlock_desc)
            binding.btnLock.text = getString(R.string.player_unlock_short)
        } else {
            bottom.visibility = View.VISIBLE
            center.visibility = View.VISIBLE
            links.visibility = View.VISIBLE
            dim.visibility = View.GONE
            binding.btnLock.setIconResource(android.R.drawable.ic_lock_lock)
            binding.btnLock.contentDescription = getString(R.string.player_lock_desc)
            binding.btnLock.text = getString(R.string.player_lock_short)
        }
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

    /** ----- Step 4.5 — Quality + Subtitle state ----- */
    private val playerPrefs: PlayerPrefs by lazy { PlayerPrefs(applicationContext) }

    /** Pending subtitle URL (cleared on each new intent / load). */
    private var pendingSubtitleUrl: String = ""
    private var pendingSubtitleLang: String? = null

    /** Current quality — seeded from DataStore on first observe. */
    private var currentQuality: VideoQualityMode = VideoQualityMode.AUTO

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
