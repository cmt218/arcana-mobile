# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

**Android:**
```bash
./gradlew :composeApp:assembleDebug
```

**iOS:** Open `/iosApp/iosApp.xcodeproj` in Xcode and run from there. The Kotlin framework is compiled as a static framework and linked by Xcode.

**Tests (shared/common):**
```bash
./gradlew :composeApp:testDebugUnitTest   # runs commonTest on the Android JVM target
```
(The aggregate `:composeApp:test` task does NOT accept `--tests`; filter on `:composeApp:testDebugUnitTest`.)

**ALWAYS compile BOTH targets when touching `commonMain`** — Android alone won't catch Kotlin/Native errors:
```bash
./gradlew :composeApp:compileDebugKotlinAndroid       # JVM/Android
./gradlew :composeApp:compileKotlinIosSimulatorArm64  # Kotlin/Native (iOS)
```
JVM-only APIs compile on Android but break the iOS build — notably `String.format` / `"%02d".format(x)` / `java.*` / `Locale`. Use string templates, `padStart`, and `kotlinx-datetime` instead. (`compileKotlinMetadata` is a no-op SKIP in this project — don't rely on it.)

No linter is configured (no ktlint/detekt).

**Clearing the iOS Simulator Keychain** (useful when testing auth flows): Simulator app → Device → Erase All Content and Settings. Uninstalling the app alone does not clear Keychain items.

## Architecture

This is a **Kotlin Compose Multiplatform** project targeting Android and iOS. All UI is written once in Compose and shared via `commonMain`.

**Source sets in `composeApp/src/`:**
- `commonMain/` — shared Kotlin/Compose code for all platforms. This is where new features should live.
- `androidMain/` — Android `actual` implementations and `MainActivity`
- `iosMain/` — iOS `actual` implementations and `MainViewController` (wrapped by SwiftUI in `iosApp/`)
- `commonTest/` — shared tests using `kotlin-test`

**Platform abstraction pattern:** `Platform.kt` in `commonMain` declares an `expect interface`; `Platform.android.kt` and `Platform.ios.kt` provide `actual` implementations. Follow this pattern for any platform-divergent behavior. `getBaseUrl()` is an existing example — returns `http://10.0.2.2:8000` on Android (emulator host alias) and `http://localhost:8000` on iOS.

**Dependency injection:** Koin 4.x. `di/AppModule.kt` in `commonMain` defines all bindings. Koin is started at the platform entry point — `ArcanaApplication.onCreate()` on Android, `MainViewController()` on iOS — before any Compose code runs. Use `koinInject()` for values and `koinViewModel()` for ViewModels in composables.

**Auth architecture:**
- `ArcanaApiClient` owns a `StateFlow<Boolean>` (`isAuthenticated`) that is the single source of truth for session state. It is initialized from `TokenStorage.isLoggedIn` at startup and updated by `login()`, `register()`, `logout()`, and token refresh failure.
- `App.kt` collects `isAuthenticated` and gates the entire main scaffold behind it. A session-scoped `ViewModelStore` (provided via `LocalViewModelStoreOwner`) wraps `MainScaffold` so all authenticated ViewModels are destroyed on logout and recreated fresh on next login.
- `auth/SecureStorage` is an `expect/actual` class: `EncryptedSharedPreferences` on Android, iOS Keychain via CoreFoundation/Security on iOS. `TokenStorage` wraps it with typed `accessToken`/`refreshToken` properties.
- The Ktor `Auth` plugin handles Bearer header injection and 401/token-refresh automatically for all authenticated requests. `sendWithoutRequest` excludes the login, register, and refresh endpoints from receiving auth headers.

**Networking:** Ktor 3.x with `kotlinx-serialization`. `networking/ArcanaApiClient.kt` contains the `HttpClient` and all endpoint methods. Data classes live in `data/` and are annotated with `@Serializable`. ViewModels live in feature packages (e.g. `classes/ClassesViewModel.kt`) and expose `StateFlow<UiState>` sealed interfaces with `Loading`, `Success`, and `Error` states.

**Date/time:** `kotlinx-datetime` in commonMain. Use `Clock.System.todayIn(TimeZone.currentSystemDefault())` for today, `Clock.System.now().toLocalDateTime(tz)` for current local time. `DayOfWeek` and `Month` are enums with `.name` returning uppercase strings (take(3) for the design's three-letter abbreviations).

**Android dev networking:** The debug source set (`src/debug/`) contains a `network_security_config.xml` that permits cleartext to `localhost` and `10.0.2.2`. This is debug-only and cannot ship in release builds.

**iOS entry point flow:** `iosApp/ContentView.swift` → `MainViewControllerKt.MainViewController()` (Kotlin) → Compose UI.

**Key versions (see `gradle/libs.versions.toml`):**
- Kotlin: 2.3.0
- Compose Multiplatform: 1.10.0
- Koin: 4.2.0
- Ktor: 3.1.2
- kotlinx-datetime: 0.7.1
- Navigation Compose: 2.9.2 (JetBrains CMP port — pinned to a version matched to the CMP release in the CMP CHANGELOG; do not bump independently of `composeMultiplatform`)
- Android min/target SDK: 24/36
- Package: `org.arcana.mobile`

## Design system

The brand-aligned theming lives in `commonMain/kotlin/org/arcana/mobile/theme/` and the reusable UI primitives in `commonMain/kotlin/org/arcana/mobile/ui/`. **Screens should never hand-roll `TextStyle`s, hex colors, or raw icon paths** — compose them from these primitives. The brand color hexes and the typography hierarchy are sourced from the brand identity doc and the typography doc respectively (see the parent `arcana/CLAUDE.md` for live links).

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

`App.kt` hosts a `NavHost` keyed off `navigation/ArcanaDestinations.kt` — a sealed `ArcanaDestination` with `@Serializable` data objects per destination (no string routes). The bottom bar's active tab is derived from `currentBackStackEntryAsState` via `hasRoute<T>()`; it hides on non-tab destinations (currently just `StudioSelection`) so a stray tab tap mid-flow can't silently pop the in-progress entry off the stack.

Tab navigation uses the standard `popUpTo(start) { saveState = true }` / `launchSingleTop` / `restoreState` block so each tab keeps its own back stack + scroll position.

Transitions are pinned at the `NavHost` level (150ms fade, all four slots) for cross-platform consistency — iOS's default slide reads wrong between sibling tabs. Override per-`composable` if a destination needs its own transition.

**Dependency pinning.** `org.jetbrains.androidx.navigation:navigation-compose` is the JetBrains CMP port, not Jetpack Navigation. Its version must match the `composeMultiplatform` release — find the matched version in the CMP CHANGELOG, not in Jetpack/Android docs. Bumping CMP almost always means bumping nav-compose; bumping nav-compose alone risks pulling a conflicting Compose runtime transitive.

## Temporary debug treatment

These are pre-launch dev affordances that must be removed (or hardened) before public release. Each is tagged inline with a comment pointing back here for findability.

**1. Runtime API base URL override + Developer Settings screen.**
- Files: `networking/BaseUrlProvider.kt`, `settings/DeveloperSettingsScreen.kt`, `settings/DeveloperSettingsViewModel.kt`, the entry-point link in `auth/AuthScreen.kt`'s footer.
- Why it exists: pre-launch we run the server on Cole's Mac and expose it to physical devices via a Cloudflare *quick* tunnel. Quick-tunnel URLs change on every `cloudflared` restart, so rebuilding the app each time would be miserable. The override is editable at runtime via the Developer Settings overlay (reachable from the auth screen footer — so testers locked out of login because the default doesn't reach the server can fix it without first authenticating). The override persists in `SecureStorage` (Keychain on iOS, EncryptedSharedPreferences on Android).
- Default fallback: platform-specific. Android `http://10.0.2.2:8000` (emulator loopback); iOS `http://localhost:8000` (simulator). Physical devices always need the override — the default just keeps emulator/simulator dev frictionless.

**2. Server-side `*.trycloudflare.com` allowlist.**
- File: `arcana-server/arcana/settings/dev.py` — `ALLOWED_HOSTS` includes `.trycloudflare.com` (wildcard) and a placeholder `api.arcana.fit`. Also sets `SECURE_PROXY_SSL_HEADER` because Cloudflare terminates TLS at the edge.
- Why it exists: paired with #1. Without the allowlist Django 400s every request through the tunnel (unknown Host header).

**Cutover checklist when prod ships (probably alongside Phase 4 / Stripe or Phase 5 / booking):**

- [ ] Flip the platform `defaultBaseUrl()` actuals to point at the prod hostname (currently `https://api.arcana.fit`). Test that a fresh install on a physical device works with no override set.
- [ ] Decide on the future of the Developer Settings screen: either remove the auth-screen link entirely, gate the entire feature on a debug build flavor, or keep it as a "support" affordance for troubleshooting. The screen itself is self-contained — removing the AuthScreen entry point is sufficient to hide it from users.
- [ ] Remove the `BaseUrlProvider` if the runtime override is no longer needed (also drop the `defaultUrl` constructor param and revert `ArcanaApiClient` to a static base URL).
- [ ] In `arcana-server`, tighten `dev.py`'s `ALLOWED_HOSTS` (remove the `.trycloudflare.com` wildcard) once prod uses `prod.py` and tunnels aren't used for active dev.
- [ ] Make sure any in-flight Developer Settings overrides on testers' devices are reset, or accept that they're sticky and document the reset path.

## Open items

- **Live data wiring.** Home + Profile are now real-data-backed (Phase 5 — `HomeViewModel`/`ProfileViewModel` read `/memberships/me` + `/bookings/me/`). The Profile **"Your studios"** section is still static mock (favorite-studios-with-real-data is a future feature). Schedule + ClassDetail have been real-data-backed since Phase 3.
- **Pull-to-refresh + resume-refresh on Schedule.** Today the Schedule fetch happens once on `ScheduleViewModel.init` and again only on a fresh login. Add a pull-to-refresh gesture and an auto-refresh when the app returns from background if last fetch > N min ago.
- **Network image loader.** `ClassDetailScreen` currently renders a studio-color-tinted Box as the class hero placeholder because no Compose Multiplatform network-image library is configured. When adopting Coil-MP or Kamel, swap the placeholder for `AsyncImage` reading `session.template.heroImageUrl`.

## Schedule + Detail screens

`ScheduleScreen` is real-data-backed since Phase 3. `ScheduleViewModel.init` fetches the 15-day window (today + 14, `WINDOW_DAYS = 15`) once via `ArcanaApiClient.fetchSchedule(from, to, ...)`; subsequent day-chip taps re-bucket the cached result client-side, filter chips refilter (studio chips client-side, `available_only` triggers a refetch because that filter is server-side). The capacity overline on each row shows a four-tier coarse label (`AVAILABLE / FILLING UP / ALMOST FULL / FULL`) instead of a precise number — this hides cross-screen capacity inconsistencies that would otherwise appear because the Schedule list and the Detail screen update independently.

`ClassDetailScreen` (Phase 3.5) is reached by tapping a class row on Schedule. `ClassDetailViewModel` takes the session id as a Koin parameter (`koinViewModel { parametersOf(id) }`) and fetches `GET /api/v1/classes/<id>/`. The server refreshes from the upstream platform if its cached row is > 30s old, so capacity numbers on the detail screen are always near-real-time — hence we DO show precise spot counts here. Cancelled sessions render a cancellation notice in place of the capacity block.

The DTOs in `data/ScheduleDto.kt` are shared between list and detail responses; detail-only fields (`template.description`, `template.layout_metadata`, `location.address`/`latitude`/`longitude`) have default values so list responses still deserialize cleanly.

## Booking flow (Phase 5)

Member booking UI built against the locked `arcana-server` `/api/v1/bookings/` + `/memberships/me` contract. Spec: `docs/superpowers/specs/2026-06-02-phase-5-booking-fulfillment-design.md` §11; plan: `docs/superpowers/plans/2026-06-02-phase-5-booking-mobile.md`.

**Data + API.** DTOs in `data/BookingDto.kt` (`BookingDto`, `SpotDto`, `SessionBriefDto` incl. `instructor`, `MyBookingsDto`, `CreateBooking*`, `CancelBookingResponse`) and `data/MembershipDto.kt` (`MembershipMeDto`). `ScheduleSessionDto` gained `spots: List<SpotDto>` (detail-only). `ArcanaApiClient` implements two **narrow interfaces** — `networking/BookingApi.kt` (`createBooking`/`myBookings`/`cancelBooking`) + `MembershipApi` (`membershipMe`) — so ViewModels depend on the interfaces and are faked in `commonTest`. `createBooking` inspects `response.status` manually (the client has `expectSuccess=false`, so 4xx does NOT throw) and surfaces the server `{error: code}` as `BookingError(code)`.

**Booking from class detail.** `schedule/ClassDetailScreen.kt`'s `StickyReserveCta` is driven by `booking/BookingViewModel.kt`: it reads `/me` (credits) + `/bookings/me/` (to detect an existing booking for this session), derives the CTA via the pure `bookCtaState(...)` → `BookCta` (`Bookable`/`Full`/`OutOfCredits`/`AlreadyBooked`/`NotBookable`), opens the `booking/BookingSheet.kt` confirmation sheet (Material3 `ModalBottomSheet`; `booking/SpotPicker.kt` for spot studios), submits, and maps errors via `bookingErrorCopy(code)`. The CTA reflects real status: `REQUESTED ✓` (just booked) / `REQUESTED` / `CONFIRMED ✓` on return, and `CLASS ENDED` (no availability, no-op) for past classes.

**My Bookings + Home + Profile.** `booking/MyBookingsScreen.kt` (`MyBookingsViewModel`) — a `MyBookings` non-tab destination reached from Home's "See all"; rows show date/time + instructor + `ui/StatusPill`, tap opens the class detail, X closes; cancel uses `cancel_policy.will_forfeit_credit` to warn before a forfeiting cancel. `home/HomeViewModel` + `profile/ProfileViewModel` read `/me` (+ `/bookings/me/`) and render real greeting/credits/streak/upcoming and member card; both shimmer while loading via `ui/Shimmer.kt`. `ui/StatusPill` mirrors the server ops-console pill tones (good/warn/bad).

**Auth: sign-in only.** There is no in-app sign-up — `auth/AuthScreen.kt` is login-only; members onboard via the invite welcome flow (welcome deep link → onboarding survey → `SignupCompletionScreen`). `ArcanaApiClient.login/logout/completeSignup` call `clearBearerTokenCache()` (clears the Ktor `Auth` plugin's in-memory bearer token via `client.authProviders…clearToken()`) so requests use the current user's token after a re-login — without it, the plugin keeps sending the previous user's cached token.

**Onboarding survey (August cohort+).** New members answer a 13-question survey between the welcome deep link and claim-your-name: `signup/SignupSurveyScreen.kt` + `SignupSurveyViewModel.kt`, questions in `signup/SignupSurveyQuestions.kt` — a 1:1 port of arcana-web `app/beta/survey.ts` (ids/options/order must stay in lockstep; the server's Google-Sheet mirror depends on it). Submits to `POST /api/v1/beta/signup-survey` (token-gated, unauthenticated — token is validated, never consumed) via the `SignupSurveyCallable` seam. Completion (or the post-failure "Continue anyway" skip — the survey must never block a paid member's signup) is persisted in `SecureStorage` keyed `signup_survey_done:<token>` from the gate in `App.kt`, so re-tapping the email link goes straight to claim-your-name. Telemetry: `signup_survey_started/submitted/failed/skipped` + `$screen` `SignupSurvey`; taxonomy locked by `SignupSurveyTelemetryTest`.

## Telemetry (PostHog analytics + Sentry observability)

Product analytics (PostHog) and crash/nonfatal reporting (Sentry) for both platforms. **All instrumentation goes through one shared, type-safe layer — never call PostHog/Sentry SDKs directly from feature code.**

**Architecture.** `analytics/` in `commonMain` defines two interfaces — `Analytics` (PostHog) and `CrashReporter` (Sentry) — and the **`Telemetry` facade** that owns the *entire* event taxonomy (one typed method per event; event-name strings live only in `Telemetry.Events`). Feature code injects `Telemetry` (Koin `single`) and calls `telemetry.bookingSucceeded(...)`, `telemetry.screen(...)`, etc. Per-platform impls supply the interfaces:
- **Android** (`androidMain/analytics/`): `PostHogAnalytics` + `SentryCrashReporter` over `posthog-android` + `sentry-android`; initialized in `ArcanaApplication.onCreate()` via `androidTelemetryModule(context)` (returns a Koin module binding the interfaces).
- **iOS** (`iosApp/iosApp/Analytics/` — Swift): `SwiftAnalytics` + `SwiftCrashReporter` over the PostHog + Sentry **Swift SDKs (SPM)**; `TelemetryBootstrap.start()` inits them in `iOSApp.swift` and the instances are passed into Kotlin via `MainViewController(analytics:crashReporter:)`, then registered into Koin. Mirrors the existing deep-link bridge.
- If a platform supplies no impl (blank key), the binding falls back to `NoopAnalytics`/`NoopCrashReporter` and the app runs with telemetry disabled.

**Adding/changing instrumentation (the rule):** add a typed method to `Telemetry` (+ its `Events`/`Screens` constant) and call it from the relevant ViewModel/screen — do **not** scatter raw `capture("...")` strings. ViewModels take `telemetry: Telemetry = Telemetry.Noop` (the default keeps tests/previews and the existing `commonTest` fakes compiling). Screen views fire from one `LaunchedEffect(currentDestination)` in `App.kt`'s `MainScaffold` (+ `Auth`/`Signup` in `App`). `identify` is called from `ProfileViewModel` on the first `/me` and is **deduped per session inside `Telemetry`** (cleared on `reset()`); don't re-add per-VM identify guards. Forced-vs-manual logout is distinguished in `ArcanaApiClient` (`forceLogout(cause)` vs `logout()`). Every event also carries a `platform` super property (`android`/`ios`).

**Keys/config (client-safe, gitignored — never commit).** Android: `composeApp/analytics.properties` → `BuildConfig` (`build.gradle.kts` `analyticsProp(...)`). iOS: `iosApp/Configuration/Secrets.xcconfig` (optionally `#include?`'d by `Config.xcconfig`, referenced from `Info.plist` via `$(VAR)`). **xcconfig URL gotcha:** `//` starts a comment, so URLs/DSNs use a `SLASH = /` var (`https:${SLASH}${SLASH}host`) — never the `$()` trick (it silently truncates the host). A blank key/DSN disables that SDK. CI supplies values via `-P`/env vars instead.

**Dev behavior (debug builds only).** Every `Telemetry` call echoes to logcat / Xcode console via `logDebug` under a `▶ Telemetry` tag (gated on `isDebugBuild` in `Platform.kt`) — watch with `adb logcat -s Telemetry:D` or the Xcode console filtered on `D/Telemetry`. PostHog `flushAt = 1` in debug so events appear in PostHog Activity in ~seconds (release batches). Session replay is **on, fully masked** (text inputs + images). The PostHog dashboard is "Beta — App Health & Usage (Mobile)" (project 439926, US).

**Sentry specifics.** Sentry's **auto-init is disabled** in `AndroidManifest.xml` (`io.sentry.auto-init=false`) because its ContentProvider crashes on startup without a manifest DSN — we init manually, which is the single source. iOS dSYM upload is a Run Script phase (`iosApp/scripts/sentry-upload-dsyms.sh` + gitignored `.sentryclirc`). **iOS crash caveats:** crashes only capture when run **without the Xcode debugger attached** and upload on the next **cold launch**; native/Swift crashes symbolicate via dSYMs, but **uncaught Kotlin/Native exceptions surface as SIGABRT with imperfect stacks** — breadcrumbs (one per telemetry event) carry the action trail. Nonfatals and crashes are distinguished in Sentry by the `handled` flag, not by issue grouping.

**Why not `sentry-kotlin-multiplatform`:** deliberately deferred. It would only improve iOS *Kotlin-uncaught-crash* stacks (a narrow gain — Android JVM stacks and iOS native stacks are already good), and it's still 0.x, unverified on Kotlin 2.3.0, and needs finicky SPM↔Kotlin/Native linking against our static-framework setup. The `CrashReporter` interface is the seam, so swapping later is localized — revisit when the SDK hits 1.0 / confirms our toolchain, or when an iOS Kotlin crash is genuinely hard to debug.

**Verifying.** `commonTest/.../analytics/` has `FakeAnalytics`-backed regression tests that lock the taxonomy — add one when you add an event. Manual QA walkthrough: `docs/analytics-qa-checklist.md`. As always, compile **both** targets after touching `commonMain`.
