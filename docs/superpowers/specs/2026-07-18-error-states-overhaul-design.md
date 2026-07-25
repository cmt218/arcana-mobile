# Error States Overhaul — Engineering Spec

**Date:** 2026-07-18
**Branch:** `feature/error-states-overhaul`
**Status:** Design approved; visual details pending Claude Design output (see companion brief: `docs/error-states-design-brief.md`).

---

## 1. Why

A member (Chad, our main Android tester) opened the freshly-updated app on a cold start, the Home screen's first API calls failed with a **client-side network error** (status 0, never reached the server), and the app told him **"server error."** The server was healthy the whole time. This is a copy-and-classification bug, and it's systemic.

Current state of error handling in the app:

- **Mislabels connection failures as server errors.** `HomeViewModel`, `ScheduleViewModel`, `ClassDetailViewModel`, `MyBookingsViewModel`, and `ProfileViewModel` emit a bare `"server error"` (or `"server error $code"`) in their catch blocks — including the generic `catch (e: Exception)` path, which is exactly where a dropped connection lands.
- **Inconsistent UI.** `Schedule` has a proper full block (`ErrorBlock`: headline + message + RETRY pill). `Home` shows a tiny gray `Caption` with **no retry at all**. Class Detail / My Bookings / Profile each roll their own.
- **The knowledge already exists, unused.** `analytics/ApiRequestMetrics.kt#apiRequestOutcome(statusCode)` already buckets `0 → network_error`, `5xx → server_error`. The UI layer just doesn't reuse it. `Auth` and `Signup` flows already split "couldn't reach the server" vs "something went wrong on our end" — proof the two-state model fits the app.

## 2. Goal / Non-goals

**Goal:** One shared classifier and one shared error-UI family, used across every data-loading screen, that distinguishes two member-facing categories and always offers a way to recover.

