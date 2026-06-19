# SportStream — Master Build Todo

> One-page build checklist for the entire SportStream project (Sportzfy clone + admin panel + backend).
> Tick items as they're completed, commit, and the post-commit hook auto-pushes to `learngermanbd/sportstream:main`.
> The canonical, full-text prompts for each step live in **`sportzfy_build_plan.html`** under the matching `#phaseN` anchor.

## Status legend

| Mark     | Meaning        |
|----------|----------------|
| `[x]`    | Done           |
| `[-]`    | In progress    |
| `[ ]`    | Not started    |
| `[!]`    | Blocked        |

## How a step works (hand-off loop)

1. Open `sportstream/sportzfy_build_plan.html` and locate the matching Phase section.
2. Open a fresh Codebuff (or downstream agent) chat. Paste the verbatim **Prompt** from the build plan.
3. Always include these two context lines with every prompt:
   - `Done when: <copy the "Done when" line from this file>`
   - `Next agent: <copy the "Next:" line from this file>`
4. Validate the agent's output against **Done when**.
5. Tick `[x]` in this file.
6. Commit: `git add TODO.md && git commit -m "tick: Phase X · Step Y"`
   - Post-commit hook pushes automatically. Verifying once: `git ls-remote origin main`.

## Cross-Phase standing instructions (paste at end of every agent prompt)

```
Always reference `sportstream/TODO.md` for the canonical step you are working on.
After every code change, run `./gradlew assembleDebug` (Basher) and spawn code-reviewer-minimax-m3 in parallel.
When library versions are ambiguous, run Web Researcher / Docs Researcher FIRST to pick a known-good version.
For UI work, attach `sportstream/ui-reference/` (screenshots.zip, ui-demo-video.mp4) so multimodal MiMo 2.5 can match the design.
Premium session budget: 5/season (Steps 2.6, 4.3, 7.12, 8.17). Do NOT use MAX mode for routine steps.
```

---

## Phase 0 — Environment & Setup  ✓ DONE

