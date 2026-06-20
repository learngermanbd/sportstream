package com.sportstream.app.data.demo

import com.sportstream.app.data.models.Banner
import com.sportstream.app.data.models.Category
import com.sportstream.app.data.models.Channel
import com.sportstream.app.data.models.Event
import com.sportstream.app.data.models.EventStatus
import com.sportstream.app.data.models.Highlight
import com.sportstream.app.data.models.StreamLink
import com.sportstream.app.data.models.Team
import com.sportstream.app.data.models.VideoQuality

/**
 * Phase 7 · Step 7.13 — Offline demo content provider.
 *
 * Provides sample sports streaming content used as a fallback when the
 * remote API is unreachable.  Lets the app demonstrate full functionality
 * (live events, categorized channels, highlights, banners) in offline
 * mode or during demo builds.
 *
 * ## Sources
 *  - Public test HLS streams from Mux/Apple/Akamai (no auth required, CDN-hosted).
 *  - Sample team/channel names are generic placeholders — no copyrighted brands.
 *
 * ## Usage
 * ```kotlin
 * val events = DemoDataProvider.events()
 * val channels = DemoDataProvider.channels()
 * ```
 *
 * **Toggle:** Set [isEnabled] = false to disable offline fallback entirely
 * (e.g. for production builds where empty-states are preferred). Phase 7
 * Step 7.13 defaults to enabled so the Step 7.13 release-build demo APK
 * shows full content offline.
 */
object DemoDataProvider {

    /**
     * Whether demo content should be served offline.  Disabled in production
     * builds where empty-states are preferred over potentially-misleading
     * demo data.
     */
    const val isEnabled: Boolean = true

    // ── HLS / DASH test streams (publicly hosted, no auth required) ──
    private const val HLS_DEMO_1 =
        "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
    private const val HLS_DEMO_2 =
        "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8"
    private const val HLS_DEMO_3 =
        "https://test-streams.mux.dev/test_001/stream.m3u8"
    private const val HLS_DEMO_4 =
        "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8"
    private const val HLS_DEMO_5 =
        "https://test-streams.mux.dev/pts_shift/master.m3u8"
    private const val HLS_DEMO_6 =
        "https://multiplatform-f.akamaihd.net/i/multi/will/bunny/big_buck_bunny_,640x360_400,640x360_700,640x360_1000,950x540_1500,.f4v.csmil/master.m3u8"

    // ── Sample categories ──
    fun categories(): List<Category> = listOf(
        Category(id = "football",   name = "Football",   iconUrl = null, sortOrder = 1, isVisible = true),
        Category(id = "basketball", name = "Basketball", iconUrl = null, sortOrder = 2, isVisible = true),
        Category(id = "cricket",    name = "Cricket",    iconUrl = null, sortOrder = 3, isVisible = true),
        Category(id = "tennis",     name = "Tennis",     iconUrl = null, sortOrder = 4, isVisible = true),
        Category(id = "general",    name = "General",    iconUrl = null, sortOrder = 5, isVisible = true)
    )

    // ── Sample channels (24/7 live) ──
    fun channels(): List<Channel> = listOf(
        Channel(id = "demo-ch-1", name = "Sports Live 1",  logoUrl = null, streamUrl = HLS_DEMO_1, category = "football",   isActive = true, sortOrder = 1),
        Channel(id = "demo-ch-2", name = "Sports Live 2",  logoUrl = null, streamUrl = HLS_DEMO_2, category = "football",   isActive = true, sortOrder = 2),
        Channel(id = "demo-ch-3", name = "Hoops HD",       logoUrl = null, streamUrl = HLS_DEMO_3, category = "basketball", isActive = true, sortOrder = 3),
        Channel(id = "demo-ch-4", name = "Cricket Network",logoUrl = null, streamUrl = HLS_DEMO_4, category = "cricket",    isActive = true, sortOrder = 4),
        Channel(id = "demo-ch-5", name = "Tennis Channel", logoUrl = null, streamUrl = HLS_DEMO_5, category = "tennis",     isActive = true, sortOrder = 5),
        Channel(id = "demo-ch-6", name = "Sports Highlights", logoUrl = null, streamUrl = HLS_DEMO_6, category = "general",isActive = true, sortOrder = 6),
        Channel(id = "demo-ch-7", name = "Premier Sports", logoUrl = null, streamUrl = HLS_DEMO_1, category = "football",   isActive = true, sortOrder = 7),
        Channel(id = "demo-ch-8", name = "NBA Stream",     logoUrl = null, streamUrl = HLS_DEMO_3, category = "basketball", isActive = true, sortOrder = 8),
        Channel(id = "demo-ch-9", name = "IPL Live",       logoUrl = null, streamUrl = HLS_DEMO_4, category = "cricket",    isActive = true, sortOrder = 9)
    )