- **Connection** — the request never got a valid answer from the server (offline, flaky, timeout, DNS, status 0). Copy is about *your connection*, never "server error."
- **Server** — the server answered badly (5xx, or an unexpected 4xx that isn't auth), or an app-side failure after a response. Copy owns the fault ("on our end").

**Non-goals (explicitly out of scope for this change):**

- **Auth / 401 / session expiry.** Already handled by the token-refresh interceptor (`refreshOutcomeForStatus`: 401/403 → REJECTED). Untouched.
- **Form-field validation errors** (Signup, Edit Profile, Concierge). They already have good, specific copy. May adopt the shared `InlineError` later; not in this pass.
- **API-layer auto-retry / backoff.** A silent retry on the cold-start race would have hidden Chad's error entirely and is worth doing, but it's a separate transport concern. Called out as a **fast-follow**, not built here. This spec delivers the *manual* retry UI the member can act on.
- **Loading-state redesign.** Shimmer / `DotMatrixLoader` stay as they are (the retry button reuses `DotMatrixLoader` for its loading state).

## 3. Taxonomy (decided)

| Category | Trigger (as seen in a ViewModel catch) | Member-facing framing |
|---|---|---|
| **Connection** | Any non-HTTP failure: `IOException`, timeout, `CancellationException`-adjacent transport drop, i.e. **not** a `ResponseException` (no HTTP status was received). Equivalent to `apiRequestOutcome == network_error`. | "Can't reach Arcana. Check your connection." |
| **Server** | A `ResponseException` with a status: 5xx, or an unexpected 4xx that isn't 401/403. Equivalent to `apiRequestOutcome ∈ {server_error, client_error}`. | "Something's off on our end." |
| **Auth (out of scope)** | 401/403 | Handled upstream; never reaches these catches in practice. |

## 4. Architecture

Three small, independently-testable units. Each has one job.

### 4.1 `networking/ErrorType.kt` — the classifier (new)

```kotlin
enum class ErrorType { CONNECTION, SERVER }

/** Map any caught failure to a member-facing category.
 *  A received HTTP status (ResponseException) => SERVER; anything with no
 *  response (IO/timeout/transport) => CONNECTION. Mirrors
 *  ApiRequestMetrics.apiRequestOutcome so telemetry and UI agree. */
fun Throwable.toErrorType(): ErrorType = when (this) {
    is ResponseException -> ErrorType.SERVER   // 4xx (non-auth) + 5xx
    else -> ErrorType.CONNECTION               // no status was received
}
```

- Pure, no Android/HttpClient deps → unit-tested like `ApiRequestMetrics`.
- Single source of truth. Delete the per-screen string logic.

### 4.2 `ui/ErrorState.kt` — the shared UI family (new)

Composables, driven by `ErrorType`, styled per the Claude Design output. Copy is centralized, not passed in as strings:

- `FullScreenError(type: ErrorType, retrying: Boolean, onRetry: () -> Unit)` — cold-load, nothing cached to show.
- `InlineError(type: ErrorType, retrying: Boolean, onRetry: () -> Unit)` — one section failed inside an otherwise-populated screen.
- `RetryButton(retrying: Boolean, onClick)` — idle / retrying (`DotMatrixLoader`) / re-enable-on-fail. Reused by both.
- A transient **refresh-failed toast** helper — reuses the app's existing snackbar/transient-notice mechanism (confirm the exact host during implementation; add a minimal one only if none exists).
- `internal object ErrorCopy` — maps `ErrorType × surface → (headline, body, cta)`, seeded from the locked copy in the design brief. Enforces the no-dashes / no-"server error"-for-connection rules in one place.

Built entirely from existing primitives: `ui/Text.kt` (Heading2/BodyText/Caption/Overline), `ui/Buttons.kt`, `ui/DotMatrixLoader.kt`, `theme/AppColors.kt` tokens (Ink/Ash/Stone/BurntNectar/Lime).

### 4.3 ViewModel state changes

Replace stringly-typed error state with the category. Per screen:

- **State type:** `Error(message: String)` → `Error(type: ErrorType)` on each screen's UiState sealed type (`HomeUiState`, `ScheduleUiState`, etc.).
- **Catch blocks:** `applyRefetchFailure("server error $code")` / `Error("server error")` → classify via `e.toErrorType()`. The generic `catch (e: Exception)` path now correctly yields `CONNECTION`.
- **Home specifically:**
  - Cold load (no cached success) → `FullScreenError` **with retry** (today it's a no-retry caption — this is the direct fix for Chad's screen).
  - Refresh failure with content already on screen → **toast**, keep the stale content (preserves the existing "don't replace good content" behavior).
- **Schedule:** delete the local `ErrorBlock` (ScheduleScreen.kt ~326), point `ScheduleUiState.Error` at `FullScreenError`, keep `onRetry = viewModel::reload`. Keep the generation/staleness guard exactly as-is.

## 5. Data flow

```
API call throws
  └─ ViewModel catch → e.toErrorType() → UiState.Error(type)
       └─ Screen renders FullScreenError/InlineError(type, retrying, onRetry)
            └─ onRetry → VM re-loads → UiState.Loading (retry button shows DotMatrixLoader)
                 └─ success → content, or Error(type) again
```

The toast path is orthogonal: refresh fails → emit transient notice → state stays `Success` with stale data.

## 6. Files

**New**
- `composeApp/src/commonMain/kotlin/org/arcana/mobile/networking/ErrorType.kt`
- `composeApp/src/commonMain/kotlin/org/arcana/mobile/ui/ErrorState.kt`
- `composeApp/src/commonTest/kotlin/org/arcana/mobile/networking/ErrorTypeTest.kt`

**Edit (swap string error → `ErrorType`, render shared component)**
- `home/HomeViewModel.kt` (+ `home/HomeScreen.kt` — add retry + toast, remove caption-only render)
- `schedule/ScheduleViewModel.kt` (+ `schedule/ScheduleScreen.kt` — remove local `ErrorBlock`)
- `schedule/ClassDetailViewModel.kt` (+ `ClassDetailScreen.kt`)
- `booking/MyBookingsViewModel.kt`
- `profile/ProfileViewModel.kt` (+ `profile/ProfileScreen.kt`)

**Reuse (no change)**
- `analytics/ApiRequestMetrics.kt` — the classifier mirrors its `apiRequestOutcome` buckets. If it's low-risk to share one helper, factor the status→bucket mapping so both call it; otherwise keep them parallel and covered by a test that asserts they agree.

**Tests to update** (they currently assert on `"server error"` / `"network"` strings)
- `booking/HomeViewModelTest.kt`, `profile/ProfileViewModelTest.kt`, `studios/StudioSelectionViewModelTest.kt`, and any schedule VM test asserting the error string → assert on `ErrorType` instead.

## 7. Testing & local end-to-end verification

### 7.1 Automated
- `ErrorTypeTest` — `ResponseException(500)` and `(404)` → `SERVER`; `IOException` / timeout / generic `Exception` → `CONNECTION`. Add a guard test that `toErrorType` agrees with `apiRequestOutcome` for representative statuses.
- Update the VM tests above to assert `Error(ErrorType.CONNECTION)` vs `Error(ErrorType.SERVER)` in the offline vs 5xx cases (the fakes already inject failures like `"network failure"` — point them at the two branches).
- Run: `./gradlew :composeApp:allTests` (or `:composeApp:testDebugUnitTest` for the JVM/Android common tests).

### 7.2 Manual, end-to-end (your visual spot-check)

The app re-reads the base URL **per request** via **Developer Settings**, so you can flip an app into each state at runtime without a rebuild.

**Build & run**
- Android: `./gradlew :composeApp:installDebug` then launch, or run `composeApp` from Android Studio on an emulator/device.
- iOS: open `iosApp/iosApp.xcodeproj` (or the workspace) in Xcode and run on a simulator.

**Force a CONNECTION error**
1. Cold path (reproduces Chad's bug): kill the app, enable **airplane mode** (Android emulator: quick-settings airplane toggle, or `adb shell svc wifi disable && adb shell svc data disable`), cold-launch. Home should show the **Connection** full-screen state **with a working retry**, not "server error."
2. Or, in **Developer Settings**, point the base URL at an unreachable host (e.g. `http://10.255.255.1`) and pull-to-refresh / navigate.
3. Tap **Try again** while still offline → stays on the Connection state. Restore network → retry → content loads.

**Force a SERVER error**
- In **Developer Settings**, point the base URL at an endpoint that returns 5xx (a throwaway `httpstat.us/500` style host, or a local stub), then load Home/Schedule/Class Detail. You should get the **Server** state ("on our end"), distinct copy and accent from Connection.

**Force the TOAST (refresh-failed, stale content stays)**
- Load Home successfully (real base URL), then flip to a bad base URL in Developer Settings and trigger a refresh. Existing content should remain, with a transient "Couldn't refresh. Showing your last update." notice — not a full-screen wipe.

**Visual checklist (per Claude Design output)**
- [ ] Home cold + offline → Connection full-screen, retry present and functional
- [ ] Schedule cold + offline → Connection full-screen (old `ErrorBlock` gone)
- [ ] Any screen + 5xx → Server full-screen, visibly distinct from Connection
- [ ] Inline/section error renders within a populated screen (Home sub-section)
- [ ] Refresh-failed toast keeps stale content
- [ ] Retry button: idle → loading (`DotMatrixLoader`) → re-enabled on repeat failure
- [ ] Light **and** dark for each
- [ ] No em/en dashes in any copy; Connection copy never says "server error"

## 8. Dev workflow (per your preference)

1. `git checkout main && git pull --ff-only` → up to date.
2. `git checkout -b feature/error-states-overhaul` → **done** (this branch).
3. Implement per §6, wiring the shared `ErrorState` composables to the finalized Claude Design visuals.
4. **Leave the working tree uncommitted** for your review. No commit, no push, no PR until you say go.
5. When you approve, the mobile changes follow the normal path (your call on commit message / PR).

## 9. Sequencing & risk

- **Blocked on:** Claude Design output for the visual details of `ui/ErrorState.kt`. The classifier (§4.1), the state-type changes (§4.3), and the tests (§7.1) are **design-independent** and can be built first; only the composable's visual body waits on designs.
- **Risk:** low and additive. Pure client UI + classification; no API, server, or auth changes. Regression-sensitive per the beta posture — the one behavioral change to preserve carefully is Home's "keep stale content on refresh failure," now routed to the toast instead of being swallowed.
- **Follow-up (separate spec):** API-layer retry/backoff on cold-start so a transient blip self-heals before any error UI is shown.

## 10. Implementation notes (decisions taken while building)

Two deviations from the Claude Design handoff, both deliberate:

1. **Light variants only; dark deferred.** The handoff specifies light + dark, but the app has no dark mode: `ArcanaTheme` installs a `lightColorScheme` and nothing in the codebase reads `isSystemInDarkTheme`. Shipping dark error screens inside an otherwise-light app would look broken, and building app-wide dark theming is far outside this change. Color resolution is therefore funnelled through `accentFor()` / `overlineColorFor()` plus the surface constants in `ui/ErrorState.kt`, so adding dark later is a change to those resolvers rather than to every composable. **The dark mockups remain valid and unused until app-wide dark mode exists.**
2. **Redlines snapped to the house grid.** `CLAUDE.md` mandates 4dp padding increments and 2sp text increments. Several redline values are off-grid, and were snapped to the nearest in-system value: padding 30→32, button padding 17×30→16×32, card padding 22→24, spacer 15→16, dot 7→8, headline 37→36sp, inline headline 19→20sp. Kept as-is: the 2dp motif bars and the 14dp corner radius (both decorative, and analogous to the existing 1dp-hairline exception). Deltas are ≤2dp/1sp and visually imperceptible; revert to exact redlines if strict fidelity is preferred.

Also unchanged: `StudioSelectionViewModel` still carries its own hand-written `"Couldn't load Studios."` copy. It sits in the onboarding flow, was not in this spec's scope, and its copy is already category-neutral and correct. Candidate for a later pass.
