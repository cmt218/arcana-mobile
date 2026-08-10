# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

**Android:**
```bash
./gradlew :androidApp:assembleDebug
```

**iOS:** Open `/iosApp/iosApp.xcodeproj` in Xcode and run from there. The Kotlin framework is compiled as a static framework and linked by Xcode.

**Tests:**
```bash
./gradlew :sharedLogic:testDebugUnitTest       # the main suite (logic + ViewModels + session)
./gradlew :sharedUI:testDebugUnitTest   # the few UI-coupled tests (DotMatrixLoader, LateCancelWindow, OpensAtLabel)
```
(The aggregate `test` tasks do NOT accept `--tests`; filter on the `testDebugUnitTest` variants.)

**ALWAYS compile BOTH targets when touching shared code** — Android alone won't catch Kotlin/Native errors:
```bash
./gradlew :sharedLogic:compileDebugKotlinAndroid :sharedUI:compileDebugKotlinAndroid       # JVM/Android
./gradlew :sharedLogic:compileKotlinIosSimulatorArm64 :sharedUI:compileKotlinIosSimulatorArm64  # Kotlin/Native (iOS)
```
JVM-only APIs compile on Android but break the iOS build — notably `String.format` / `"%02d".format(x)` / `java.*` / `Locale`. Use string templates, `padStart`, and `kotlinx-datetime` instead. (`compileKotlinMetadata` is a no-op SKIP in this project — don't rely on it.)

No linter is configured (no ktlint/detekt).

**Clearing the iOS Simulator Keychain** (useful when testing auth flows): Simulator app → Device → Erase All Content and Settings. Uninstalling the app alone does not clear Keychain items.

## Architecture

This is a **Kotlin Compose Multiplatform** project targeting Android and iOS, split into three Gradle modules (2026-08-10, matching JetBrains' sharedLogic/sharedUI default naming exactly and AGP 9's app-plugin-out-of-KMP-modules requirement):

- **`:sharedLogic`** — the Compose-free Kotlin core: `networking/`, `data/` DTOs, `auth/` (token storage + diagnostics), `analytics/` (Telemetry facade + taxonomy), `di/AppModule.kt`, `navigation/` (destinations + DeepLinkHandler), `session/AppSessionController.kt`, ALL ViewModels, and the pure display-logic helpers (`ScheduleDisplayLogic`, `ClassDetailLogic`, `SpotLayout`, `ScheduleFilter`). Platform actuals live in its `androidMain`/`iosMain` (SecureStorage, PendingTokenSource, Platform; Android ones read the app context from `SharedAndroidContext`, set by `ArcanaApplication`). `commonTest/` here is the main test suite. **No Compose dependency may be added to this module** — it is what survives any UI-framework change.
- **`:sharedUI`** — all Compose UI (screens, `ui/` design system, `theme/`), plus the platform entry points: `MainActivity`/`ArcanaApplication` + Android telemetry impls (androidMain), `MainViewController` + the iOS framework (iosMain). An `androidLibrary` — it is NOT the installable app. The iOS framework `export(project(":sharedLogic"))`s so Swift sees real types; new logic in :sharedLogic that Swift must call needs no extra wiring (api + export cover it).
- **`:androidApp`** — the installable Android application shell: applicationId, versionCode/versionName, release signing, minimal manifest. No code. The manifest application element (activities, deep links, icons) merges in from :sharedUI's androidMain manifest.

New feature code: logic + ViewModel in `:sharedLogic`, screen in `:sharedUI`.

**Known follow-up (AGP 10 horizon):** KGP deprecates `kotlinMultiplatform` + `com.android.library` on AGP 9 (warning-only today) in favor of the single-variant `com.android.kotlin.multiplatform.library` plugin. Migrating will require redesigning three build-type-dependent pieces: the `sharedUI/src/debug/` networkSecurityConfig manifest overlay, `BuildConfig.DEBUG` gating `flushAt=1` in AndroidTelemetry, and the library BuildConfig analytics fields (candidates: move to `:androidApp`, or runtime FLAG_DEBUGGABLE checks). Do this deliberately when AGP forces it, not before.

**Platform abstraction pattern:** `Platform.kt` (:sharedLogic commonMain) declares a plain `interface Platform` plus top-level `expect` declarations (`getPlatform()`, `defaultBaseUrl()`, `logWarning()`, `logDebug()`, `isDebugBuild`, `appVersionName()`); the actuals live in :sharedLogic's `androidMain`/`iosMain` (`Platform.android.kt` / `Platform.ios.kt`). Follow this pattern for any platform-divergent behavior. `defaultBaseUrl()` is an existing example — it currently returns `https://api.arcana.fit` on **both** platforms (the prod cutover is already done; it is NOT a localhost default). To run against a local server, override the base URL in Developer Settings (see "Temporary debug treatment").

**Dependency injection:** Koin 4.x. `di/AppModule.kt` in :sharedLogic's commonMain defines all bindings. Koin is started at the platform entry point — `ArcanaApplication.onCreate()` on Android, `MainViewController()` on iOS — before any Compose code runs. Use `koinInject()` for values and `koinViewModel()` for ViewModels in composables.

**Auth architecture:**
- `ArcanaApiClient` owns a `StateFlow<Boolean>` (`isAuthenticated`) that is the single source of truth for session state. It is initialized from `TokenStorage.isLoggedIn` at startup and updated by `login()`, `completeSignup()`, `logout()`/`forceLogout(cause)`, and token refresh failure.
- `App.kt` collects `isAuthenticated` (via the Koin-injected `AppSessionController`, whose flow is ArcanaApiClient's) and gates the entire main scaffold behind it; all session rules (welcome-token machine, survey gate, first-launch recovery, teardown) live in :sharedLogic `session/AppSessionController.kt` — App.kt only collects state and forwards events. A session-scoped `ViewModelStore` (provided via `LocalViewModelStoreOwner`) wraps `MainScaffold` so all authenticated ViewModels are destroyed on logout and recreated fresh on next login.
- `auth/SecureStorage` is an `expect/actual` class: `EncryptedSharedPreferences` on Android, iOS Keychain via CoreFoundation/Security on iOS. `TokenStorage` wraps it with typed `accessToken`/`refreshToken` properties.
- The Ktor `Auth` plugin handles Bearer header injection and 401/token-refresh automatically for all authenticated requests. `sendWithoutRequest` excludes the login, register, and refresh endpoints from receiving auth headers.

**Networking:** Ktor 3.x with `kotlinx-serialization`. `networking/ArcanaApiClient.kt` contains the `HttpClient` and all endpoint methods. Data classes live in `data/` and are annotated with `@Serializable`. ViewModels live in feature packages (e.g. `classes/ClassesViewModel.kt`) and expose `StateFlow<UiState>` sealed interfaces with `Loading`, `Success`, and `Error` states.

**Date/time:** `kotlinx-datetime` in commonMain. Use `Clock.System.todayIn(TimeZone.currentSystemDefault())` for today, `Clock.System.now().toLocalDateTime(tz)` for current local time. `DayOfWeek` and `Month` are enums with `.name` returning uppercase strings (take(3) for the design's three-letter abbreviations).

**Android dev networking:** The debug source set (`sharedUI/src/debug/`) contains a `network_security_config.xml` that permits cleartext to `localhost` and `10.0.2.2`. This is debug-only and cannot ship in release builds.

**Android release fields** (`versionCode`/`versionName`, signing) live in `androidApp/build.gradle.kts`. Release bundle: `./gradlew :androidApp:bundleRelease` → `androidApp/build/outputs/bundle/release/androidApp-release.aab`. `keystore.properties` is honored from `androidApp/` (canonical) or `sharedUI/` (pre-split location); `analytics.properties` stays at `sharedUI/analytics.properties`.

**iOS entry point flow:** `iosApp/ContentView.swift` → `MainViewControllerKt.MainViewController()` (Kotlin) → Compose UI.

**Key versions (see `gradle/libs.versions.toml`):**
- Kotlin: 2.3.10
- Compose Multiplatform: 1.11.1 (concurrent/parallel rendering is ON by default on iOS since 1.11.0)
- Koin: 4.2.0
- Ktor: 3.1.2
- kotlinx-datetime: 0.7.1
- Navigation Compose: 2.9.2 (JetBrains CMP port — pinned to a version matched to the CMP release in the CMP CHANGELOG; do not bump independently of `composeMultiplatform`)
- Lifecycle (`org.jetbrains.androidx.lifecycle`): 2.11.0 — like nav-compose, bump in lockstep with the CMP release notes' listed version (prefer the stable if one exists at that minor).
- Material3 (`org.jetbrains.compose.material3`): 1.11.0-alpha07 — the multiplatform material3 port publishes **alpha-only** since it decoupled from CMP releases (no stable exists past 1.8.x); always use the exact version listed in the CMP release notes. We have shipped alpha material3 from day one; this is normal, not a red flag.
- Android min/target SDK: 24/36 (compileSdk 37 — required by lifecycle 2.11.0's Jetpack delegate; AGP 9.1 warns it was tested up to 36.1, warning-only)
- Package: `org.arcana.mobile`

**Planned upgrade — CMP 1.12.0 when stable (~Sept–Oct 2026):** it ships the iOS lazy-list prefetch scheduler (compose-multiplatform-core PR #3149), the expected fix for remaining schedule scroll hitches. Before/after measurement is repeatable via the runbook in `docs/perf/README.md` (scripted simulator scroll A/B; 1.10.0→1.11.1 already measured −40% dropped frames there).

## Design system

The brand-aligned theming lives in :sharedUI at `sharedUI/src/commonMain/kotlin/org/arcana/mobile/theme/` and the reusable UI primitives in `.../ui/`. (Note :sharedLogic also has an `org.arcana.mobile.ui` package for the pure `studioLocationLabel` helper.) **Screens should never hand-roll `TextStyle`s, hex colors, or raw icon paths** — compose them from these primitives. The brand color hexes and the typography hierarchy are sourced from the brand identity doc and the typography doc respectively (see the parent `arcana/CLAUDE.md` for live links).

**`theme/AppColors.kt`** — five primaries are the brand doc's source of truth: `Lime #B6C24F`, `Moss #283B15`, `Stone #F5F2ED`, `Wood #2E1B0F`, `BurntNectar #F65713`. Derived variants (`LimeBright/Deep`, `MossDeep/Light`, `Stone2`, `Paper`) are HSL-style shifts of those primaries — recompute them, don't hand-edit, if a primary changes. `Ink/Graphite/Charcoal/Ash/Ash2/Mist/Mist2` are the warm neutrals. `StoneAlpha*` are translucent helpers for dark surfaces.

**`theme/Typography.kt`** — three font families, sourced from `composeResources/font/`:
- `LeagueSpartan` — display headlines, H2, CTAs (ALL CAPS)
- `DmSans` — body, overlines, captions, nav
- `CormorantGaramond` (italic) — sparing emotional accents, one line per screen max

**Do not add a monospace family.** The typography doc has exactly three; there is no `JetBrains Mono` / `MonoText`. For metadata stamps use `Overline` (DM Sans Bold caps with breathable tracking); for sentence-case microcopy use `Caption` (DM Sans Medium).

**`theme/ArcanaTheme.kt`** — single root wrapper. Resolves the font families once and provides them via `Arcana.fonts.{display,body,accent}`. Also installs the Material3 `lightColorScheme` keyed to the Stone palette and sets `LocalContentColor` to `Ink`. Wrap the app once in `App.kt`; downstream composables consume from `Arcana.fonts.*`.

**`theme/WordmarkLogo.kt`** — the dot-matrix "arcana" wordmark. The PNG (`composeResources/drawable/wordmark.png`) is cropped tight to the text bbox (aspect ratio `4.88:1`) so it renders flush left/right in any layout — no offsets or alignment hacks needed. Pass `tint = Moss` (or any color) to recolor every dot via `ColorFilter.tint`; leave null to use the asset's native stone + lime dots (the splash does this).

## Shared UI library — `ui/` package

The `ui/` package is the mobile design system. New screens compose from these; new shared affordances belong here.

- **`Text.kt`** — `Display`, `Heading2`, `Heading3`, `Overline`, `BodyText`, `AccentText`, `Caption`. Each takes a `size: Int` (sp) and `color: Color`. `Display`, `Heading2`, and `Overline` uppercase their input automatically.
- **`Icons.kt`** — `ArcanaIcons` exposes `DrawableResource` handles to the icon set in `composeResources/drawable/icon_*.xml`. `StrokeIcon(icon, size, tint)` renders one via Material3's `Icon` with `ColorFilter` tinting. **To add a new icon:** drop a 24×24 `<vector>` XML in `composeResources/drawable/` (stroke baked at 1.8, `fillColor="#00000000"`, `strokeLineCap/Join="round"`) and add a one-liner to `ArcanaIcons`. Works on Android and iOS unchanged.
- **`Dots.kt`** — `Pulse`, `DottedDivider`, `SectionRule`, `DotField`. The dot is the brand's repeating gesture; reach for these instead of inventing bespoke decoration.
- **`Buttons.kt`** — `PrimaryCta` (Moss pill with Lime arrow well), `TextLink` (underlined display-type link with trailing icon), `IconCircle` (the recurring round affordance — pass `background` for filled or `borderColor` for outlined).
- **`Inputs.kt`** — `ArcanaTextField` (hairline-underline input; Mist → Moss on focus). Built on `BasicTextField`, not Material's filled/outlined; resist the urge to swap.
- **`TabBar.kt`** — the bottom nav. `ArcanaTab` enum is the source of truth for the three primary destinations (`Home`, `Schedule`, `Profile`); the Profile tab renders as the member's avatar. `ArcanaTabBar` handles its own `safeBottomBarPadding()` internally — callers in `App.kt` do not pass a safe-area modifier.
- **`Insets.kt`** — `Modifier.safeContentPadding()` (top + horizontal), `Modifier.safeBottomBarPadding()` (bottom + horizontal), `Modifier.safeHorizontalPadding()` (horizontal only). **Always prefer these to `statusBarsPadding()` / `navigationBarsPadding()`** so display cutouts (camera punch-outs in landscape) are respected. They wrap `WindowInsets.safeDrawing.only(...)` and work the same on Android and iOS.

## Surface conventions

The app is **Stone-primary (light)** with two intentional dark counterweights: the splash sits on **MossDeep `#1F2D10`** (the only screen that does), and the Profile hero sits on **Ink**. Burnt Nectar is reserved as a sparing accent — do not introduce it as a primary surface.

**Splash** (`SplashScreen.kt` + `ui/DancingWordmark.kt`): full-screen Compose Canvas rendering a grid of stone-colored dots that flicker, settle into the Arcana wordmark from `ui/WordmarkGridData.kt` (embedded const, no I/O), then breath-pulse. Sits on MossDeep with a radial vignette fading to Ink at the corners. Timing is derived from `DANCE_DURATION_MS` + `DANCE_SETTLE_STAGGER_MS`; the splash min-display constant in `SplashScreen.kt` adds a 200 ms tail so the breath pulse lands before the 300 ms exit fade in `App.kt`. The canonical reference grid JSON is in `composeResources/files/wordmark-grid.json`; if it ever changes, regenerate the const in `WordmarkGridData.kt` from the file.

**Full-bleed dark hero pattern** — when a screen needs a dark hero at the top with a Stone body below (currently Profile), use this structure:

```kotlin
Box(modifier = Modifier.fillMaxSize().background(Stone)) {
    // Ink strip behind the hero — covers ~55% so iOS top-overscroll reads as ink.
    Box(Modifier.fillMaxWidth().fillMaxHeight(0.55f).background(Ink))
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { Hero() }            // its own Ink background, sits over the strip
        // post-hero items wear Stone backgrounds (see Profile's `StoneWrap` helper)
    }
}
```

This avoids the iOS overscroll-flash trap (transparent LazyColumn lets the Ink strip show through during top overscroll without leaving Ink visible below the last item). `ArcanaTabBar` separately fills its full slot with Stone, so nothing behind the Scaffold can bleed through under the visible tab row.

## Spacing & sizing

Pad in **4dp increments** (`4 / 8 / 12 / 16 / 20 / 24 / 28 / 32 / 40 / 48`). Text sizes in **2sp increments** (`10 / 12 / 14 / 16 / 18 / 20 / 22 / 26 / 28 / 36 / 44 / 52 / 56`). The few intentional exceptions — `1.dp` hairlines, the `116.dp` avatar diameter — should stay rare and obvious.

## Navigation

`App.kt` hosts a `NavHost` keyed off `navigation/ArcanaDestinations.kt` — a sealed `ArcanaDestination` with `@Serializable` data objects per destination (no string routes). The bottom bar's active tab is derived from `currentBackStackEntryAsState` via `hasRoute<T>()`; it hides on every non-tab destination (StudioSelection, MyBookings, EditProfile, ConciergeRequest, ClassDetail) so a stray tab tap mid-flow can't silently pop the in-progress entry off the stack.

Tab navigation uses the standard `popUpTo(start) { saveState = true }` / `launchSingleTop` / `restoreState` block so each tab keeps its own back stack + scroll position.

Transitions are pinned at the `NavHost` level (150ms fade, all four slots) for cross-platform consistency — iOS's default slide reads wrong between sibling tabs. Override per-`composable` if a destination needs its own transition.

**Dependency pinning.** `org.jetbrains.androidx.navigation:navigation-compose` is the JetBrains CMP port, not Jetpack Navigation. Its version must match the `composeMultiplatform` release — find the matched version in the CMP CHANGELOG, not in Jetpack/Android docs. Bumping CMP almost always means bumping nav-compose; bumping nav-compose alone risks pulling a conflicting Compose runtime transitive.

## Temporary debug treatment

These are pre-launch dev affordances that must be removed (or hardened) before public release. Each is tagged inline with a comment pointing back here for findability.

**1. Runtime API base URL override + Developer Settings screen.**
- Files (straddling the split): `networking/BaseUrlProvider.kt` + `settings/DeveloperSettingsViewModel.kt` in :sharedLogic; `settings/DeveloperSettingsScreen.kt` + the hidden entry gesture in `auth/AuthScreen.kt` (10 taps on the wordmark) in :sharedUI.
- Why it exists: pre-launch we run the server on Cole's Mac and expose it to physical devices via a Cloudflare *quick* tunnel. Quick-tunnel URLs change on every `cloudflared` restart, so rebuilding the app each time would be miserable. The override is editable at runtime via the Developer Settings overlay (reachable from the auth screen footer — so testers locked out of login because the default doesn't reach the server can fix it without first authenticating). The override persists in `SecureStorage` (Keychain on iOS, EncryptedSharedPreferences on Android).
- Default fallback: **`https://api.arcana.fit` on both platforms** — the prod cutover is already done (see `defaultBaseUrl()` in `Platform.android.kt` / `Platform.ios.kt`), so a fresh install (debug included) reaches prod with no setup. It is NOT a localhost default. To run any build against a local server you MUST set an override in Developer Settings: `http://localhost:8000` (iOS simulator), `http://10.0.2.2:8000` (Android emulator host-loopback alias), or a Cloudflare quick-tunnel URL (physical device). Debug builds permit cleartext to `localhost` / `10.0.2.2` for exactly that.

**2. Server-side `*.trycloudflare.com` allowlist.**
- File: `arcana-server/arcana/settings/dev.py` — `ALLOWED_HOSTS` includes `.trycloudflare.com` (wildcard) and a placeholder `api.arcana.fit`. Also sets `SECURE_PROXY_SSL_HEADER` because Cloudflare terminates TLS at the edge.
- Why it exists: paired with #1. Without the allowlist Django 400s every request through the tunnel (unknown Host header).

**Cutover checklist when prod ships (probably alongside Phase 4 / Stripe or Phase 5 / booking):**

- [x] Flip the platform `defaultBaseUrl()` actuals to point at the prod hostname. **DONE** — both `Platform.android.kt` and `Platform.ios.kt` return `https://api.arcana.fit`. A fresh install reaches prod with no override; local dev now requires an explicit Developer Settings override.
- [ ] Decide on the future of the Developer Settings screen: either remove the auth-screen link entirely, gate the entire feature on a debug build flavor, or keep it as a "support" affordance for troubleshooting. The screen itself is self-contained — removing the AuthScreen entry point is sufficient to hide it from users.
- [ ] Remove the `BaseUrlProvider` if the runtime override is no longer needed (also drop the `defaultUrl` constructor param and revert `ArcanaApiClient` to a static base URL).
- [ ] In `arcana-server`, tighten `dev.py`'s `ALLOWED_HOSTS` (remove the `.trycloudflare.com` wildcard) once prod uses `prod.py` and tunnels aren't used for active dev.
- [ ] Make sure any in-flight Developer Settings overrides on testers' devices are reset, or accept that they're sticky and document the reset path.

## Open items

- **Live data wiring.** Home + Profile are now real-data-backed (Phase 5 — `HomeViewModel`/`ProfileViewModel` read `/memberships/me` + `/bookings/me/`). The Profile **"Your favorites"** section is real-data-backed via `FavoritesRepository` (whole-studio favorites first, then location-grain rows) with a Manage link into StudioSelection. Schedule + ClassDetail have been real-data-backed since Phase 3.
- **Time-based background refetch on Schedule.** Pull-to-refresh and resume-refresh both shipped (`PullToRefreshBox` → `ScheduleViewModel.refresh()`; `LifecycleResumeEffect` refreshes booked-pills on every return). Remaining: an optional full refetch when returning from background after N minutes (only bookings refresh on resume today).
- **Network image loader.** `ClassDetailScreen` currently renders a studio-color-tinted Box as the class hero placeholder because no Compose Multiplatform network-image library is configured. When adopting Coil-MP or Kamel, swap the placeholder for `AsyncImage` reading `session.template.heroImageUrl`.

## Schedule + Detail screens

`ScheduleScreen` is real-data-backed since Phase 3 and **cursor-paged since Phase 2 of schedule-loading**. `ScheduleViewModel.init` loads favorites (defaulting the scope toggle to Favorites when the member has any), then fetches the chip-rail overview (`ScheduleApi.fetchOverview`) plus page 1 of the selected day (`fetchSessionsPage`) in parallel. Day-chip taps fetch that day's first page from the server (per-day `DayState` caches are kept only for days visited under the current filter set); infinite scroll paginates via the keyset cursor (`DayState.nextCursor` / `loadMore`, with generation counters discarding stale in-flight pages). Since Phase 2 EVERY filter (scope, studio/location picks, time-of-day, modality categories) narrows **server-side** through a debounced (250ms) refetch pipeline — nothing re-buckets or refilters client-side. `WINDOW_DAYS = 15` now only defines the day-chip range. Pull-to-refresh re-fetches the overview + selected day's page 1; booked-status pills refresh independently on every resume. The capacity overline on each row shows a four-tier coarse label (`AVAILABLE / FILLING UP / ALMOST FULL / FULL`) instead of a precise number — this hides cross-screen capacity inconsistencies that would otherwise appear because the Schedule list and the Detail screen update independently.

`ClassDetailScreen` (Phase 3.5) is reached by tapping a class row on Schedule. `ClassDetailViewModel` takes the session id as a Koin parameter (`koinViewModel { parametersOf(id) }`) and fetches `GET /api/v1/classes/<id>/`. The server refreshes from the upstream platform if its cached row is > 30s old, so capacity numbers on the detail screen are always near-real-time — hence we DO show precise spot counts here. Cancelled sessions render a cancellation notice in place of the capacity block.

The DTOs in `data/ScheduleDto.kt` are shared between list and detail responses; detail-only fields (`template.description`, `template.layout_metadata`, `location.address`/`latitude`/`longitude`) have default values so list responses still deserialize cleanly.

## Booking flow (Phase 5)

Member booking UI built against the locked `arcana-server` `/api/v1/bookings/` + `/memberships/me` contract. Spec: `docs/superpowers/specs/2026-06-02-phase-5-booking-fulfillment-design.md` §11; plan: `docs/superpowers/plans/2026-06-02-phase-5-booking-mobile.md`.

**Data + API.** DTOs in `data/BookingDto.kt` (`BookingDto`, `SpotDto`, `SessionBriefDto` incl. `instructor`, `MyBookingsDto`, `CreateBooking*`, `CancelBookingResponse`) and `data/MembershipDto.kt` (`MembershipMeDto`). `ScheduleSessionDto` gained `spots: List<SpotDto>` (detail-only). `ArcanaApiClient` implements two **narrow interfaces** — `networking/BookingApi.kt` (`createBooking`/`myBookings`/`cancelBooking`) + `MembershipApi` (`membershipMe`) — so ViewModels depend on the interfaces and are faked in `commonTest`. `createBooking` inspects `response.status` manually (the client has `expectSuccess=false`, so 4xx does NOT throw) and surfaces the server `{error: code}` as `BookingError(code)`.

**Booking from class detail.** `schedule/ClassDetailScreen.kt`'s `StickyReserveCta` is driven by `booking/BookingViewModel.kt`: it reads `/me` (credits) + `/bookings/me/` (to detect an existing booking for this session), derives the CTA via the pure `bookCtaState(...)` → `BookCta` (`Bookable`/`Full`/`OutOfCredits`/`AlreadyBooked`/`NotBookable`), opens the `booking/BookingSheet.kt` confirmation sheet (Material3 `ModalBottomSheet`; `booking/SpotPicker.kt` for spot studios), submits, and maps errors via `bookingErrorCopy(code)`. The CTA reflects real status: `REQUESTED ✓` (just booked) / `REQUESTED` / `CONFIRMED ✓` on return, and `CLASS ENDED` (no availability, no-op) for past classes.

**My Bookings + Home + Profile.** `booking/MyBookingsScreen.kt` (`MyBookingsViewModel`) — a `MyBookings` non-tab destination reached from Home's "See all"; rows show date/time + instructor + `ui/StatusPill`, tap opens the class detail, X closes; cancel uses `cancel_policy.will_forfeit_credit` to warn before a forfeiting cancel. `home/HomeViewModel` + `profile/ProfileViewModel` read `/me` (+ `/bookings/me/`) and render real greeting/credits/streak/upcoming and member card; both shimmer while loading via `ui/Shimmer.kt`. `ui/StatusPill` mirrors the server ops-console pill tones (good/warn/bad).

**Auth: sign-in only.** There is no in-app sign-up — `auth/AuthScreen.kt` is login-only; members onboard via the invite welcome flow (welcome deep link → onboarding survey → `SignupCompletionScreen`). `ArcanaApiClient.login/logout/completeSignup` call `clearBearerTokenCache()` (clears the Ktor `Auth` plugin's in-memory bearer token via `client.authProviders…clearToken()`) so requests use the current user's token after a re-login — without it, the plugin keeps sending the previous user's cached token.

**Onboarding survey (August cohort+).** New members answer a 13-question survey between the welcome deep link and claim-your-name: `signup/SignupSurveyScreen.kt` + `SignupSurveyViewModel.kt`, questions in `signup/SignupSurveyQuestions.kt` — a 1:1 port of arcana-web `app/beta/survey.ts` (ids/options/order must stay in lockstep; the server's Google-Sheet mirror depends on it). Submits to `POST /api/v1/beta/signup-survey` (token-gated, unauthenticated — token is validated, never consumed) via the `SignupSurveyCallable` seam. Completion (or the post-failure "Continue anyway" skip — the survey must never block a paid member's signup) is persisted in `SecureStorage` keyed `signup_survey_done:<token>` via `AppSessionController.markSurveyDone/isSurveyDone` (App.kt collects the gate and advances), so re-tapping the email link goes straight to claim-your-name. Telemetry: `signup_survey_started/submitted/failed/skipped` + `$screen` `SignupSurvey`; taxonomy locked by `SignupSurveyTelemetryTest`.

## Telemetry (PostHog analytics + Sentry observability)

Product analytics (PostHog) and crash/nonfatal reporting (Sentry) for both platforms. **All instrumentation goes through one shared, type-safe layer — never call PostHog/Sentry SDKs directly from feature code.**

**Architecture.** `analytics/` in :sharedLogic's commonMain defines two interfaces — `Analytics` (PostHog) and `CrashReporter` (Sentry) — and the **`Telemetry` facade** that owns the *entire* event taxonomy (one typed method per event; event-name strings live only in `Telemetry.Events`). Feature code injects `Telemetry` (Koin `single`) and calls `telemetry.bookingSucceeded(...)`, `telemetry.screen(...)`, etc. Per-platform impls supply the interfaces:
- **Android** (:sharedUI's `androidMain/analytics/`): `PostHogAnalytics` + `SentryCrashReporter` over `posthog-android` + `sentry-android`; initialized in `ArcanaApplication.onCreate()` via `androidTelemetryModule(context)` (returns a Koin module binding the interfaces).
- **iOS** (`iosApp/iosApp/Analytics/` — Swift): `SwiftAnalytics` + `SwiftCrashReporter` over the PostHog + Sentry **Swift SDKs (SPM)**; `TelemetryBootstrap.start()` inits them in `iOSApp.swift` and the instances are passed into Kotlin via `MainViewController(analytics:crashReporter:)`, then registered into Koin. Mirrors the existing deep-link bridge.
- If a platform supplies no impl (blank key), the binding falls back to `NoopAnalytics`/`NoopCrashReporter` and the app runs with telemetry disabled.

**Adding/changing instrumentation (the rule):** add a typed method to `Telemetry` (+ its `Events`/`Screens` constant) and call it from the relevant ViewModel/screen — do **not** scatter raw `capture("...")` strings. ViewModels take `telemetry: Telemetry = Telemetry.Noop` (the default keeps tests/previews and the existing `commonTest` fakes compiling). Screen views fire from one `LaunchedEffect` keyed on the resolved screen name in `App.kt`'s `MainScaffold` (+ `Auth`/`Signup` in `App`). `identify` is called from `ProfileViewModel` on the first `/me` and is **deduped per session inside `Telemetry`** (cleared on `reset()`); don't re-add per-VM identify guards. Forced-vs-manual logout is distinguished in `ArcanaApiClient` (`forceLogout(cause)` vs `logout()`). Every event also carries a `platform` super property (`android`/`ios`).

**Keys/config (client-safe, gitignored — never commit).** Android: `sharedUI/analytics.properties` → `BuildConfig` (`build.gradle.kts` `analyticsProp(...)`). iOS: `iosApp/Configuration/Secrets.xcconfig` (optionally `#include?`'d by `Config.xcconfig`, referenced from `Info.plist` via `$(VAR)`). **xcconfig URL gotcha:** `//` starts a comment, so URLs/DSNs use a `SLASH = /` var (`https:${SLASH}${SLASH}host`) — never the `$()` trick (it silently truncates the host). A blank key/DSN disables that SDK. CI supplies values via `-P`/env vars instead.

**Dev behavior (debug builds only).** Every `Telemetry` call echoes to logcat / Xcode console via `logDebug` under a `▶ Telemetry` tag (gated on `isDebugBuild` in `Platform.kt`) — watch with `adb logcat -s Telemetry:D` or the Xcode console filtered on `D/Telemetry`. PostHog `flushAt = 1` in debug so events appear in PostHog Activity in ~seconds (release batches). Session replay is **on, fully masked** (text inputs + images). The PostHog dashboard is "Beta — App Health & Usage (Mobile)" (project 439926, US).

**Sentry specifics.** Sentry's **auto-init is disabled** in `AndroidManifest.xml` (`io.sentry.auto-init=false`) because its ContentProvider crashes on startup without a manifest DSN — we init manually, which is the single source. iOS dSYM upload is a Run Script phase (`iosApp/scripts/sentry-upload-dsyms.sh` + gitignored `.sentryclirc`). **iOS crash caveats:** crashes only capture when run **without the Xcode debugger attached** and upload on the next **cold launch**; native/Swift crashes symbolicate via dSYMs, but **uncaught Kotlin/Native exceptions surface as SIGABRT with imperfect stacks** — breadcrumbs (one per telemetry event) carry the action trail. Nonfatals and crashes are distinguished in Sentry by the `handled` flag, not by issue grouping.

**Why not `sentry-kotlin-multiplatform`:** deliberately deferred. It would only improve iOS *Kotlin-uncaught-crash* stacks (a narrow gain — Android JVM stacks and iOS native stacks are already good), and it's still 0.x, unverified on Kotlin 2.3.0, and needs finicky SPM↔Kotlin/Native linking against our static-framework setup. The `CrashReporter` interface is the seam, so swapping later is localized — revisit when the SDK hits 1.0 / confirms our toolchain, or when an iOS Kotlin crash is genuinely hard to debug.

**Verifying.** `commonTest/.../analytics/` has `FakeAnalytics`-backed regression tests that lock the taxonomy — add one when you add an event. Manual QA walkthrough: `docs/analytics-qa-checklist.md`. As always, compile **both** targets after touching `commonMain`.

### Performance & latency instrumentation

A perf-observability layer sits on top of the telemetry facade, feeding the **"Mobile Performance & Latency"** PostHog dashboard (project 439926, id 1849473). Spec/plan: `docs/superpowers/specs|plans/2026-07-14-mobile-performance-observability*`.

**Events:**
- **`api_request`** — emitted for EVERY HTTP call by `networking/PerfTimingPlugin.kt` (a Ktor client plugin on `ArcanaApiClient`), no per-call-site code. Carries `total_ms` (client round-trip), `server_ms` (from the `X-Arcana-Server-Ms` response header that arcana-server's `ServerTimingMiddleware` stamps), derived `network_ms = total − server`, `endpoint`, `outcome`, `status_code`, `response_bytes`.
- **`app_start_completed`** — cold-start → first Home frame, via `analytics/AppStartTracker.kt` (`markStart()` at the platform entry points, `onFirstContent()` in `App.kt`). Fires once per process (`start_type=cold`; warm deferred).
- **`screen_load_completed`** (`source` = cold_start/tab_switch/refresh/day_switch/filter) + **`schedule_page_loaded`** — fired from `ScheduleViewModel`. Reuses existing `class_viewed.load_ms` for class-detail latency.

**Maintenance rules (so this stays sustainable):**
- **New API endpoint** → add a `when` case to `analytics/ApiRequestMetrics.kt` `normalizeEndpoint(method, path)`, else it buckets as `other` on the dashboard (safe, but not individually visible). The FULL map is locked by `ApiRequestMetricsTest` — a rename fails the build. `other` is an intentional bounded fallback (auto-derived names would blow up cardinality on slug/uuid paths).
- **New journey/timing event** → typed method on `Telemetry` + `Events` constant + a `PerformanceTelemetryTest`/`ScheduleTelemetryTest` assertion (same rule as all telemetry).
- **New PostHog super-property** → register it AND **re-register it after `reset()`** in BOTH `PostHogAnalytics.kt` and `SwiftAnalytics.swift` — `reset()` (fired on logout) clears super-properties, so anything not re-registered is lost until the next cold start. `platform` and `environment` already do this.

**`environment` super-property** (`analytics/AppEnvironment.kt` `classifyEnvironment`): `prod` (`api.arcana.fit`) / `local` (localhost/127.0.0.1/10.0.2.2) / `tunnel` (`*.trycloudflare.com`) / `other`, derived from the resolved base-URL host. Owned by `BaseUrlProvider` (set at init + on every override change). The dashboard is filtered to `environment='prod'`, so **local/tunnel dev traffic is excluded** — your simulator/emulator testing never pollutes the prod metrics.

**iOS boolean gotcha:** a Kotlin `Boolean` in an event-property map boxes to `KotlinBoolean` crossing into Swift and is silently dropped by PostHog's serializer, so `SwiftAnalytics.bridge()` coerces it to a native `Bool`. Any new impl of `Analytics` on iOS must keep that coercion or boolean properties vanish on iOS only.