    // ── Sample live & upcoming events ──
    fun events(): List<Event> = listOf(
        Event(
            id = "demo-evt-1",
            title = "Live: Premier League Clash",
            teamA = Team(name = "City United", logoUrl = null),
            teamB = Team(name = "Rovers FC",   logoUrl = null),
            date = "2026-06-20",
            time = "20:00",
            isLive = true,
            category = "football",
            streams = listOf(
                StreamLink(name = "HD", url = HLS_DEMO_1, quality = VideoQuality.HD),
                StreamLink(name = "SD", url = HLS_DEMO_2, quality = VideoQuality.SD)
            ),
            status = EventStatus.LIVE,
            thumbnailUrl = null
        ),
        Event(
            id = "demo-evt-2",
            title = "NBA Finals Game 7",
            teamA = Team(name = "Lakers",  logoUrl = null),
            teamB = Team(name = "Celtics", logoUrl = null),
            date = "2026-06-20",
            time = "21:30",
            isLive = true,
            category = "basketball",
            streams = listOf(
                StreamLink(name = "HD", url = HLS_DEMO_3, quality = VideoQuality.HD)
            ),
            status = EventStatus.LIVE,
            thumbnailUrl = null
        ),
        Event(
            id = "demo-evt-3",
            title = "Cricket T20 Match",
            teamA = Team(name = "India",     logoUrl = null),
            teamB = Team(name = "Australia", logoUrl = null),
            date = "2026-06-21",
            time = "14:00",
            isLive = false,
            category = "cricket",
            streams = listOf(
                StreamLink(name = "HD", url = HLS_DEMO_4, quality = VideoQuality.HD)
            ),
            status = EventStatus.SCHEDULED,
            thumbnailUrl = null
        ),
        Event(
            id = "demo-evt-4",
            title = "Tennis Grand Slam Final",
            teamA = Team(name = "Djokovic", logoUrl = null),
            teamB = Team(name = "Alcaraz",  logoUrl = null),
            date = "2026-06-22",
            time = "16:00",
            isLive = false,
            category = "tennis",
            streams = listOf(
                StreamLink(name = "HD", url = HLS_DEMO_5, quality = VideoQuality.HD)
            ),
            status = EventStatus.SCHEDULED,
            thumbnailUrl = null
        ),
        Event(
            id = "demo-evt-5",
            title = "Champions League Semi-Final",
            teamA = Team(name = "Real Madrid", logoUrl = null),
            teamB = Team(name = "Bayern",      logoUrl = null),
            date = "2026-06-21",
            time = "19:00",
            isLive = false,
            category = "football",
            streams = listOf(
                StreamLink(name = "HD", url = HLS_DEMO_1, quality = VideoQuality.HD),
                StreamLink(name = "SD", url = HLS_DEMO_2, quality = VideoQuality.SD)
            ),
            status = EventStatus.SCHEDULED,
            thumbnailUrl = null
        ),
        Event(
            id = "demo-evt-6",
            title = "Live: Bundesliga Match",
            teamA = Team(name = "Dortmund",   logoUrl = null),
            teamB = Team(name = "Leverkusen", logoUrl = null),
            date = "2026-06-20",
            time = "18:30",
            isLive = true,
            category = "football",
            streams = listOf(
                StreamLink(name = "HD", url = HLS_DEMO_1, quality = VideoQuality.HD)
            ),
            status = EventStatus.LIVE,
            thumbnailUrl = null
        )
    )

    // ── Sample highlights ──
    fun highlights(): List<Highlight> {
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        return listOf(
            Highlight(
                id = "demo-hl-1",
                title = "Top 10 Goals of the Week",
                thumbnailUrl = "",
                videoUrl = HLS_DEMO_1,
                date = now - day,
                duration = 312,
                views = 124000
            ),
            Highlight(
                id = "demo-hl-2",
                title = "Last-Minute Buzzer Beater",
                thumbnailUrl = "",
                videoUrl = HLS_DEMO_3,
                date = now - 2 * day,
                duration = 48,
                views = 89200
            ),
            Highlight(
                id = "demo-hl-3",
                title = "Cricket Match-Winning Six",
                thumbnailUrl = "",
                videoUrl = HLS_DEMO_4,
                date = now - 3 * day,
                duration = 27,
                views = 215000
            ),
            Highlight(
                id = "demo-hl-4",
                title = "Tennis Ace Compilation",
                thumbnailUrl = "",
                videoUrl = HLS_DEMO_5,
                date = now - 4 * day,
                duration = 184,
                views = 67300
            ),
            Highlight(
                id = "demo-hl-5",
                title = "Champions League Best Saves",
                thumbnailUrl = "",
                videoUrl = HLS_DEMO_6,
                date = now - 5 * day,
                duration = 256,
                views = 178000
            ),
            Highlight(
                id = "demo-hl-6",
                title = "NBA Dunk Contest Highlights",
                thumbnailUrl = "",
                videoUrl = HLS_DEMO_2,
                date = now - 7 * day,
                duration = 142,
                views = 45100
            )
        )
    }

    // ── Sample banner slides (text-only banners since we don't ship demo images) ──
    fun banners(): List<Banner> = listOf(
        Banner(
            id = "demo-banner-1",
            title = "Live Sports Streaming",
            imageUrl = "",
            linkUrl = "",
            sortOrder = 1,
            active = true
        ),
        Banner(
            id = "demo-banner-2",
            title = "24/7 Channels",
            imageUrl = "",
            linkUrl = "",
            sortOrder = 2,
            active = true
        ),
        Banner(
            id = "demo-banner-3",
            title = "On-Demand Highlights",
            imageUrl = "",
            linkUrl = "",
            sortOrder = 3,
            active = true
        )
    )
}