- [x] Survey dev environment (Java 17 Temurin · Android SDK 35 · build-tools 35.0.0/35.0.1 · Node 24 · npm 11 · Gradle 9.5.1)
- [x] Persist `ANDROID_HOME=C:\Android\android-sdk` via `setx`
- [x] Persist `JAVA_HOME` to Temurin 17 via `setx`
- [x] Accept all Android SDK licenses (7 files under `C:\Android\android-sdk\licenses\`)
- [x] Clone `learngermanbd/sportstream` and configure git identity
- [x] Add `.git/hooks/post-commit` to auto-push `origin main` and verify with empty commit `chore: verify post-commit auto-push hook`
- [ ] *(Optional, deferred)* `firebase-tools` CLI — backend uses `firebase-admin` SDK directly. Re-attempt when npm cache is repaired.

---

## Phase 1 — Project Setup & Foundation  · Est: 2–3 hours · Section: `#phase1`

- [ ] **Step 1.1 — Create Android project structure**
  - Agent: Buff | Mode: DEFAULT | Premium: No
  - Prompt: see `sportzfy_build_plan.html` → `#phase1` → step 1.1
  - Done when: `com.sportstream.app` project scaffolded with package tree `ui/{activities,fragments,adapters}`, `data/{models,viewmodels,repository,local,remote}`, `utils`, `services`
  - Next: spawn Step 1.2 (Buff). Tell it: "Project skeleton is at `<repo>`. Add every dep from §1.2 to `app/build.gradle.kts` and enable ViewBinding + google-services."

- [ ] **Step 1.2 — Add all dependencies**
  - Agent: Buff | Mode: DEFAULT | Premium: No
  - Prompt: `#phase1` → step 1.2
  - Done when: `app/build.gradle.kts` includes Media3 (player+ui+session+datasource-okhttp), OkHttp+logging, Room (runtime+ktx+kapt), Firebase BoM → `firebase-messaging` only, Sentry, Lottie, Material 3, navigation-fragment-ktx + navigation-ui-ktx, lifecycle viewmodel-ktx + livedata-ktx, datastore-preferences, Glide 4.16, kotlinx-coroutines-android
  - Next: spawn Step 1.3 (Basher). Ask to run `./gradlew build --dry-run`.

- [ ] **Step 1.3 — Sync & verify build**
  - Agent: Basher | Mode: DEFAULT | Premium: No
  - Done when: `./gradlew build --dry-run` exits 0 and prints resolved task graph.
  - Next: spawn Step 1.4 (Buff).

- [ ] **Step 1.4 — FCM + Sentry + API config setup**
  - Agent: Buff | Mode: DEFAULT | Premium: No
  - Prompt: `#phase1` → step 1.4
  - Done when: `SportStreamApp.kt` initializes `FirebaseMessaging` + `Sentry`; placeholder `google-services.json` added; `RemoteConfigHelper.kt` fetches `GET /api/config`; `AndroidManifest.xml` registers the Application class and permissions `INTERNET / ACCESS_NETWORK_STATE / POST_NOTIFICATIONS / WAKE_LOCK`
  - Next: spawn Step 1.5 (Buff).

- [ ] **Step 1.5 — Design system & theme**
  - Agent: Buff | Mode: DEFAULT | Premium: No
  - Prompt: `#phase1` → step 1.5
  - Done when: `colors.xml` (primary `#1CCBD4`, bg `#0A0F1A`, card `#121A27`), `themes.xml` (Material 3 dark, NoActionBar), `styles.xml` (glass card, bottom nav, player overlay), `bg_glass_card.xml`, `bg_splash.xml`, `bg_player_controls.xml`, Poppins font
  - Next: spawn Step 1.6 (Code Reviewer) + run `./gradlew assembleDebug` (Basher) in parallel.

- [ ] **Step 1.6 — Review foundation**
  - Agent: Code Reviewer | Mode: DEFAULT | Premium: No
  - Done when: reviewer returns clean feedback OR all flagged issues fixed.
  - Next: Phase 1 complete. Move to Phase 2 step 2.1.

---

## Phase 2 — Core Architecture & Data Layer  · Est: 3–4 hours · Section: `#phase2`

- [ ] **Step 2.1 — Create data models** (Buff) — Prompt §2.1 · Done when: `Event`, `Team`, `Channel`, `Highlight`, `Category`, `StreamLink`, `Playlist` (Room @Entity), `Banner`, `ApiResponse` all defined with `@SerializedName`. Next: tell Step 2.2 "Models at `data/models/` — build OkHttp `ApiClient` + `ApiService` + `RemoteDataSource`."
- [ ] **Step 2.2 — Build API service** (Buff) · Done when: `ApiClient` (30 s timeouts, logging only in debug, OkHttp cache), `ApiService` interface with suspend `getAppData/getEvents/getChannels/getHighlights`, `RemoteDataSource` returning `Result<T>`. Next: Step 2.3.
- [ ] **Step 2.3 — Room database setup** (Buff) · Done when: `AppDatabase` (v1), `FavoriteDao`, `PlaylistDao`, `Converters` (`List<String>` ↔ JSON), `LocalDataSource`. Next: Step 2.4.
- [ ] **Step 2.4 — Repository layer** (Buff) · Done when: `MainRepository`, `FavoritesRepository`, `PlaylistRepository` (Dispatchers.IO, error-safe). Next: Step 2.5.
- [ ] **Step 2.5 — ViewModels** (Buff) · Done when: `MainViewModel`, `HomeViewModel`, `CategoriesViewModel`, `HighlightsViewModel`, `PlayerViewModel`, sealed `UiState<T>`. Next: Step 2.6 (PREMIUM).
- [ ] **Step 2.6 — Architecture review (PREMIUM Thinker)** ⚑ MAX
  - Agent: Thinker (DeepSeek V4 Pro / MiMo 2.5 Pro) | Mode: **MAX** | Premium: ⚑ **Yes**
  - Prompt: §2.6 — review data flow API→Repo→VM→UI, coroutine scopes, Room TypeConverters, StateFlow patterns; apply anti-pattern fixes.
  - Done when: clean review + all fixes applied.
  - Next: Step 2.7.
- [ ] **Step 2.7 — Build verification** (Basher) · Done when: `./gradlew assembleDebug` exits 0. Next: Phase 3.

> **Premium budget used so far:** ☐ Step 2.6

---

## Phase 3 — UI Screens (Home, Categories, Highlights)  · Est: 4–5 hours · Section: `#phase3`

- [ ] **Step 3.0 — UI reference assets pre-flight**
  - Agent: any | Mode: DEFAULT | Premium: No
  - Done when: ✅ `sportstream/ui-reference/screenshots.zip` extracted locally to **<code>sportstream/ui-reference/screenshots/</code>** (17 JPGs, 11 MB — gitignored, not tracked) AND ✅ `sportstream/ui-reference/ui-demo-video.mp4` (6 MB, ISO MP4) confirmed present, so subsequent Buff / MiMo 2.5 multimodal prompts can attach individual JPGs by absolute path (example: `sportstream/ui-reference/screenshots/Screenshot_2026-06-18-00-14-49-053_app.blaze.spofficial.jpg`).
  - Next: Step 3.1.

- [ ] **Step 3.1 — Splash screen** (Buff) · §3.1 · Done when: `activity_splash.xml` + `SplashActivity.kt` with ConstraintLayout, glass logo card (180 dp, rounded 28 dp), Lottie loading bar, network error card, version text; navigate to Main after 2 s. Next: Step 3.2.
- [ ] **Step 3.2 — Main activity & navigation** (Buff) · §3.2 · Done when: DrawerLayout + BottomNav (Home/Categories/Highlights) + `nav_graph.xml` + drawer menu (Network Stream, Playlists, Floating Player, Video Quality, Notice, Join Us, Share, Update, Exit) + Toolbar with search. Next: Step 3.3.
- [ ] **Step 3.3 — Home fragment** (Buff) · §3.3 · Done when: SwipeRefresh + auto-scroll banners + LIVE Events list with VS team logo layout + animated LIVE badge + EventAdapter with DiffUtil. Next: Step 3.4.
- [ ] **Step 3.4 — Categories fragment** (Buff) · §3.4 · Done when: ChipGroup category filters + GridLayoutManager(3 cols) channel cards + CategoryAdapter. Next: Step 3.5.
- [ ] **Step 3.5 — Highlights fragment** (Buff) · §3.5 · Done when: 16:9 thumbnail cards with play overlay + duration + HighlightAdapter. Next: Step 3.6 + Step 3.7 in parallel.
- [ ] **Step 3.6 — UI review** (Code Reviewer) · DiffUtil, ViewBinding, Glide placeholders, click listeners, navigation, SwipeRefresh.
- [ ] **Step 3.7 — Visual verification** (Browser) · emulator screenshots of Splash, Home, Categories, Highlights, Drawer, BottomNav. Next: Phase 4.

---

## Phase 4 — Video Player  · Est: 4–5 hours · Section: `#phase4`

- [ ] **Step 4.1 — Research Media3 APIs** (Web Researcher) · §4.1 · player controller, PiP, subtitles (SRT/VTT/ASS), HLS/DASH, foreground service. Next: Step 4.2.
- [ ] **Step 4.2 — Player activity & layout** (Buff) · §4.2 · top bar (back, title marquee, lock, PiP, resize, settings) + links bar + center rewind/play/forward + bottom seek bar + volume + fullscreen. Next: Step 4.3 (PREMIUM).
- [ ] **Step 4.3 — Swipe gestures (PREMIUM Thinker)** ⚑ MAX
  - Agent: Thinker (MAX). Prompt §4.3 — horizontal seek, left brightness, right volume, single-tap toggle, double-tap skip; visual feedback overlays. Apply all fixes.
  - Done when: gestures behave cleanly, no gesture conflicts.
  - Next: Step 4.4.
- [ ] **Step 4.4 — Screen lock & PiP** (Buff) · §4.4 · Next: Step 4.5.
- [ ] **Step 4.5 — Subtitle & quality selection** (Buff) · §4.5 · Next: Step 4.6.
- [ ] **Step 4.6 — Floating player service** (Buff) · §4.6 · FOREGROUND_SERVICE_MEDIA_PLAYBACK on API 34+, SYSTEM_ALERT_WINDOW, draggable overlay. Next: Step 4.7 + Basher `./gradlew assembleDebug`.
- [ ] **Step 4.7 — Player review** (Code Reviewer) · ExoPlayer lifecycle, PiP API 26+, no memory leaks, subtitle render, foreground notification.

> **Premium budget used so far:** ☐ Step 2.6 · ☐ Step 4.3

---

## Phase 5 — Extra Features  · Est: 3–4 hours · Section: `#phase5`

- [ ] **Step 5.1 — Favorites system** (Buff) · §5.1 · swipe-to-delete + undo, heart toggle animation.
- [ ] **Step 5.2 — Playlist management** (Buff) · §5.2 · CRUD + FAB + add-to-playlist from player.
- [ ] **Step 5.3 — Network stream player** (Buff) · §5.3 · URL dialog + format selector + recent URLs in DataStore.
- [ ] **Step 5.4 — Push notifications** (Buff) · §5.4 · MessagingService + NotificationHelper API 33+ POST_NOTIFICATIONS + RemoteConfigHelper fetches `/api/config`.
- [ ] **Step 5.5 — Drawer menu actions** (Buff) · §5.5 · all 9 menu items wired.
- [ ] **Step 5.6 — Search functionality** (Buff) · §5.6 · debounced Flow 300 ms, recent searches in DataStore.
- [ ] **Step 5.7 — Feature review** (Code Reviewer) · all CRUD + search + notifications.

---

## Phase 6 — Polish, Ads & Launch  · Est: 3–4 hours · Section: `#phase6`

- [ ] **Step 6.1 — Ad integration** (Buff) · §6.1 · AdMob banner + interstitial (≤ 1 / 5 min), GDPR consent, test IDs.
- [ ] **Step 6.2 — Auto-update system** (Buff) · §6.2 · force + optional update, DownloadManager, FileProvider, REQUEST_INSTALL_PACKAGES on API 26+.
- [ ] **Step 6.3 — Animations & transitions** (Buff) · §6.3 · splash, shared element, staggered list, PiP/Lock transitions.
- [ ] **Step 6.4 — Error handling & edge cases** (Buff) · §6.4 · network, player, global crash handler.
- [ ] **Step 6.5 — ProGuard & release build** (Basher) · §6.5 · `./gradlew assembleRelease`. Confirm APK ≤ 30 MB.

---

## Phase 7 — Security & Protection  · Est: 4–5 hours · Section: `#phase7`

20-layer defense model. Three **CRITICAL** layers + final review go through **Thinker (PREMIUM)**.

- [ ] **Step 7.1 — Research Android security** (Web Researcher) · R8, Keystore, EncryptedSharedPreferences, Network Security Config, Play Integrity, NDK, RootBeer, Frida/Xposed.
- [ ] **Step 7.2 — String encryption (CRITICAL)** (Buff) · §7.2 · AES-256-GCM via Android Keystore, build-time encryptor, Gradle task.
- [ ] **Step 7.3 — ProGuard/R8 obfuscation (CRITICAL)** (Buff) · §7.3 · R8 full mode, CJK rename dictionary, control-flow obfuscation.
- [ ] **Step 7.4 — Native library protection (NDK)** (Buff) · §7.4 · C/C++ `libsportstream_security.so` with `nativeDecrypt`, `nativeVerifySignature`, `nativeCheckRoot`, etc. *Build on Linux/macOS if Windows NDK unavailable.*
- [ ] **Step 7.5 — APK integrity & anti-tampering (CRITICAL)** (Buff) · §7.5 · signing cert hash, file SHA-256 compare, repackage detection, gradual degradation.
- [ ] **Step 7.6 — Root & emulator detection** (Buff) · §7.6 · RootBeer + manual checks, Frida/Xposed/Substrate detection in `/proc/self/maps`.
- [ ] **Step 7.7 — SSL pinning & network security (CRITICAL)** (Buff) · §7.7 · `res/xml/network_security_config.xml` + `CertificatePinner` + request signing.
- [ ] **Step 7.8 — Secure key storage & data protection** (Buff) · §7.8 · Keystore + EncryptedSharedPreferences + SQLCipher + char[] secrets + FLAG_SECURE.
- [ ] **Step 7.9 — Anti-debugging & runtime protection** (Buff) · §7.9 · `Debug.isDebuggerConnected`, TracerPid, ptrace self-trace, honey pots.
- [ ] **Step 7.10 — Play Integrity API** (Buff) · §7.10 · `com.google.android.play:integrity`, server-side token verification.
- [ ] **Step 7.11 — WebView & deep link hardening** (Buff) · §7.11 · disable JS where unused, intent:// blocks, deep-link allowlist.
- [ ] **Step 7.12 — Security architecture review (PREMIUM Thinker, CRITICAL)** ⚑ MAX
  - Agent: Thinker (MAX). Prompt §7.12 — STRIDE threat model across all 12 attack surfaces; verify full security checklist. Apply all fixes.
  - Done when: STRIDE report + checklist all green.
  - Next: Step 7.13.
- [ ] **Step 7.13 — Security build & verify** (Basher) · §7.13 · `./gradlew clean assembleRelease`, decompile with jadx, verify obfuscation, test on rooted + emulator.

> **Premium budget used so far:** ☐ Step 2.6 · ☐ Step 4.3 · ☐ Step 7.12

---

## Phase 8 — Admin Panel (Web + Android Admin + Backend)  · Est: 8–10 hours · Section: `#phase8`

- [ ] **Step 8.1 — Research admin panel tech stack** (Web Researcher) · §8.1 — compare standalone HTML vs Next.js vs Ktor.
- [ ] **Step 8.2 — Backend API: database & models** (Buff) · §8.2 · Prisma + Supabase Postgres: Admin, Event, Channel, Highlight, Category, Banner, StreamLink, AppConfig, Notification, AnalyticsEvent + seed.
- [ ] **Step 8.3 — Backend API: auth + routes** (Buff) · §8.3 · bcrypt + JWT (15 min access / 7 day refresh) + middleware (helmet, cors, rateLimit, rbac, Zod, multer).
- [ ] **Step 8.4 — Web admin: login + layout** (Buff) · §8.4 · login page + dark-theme sidebar + top bar + nav, `admin/auth.js` + `admin/api.js`.
- [ ] **Step 8.5 — Web admin: dashboard page** (Buff) · §8.5 · 4 stat cards + Chart.js line + doughnut + bar.
- [ ] **Step 8.6 — Web admin: events manager** (Buff) · §8.6 · data table + filters + pagination + bulk select + event form (stream links + team logos).
- [ ] **Step 8.7 — Web admin: channels + highlights** (Buff) · §8.7 · grid/list + form + bulk ops + drag reorder.
- [ ] **Step 8.8 — Web admin: notifications + config** (Buff) · §8.8 · composer + targeting + history + templates + AppConfig editor + admin users CRUD.
- [ ] **Step 8.9 — Web admin: analytics** (Buff) · §8.9 · date-range + line/area/bar/pie/heatmap + CSV export.
- [ ] **Step 8.10 — Mobile app: admin API integration** (Buff) · §8.10 · ApiService → admin endpoints + analytics POST batching.
- [ ] **Step 8.11 — Admin panel review** (Code Reviewer) · auth, RBAC, file validation, CORS, responsive frontend, API contract.
- [ ] **Step 8.12 — Admin panel build & deploy** (Basher) · npm build + Prisma migrate + deploy docs.
- [ ] **Step 8.13 — Separate Android admin app: project + login** (Buff) · §8.13 · new project `com.sportstream.admin`, Compose + Material 3, JWT in EncryptedSharedPrefs, biometric (API 28+).
- [ ] **Step 8.14 — Admin app: events + channels screens** (Buff) · §8.14 · Filter chips, FAB, swipe, multi-select grid for channels, form for events with stream links.
- [ ] **Step 8.15 — Admin app: config + notifications + analytics** (Buff) · §8.15 · config sections, FCM composer with scheduling, MPAndroidChart analytics.
- [ ] **Step 8.16 — Android 6 → 16/17 compat layer for both apps** (Buff) · §8.16 · VersionCompatHelper, PermissionManager (API 23-35), NotificationCompatHelper, UI compat.
- [ ] **Step 8.17 — Admin system review all 3 platforms (PREMIUM)** ⚑ MAX
  - Agent: Thinker (MAX). Verify web + Android admin + REST API consistency, RBAC, cross-platform sync.
  - Done when: review clean + all fixes applied.
  - Next: Step 8.18.
- [ ] **Step 8.18 — FCM notification targeting & scheduling** (Buff) · §8.18 · topic-based + token-based targeting + `node-cron` scheduler + delivery logging.

> **Premium budget used so far:** ☐ Step 2.6 · ☐ Step 4.3 · ☐ Step 7.12 · ☐ Step 8.17

---

## Phase 9 — Testing & QA  · Est: 3–4 hours · Section: `#phase9`

- [ ] **Step 9.1 — Unit tests** (Buff) · §9.1 · Repository, ViewModel, utility tests. `./gradlew test`.
- [ ] **Step 9.2 — UI testing on emulator** (Browser) · §9.2 · full user journey + rotation + back/foreground + offline.
- [ ] **Step 9.3 — Security penetration testing** (Browser) · §9.3 · obfuscation verify, SSL pinning, root + emulator.
- [ ] **Step 9.4 — Final build & APK** (Basher) · §9.4 · `./gradlew clean assembleRelease`, lint, signed AAB.

---

## Premium session budget (5/season — confirm before using MAX mode)

| Step | Why                            | Reserved? |
|------|--------------------------------|-----------|
| 2.6  | Architecture review            | ☐ |
| 4.3  | Swipe gesture logic            | ☐ |
| 7.12 | STRIDE security audit (CRITICAL) | ☐ |
| 8.17 | Cross-platform admin review    | ☐ |
| 5/season | Free                       | ☐ |

If 5 are used, fall back to Mini Max M3 (Buff) + Web/Docs researchers — most steps still complete.

---

## App identity cheat-sheet

- **com.sportstream.app** — User Android app (cyan `#1CCBD4`, dark glassmorphism, XML + ViewBinding, API 23 → 35)
- **com.sportstream.admin** — Admin Android app (orange `#F59E0B`, Jetpack Compose, private APK)
- **Backend** — Node.js + Express + Prisma + Supabase Postgres (free) → Railway `$5/mo` for prod
- **Single Firebase service** retained: FCM via `firebase-admin` SDK (NOT Cloud Messaging consumer)
- **Distribution**: APK sideload (no Play Store initially)
- **Min SDK**: 23 · **Target SDK**: 35 · **Branch**: `main` · **Remote**: `learngermanbd/sportstream`
- **API replaces Firebase Remote Config**: `GET /api/config` returns `{apiBaseUrl, updateUrl, telegramLink, noticeText, maintenanceMode, minAppVersion, featureFlags}`
- **App-side ingest**: every request carries `X-App-Version`; backend middleware `versionCheck` returns HTTP **426** if version < `minAppVersion`, blocking the entire app until the user updates.

---

## Self-check at every checkpoint

```bash
cd sportstream
./gradlew assembleDebug 2>&1 | tail -20    # Basher
git ls-remote origin main                  # confirm push went through
grep -E "^\s*-\s*\[ \]" TODO.md | head -5  # next 5 unchecked steps
```

If a step is `[!]` (blocked), write the blocker inline above the step with the date and the agent that hit it.
