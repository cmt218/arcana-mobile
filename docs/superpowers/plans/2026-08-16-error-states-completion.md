# Error States Overhaul — Completion Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the banked CONNECTION/SERVER error-state system on post-split `main`, add the client HTTP timeout that makes those states actually fire, and close the three device-observed gaps the 2026-08-11 regression run found (ERR-03 silent day-chip, ERR-11 network/server conflation, SCHED-02 scope loss on retry).

**Architecture:** One pure classifier (`ErrorType`) in `:sharedLogic`, one shared Compose family (`ErrorState.kt`) in `:sharedUI`, and per-screen migration from `Error(String)` to `Error(ErrorType)`. Copy lives in exactly one object (`ErrorCopy`); no screen hand-writes an error string. The timeout is a Ktor `HttpTimeout` plugin install on the single `HttpClient`.

**Tech Stack:** Kotlin Compose Multiplatform 1.11.1, Kotlin 2.3.10, Ktor 3.1.2, Koin 4.2.0, kotlin.test in `commonTest`.

**Supersedes:** `docs/superpowers/plans/2026-07-18-error-states-overhaul-and-reliability-plan.md` (paths, QA levers, and scope are stale — see §"What changed" below). Design spec of record: `docs/superpowers/specs/2026-07-18-error-states-overhaul-design.md` (restored in Task 0).

**Reference material:** branch `feature/error-states-overhaul` @ `a5e02bd`. Salvage from it; do **not** rebase it. Its 17 files sit under the deleted `composeApp/`.

---

## Global Constraints

- **Module split.** Compose-free logic + ALL ViewModels → `:sharedLogic`. All Compose UI → `:sharedUI`. `ErrorType.kt` goes in `:sharedLogic` (it must stay Compose-free); `ErrorState.kt` goes in `:sharedUI`. Note `:sharedLogic` *also* has an `org.arcana.mobile.ui` package — the package name alone does not tell you the module.
- **Compile BOTH targets after any `commonMain` change.** Android alone does not catch Kotlin/Native breaks.
  ```
  ./gradlew :sharedLogic:compileDebugKotlinAndroid :sharedUI:compileDebugKotlinAndroid
  ./gradlew :sharedLogic:compileKotlinIosSimulatorArm64 :sharedUI:compileKotlinIosSimulatorArm64
  ```
- **Tests:** `./gradlew :sharedLogic:testDebugUnitTest` (main suite) and `./gradlew :sharedUI:testDebugUnitTest`. The aggregate `test` tasks do NOT accept `--tests`.
- **No em/en dashes in any member-facing copy.** Brand rule. This includes in-app strings. Use a colon, period, or comma.
- **Connection copy must never say "server error."** That is the originating bug.
- **No JVM-only APIs in `commonMain`** — no `String.format`, `java.*`, `Locale`. Use string templates and `padStart`.
- **Design system only.** Compose from `sharedUI/.../ui/` + `theme/` primitives. Never hand-roll `TextStyle`s or hex colors. Pad in 4dp increments, text in 2sp increments.
- **`AuthFlowRoot.kt` KEEP IN SYNC with `App.kt`.** `sharedUI/src/iosMain/.../shell/AuthFlowRoot.kt` mirrors App.kt's unauthenticated branches. This plan does not touch the signed-out flow (Auth/401 is out of scope per the design spec §2), so no mirror edit is expected — but if any task finds itself editing App.kt's unauthenticated branches, it must mirror into `AuthFlowRoot.kt` in the same task.
- **Regression inventory is a hard PR requirement.** Any user-facing behavior change updates `docs/regression/inventory.md` in the same branch (Task 8). `tools/regression/self_audit.sh` must print `FINDINGS: 0`.
- **Leave the working tree uncommitted.** No commit, no push, no PR until Cole says go. (The per-task "Commit" steps in the superpowers template are therefore replaced by "Verify" steps throughout this plan — do not commit.)

### Out of scope (decided, do not re-litigate)

- Auth / 401 / 403 / session expiry — the token-refresh interceptor owns it. ERR-07/08/09/10 unchanged.
- Token-refresh error behavior — unforceable in QA (`BLACKLIST_AFTER_ROTATION=False`, no `token_blacklist` app). Do not let a "while we're here" refresh change ride along untested.
- Form-field validation errors (Signup, Edit Profile, Concierge). ERR-16/17/18/19 unchanged.
- Dark variants — the app is locked light (`UIUserInterfaceStyle=Light` on iOS; `ArcanaTheme` installs `lightColorScheme`).
- Auto-retry of cold-start calls (old plan §4.2) — deferred. Rationale in Task 4's note.
- Retry "failed again" third state (old plan §4.3) — deferred, not required by any inventory entry or card.
- `StudioSelectionViewModel` load-error migration (ERR-14) — already has good structure and category-neutral copy.
- Profile's Ink-hero caption treatment (PROFILE-05/06) — intentional, a Stone block would clash.
- Server-side fault-injection middleware — an `arcana-server` PR (SUITE cards), not this branch.

---

## What changed since the 2026-07-18 plan

| Old plan said | Reality now |
|---|---|
| Files under `composeApp/src/...` | `composeApp` no longer exists: `:sharedLogic` + `:sharedUI` + `:androidApp` |
| Build with `:composeApp:installDebug` / `:composeApp:testDebugUnitTest` | `:androidApp:assembleDebug`, `:sharedLogic:testDebugUnitTest` |
| QA via Developer Settings base-URL levers | **Impossible on authenticated screens** — DevSettings is only reachable from the signed-out AuthScreen's 10-tap wordmark. Use the §QA recipes below. |
| `HttpTimeout` is "P0 alongside" | **P0-first.** SIGSTOP produces an indefinite hang on device; no error UI can fire for a stalled socket. |
| ERR-03, ERR-11, SCHED-02 unmentioned | In scope (Tasks 5 and 7). |
| Inventory update optional | Hard requirement, self-audit enforced. |
| iOS chrome is Compose | iOS chrome is native SwiftUI with per-tab NavHosts; content flows under a floating glass bar (`LocalFloatingBarInset`). |

---

## File Structure

**Create**
| File | Module | Responsibility |
|---|---|---|
| `networking/ErrorType.kt` | `:sharedLogic` commonMain | The classifier. `ErrorType` enum + `Throwable.toErrorType()` + `errorTypeForStatus()`. Compose-free. |
| `networking/ErrorTypeTest.kt` | `:sharedLogic` commonTest | Classifier tests incl. the timeout-exception lock. |
| `ui/ErrorState.kt` | `:sharedUI` commonMain | `FullScreenError`, `InlineError`, `RefreshFailedToast`, `RetryButton`, `RetryLink`, `ErrorCopy`. |

**Modify**
| File | Module | Change |
|---|---|---|
| `networking/ArcanaApiClient.kt` | `:sharedLogic` | Install `HttpTimeout`; per-request override for booking create/cancel. |
| `home/HomeViewModel.kt` | `:sharedLogic` | `Error(String)` → `Error(ErrorType)`; add `retry()`, refresh-failed signal. |
| `home/HomeScreen.kt` | `:sharedUI` | Caption → `FullScreenError` + `RefreshFailedToast`. |
| `schedule/ScheduleViewModel.kt` | `:sharedLogic` | `Error(ErrorType)`; ERR-03 per-day error state; SCHED-02 scope restore in `reload()`. |
| `schedule/ScheduleScreen.kt` | `:sharedUI` | Delete local `ErrorBlock`; render `FullScreenError` + per-day `InlineError`. |
| `schedule/ClassDetailViewModel.kt` + `ClassDetailScreen.kt` | both | `Error(ErrorType)`; delete local `ErrorBlock`. |
| `booking/MyBookingsViewModel.kt` + `MyBookingsScreen.kt` | both | `Error(ErrorType)`; Caption → `InlineError`. |
| `profile/ProfileViewModel.kt` | `:sharedLogic` | `Error(ErrorType)`; screen render unchanged (caption stays). |
| `booking/BookingViewModel.kt` + `booking/BookingCopy.kt` | `:sharedLogic` | ERR-11: stop collapsing network failures into `booking_failed`. Strip em dashes. |
| `docs/regression/inventory.md` | — | Rewrite Expected on ERR-01..06, ERR-11, ERR-12, ERR-20; add new entries. |

**Restore from `a5e02bd`** (docs, unchanged content): `docs/superpowers/specs/2026-07-18-error-states-overhaul-design.md`, `docs/error-states-design-brief.md`.

---

## Task 0: Restore design docs and salvage the reference files

**Files:**
- Create: `docs/superpowers/specs/2026-07-18-error-states-overhaul-design.md`
- Create: `docs/error-states-design-brief.md`

**Interfaces:**
- Produces: the on-disk design spec of record that Tasks 1 and 3 implement against. The Claude Design project URL returns 403 to tooling; these two files are the authoritative copy.

- [ ] **Step 1: Restore both docs from the WIP commit**

```bash
cd /Users/coletomlinson/Desktop/arcana/arcana-mobile
git show a5e02bd:docs/superpowers/specs/2026-07-18-error-states-overhaul-design.md \
  > docs/superpowers/specs/2026-07-18-error-states-overhaul-design.md
git show a5e02bd:docs/error-states-design-brief.md > docs/error-states-design-brief.md
```

- [ ] **Step 2: Verify they landed**

Run: `wc -l docs/superpowers/specs/2026-07-18-error-states-overhaul-design.md docs/error-states-design-brief.md`
Expected: `175` and `66` respectively.

- [ ] **Step 3: Append a status note to the spec**

Add to the very end of `docs/superpowers/specs/2026-07-18-error-states-overhaul-design.md`:

```markdown

---

## 11. Status update (2026-08-16)

Implemented on branch `feature/error-states-completion`, not on the original
`feature/error-states-overhaul` (which predates the `:sharedLogic`/`:sharedUI`
split and the iOS Liquid Glass shell). Paths in §6 above are stale — the
current file layout is in
`docs/superpowers/plans/2026-08-16-error-states-completion.md`.

§7.2's Developer Settings QA levers do not work on authenticated screens
(DevSettings is reachable only from the signed-out AuthScreen). The working
recipes are in that same plan's "QA recipes" section.

Scope grew by three device-observed defects: ERR-03 (uncached day-chip
failure renders nothing), ERR-11 (network failures conflated into
`booking_failed`), and SCHED-02 (favorites scope lost after a successful
RETRY).
```

---

## Task 1: The `ErrorType` classifier

**Files:**
- Create: `sharedLogic/src/commonMain/kotlin/org/arcana/mobile/networking/ErrorType.kt`
- Create: `sharedLogic/src/commonTest/kotlin/org/arcana/mobile/networking/ErrorTypeTest.kt`

**Interfaces:**
- Consumes: `org.arcana.mobile.analytics.apiRequestOutcome(statusCode: Int): String` (already in `:sharedLogic`, `analytics/ApiRequestMetrics.kt`).
- Produces:
  - `enum class ErrorType { CONNECTION, SERVER }`
  - `fun errorTypeForStatus(statusCode: Int): ErrorType`
  - `fun Throwable.toErrorType(): ErrorType`

  Every later task imports `org.arcana.mobile.networking.ErrorType` and calls `e.toErrorType()`.

- [ ] **Step 1: Write the failing test**

Create `sharedLogic/src/commonTest/kotlin/org/arcana/mobile/networking/ErrorTypeTest.kt`:

```kotlin
package org.arcana.mobile.networking

import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.utils.io.errors.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import org.arcana.mobile.analytics.apiRequestOutcome

class ErrorTypeTest {

    // NOTE: backtick test names must not contain commas OR colons — Kotlin/Native
    // rejects both ("Name contains illegal characters") and the whole commonTest
    // source set then fails to compile for iOS. Rephrase; do not just swap the
    // punctuation. (Colons additionally break the Android test compile.)
    @Test
    fun `status 0 means the request never completed so CONNECTION`() {
        assertEquals(ErrorType.CONNECTION, errorTypeForStatus(0))
    }

    @Test
    fun `5xx is SERVER`() {
        assertEquals(ErrorType.SERVER, errorTypeForStatus(500))
        assertEquals(ErrorType.SERVER, errorTypeForStatus(503))
    }

    @Test
    fun `unexpected non-auth 4xx is SERVER because the server did answer`() {
        assertEquals(ErrorType.SERVER, errorTypeForStatus(404))
        assertEquals(ErrorType.SERVER, errorTypeForStatus(409))
    }

    @Test
    fun `a plain exception never reached the server so CONNECTION`() {
        assertEquals(ErrorType.CONNECTION, Exception("boom").toErrorType())
        assertEquals(ErrorType.CONNECTION, IOException("socket closed").toErrorType())
    }

    // The reason Task 2 exists. HttpTimeout throws these, and neither is a
    // ResponseException, so both must land in CONNECTION: a timeout received
    // no HTTP response, so blaming the server would repeat the original bug.
    @Test
    fun `Ktor timeout exceptions are CONNECTION rather than SERVER`() {
        assertEquals(
            ErrorType.CONNECTION,
            HttpRequestTimeoutException("https://api.arcana.fit", 30_000L).toErrorType(),
        )
        assertEquals(
            ErrorType.CONNECTION,
            SocketTimeoutException("timed out").toErrorType(),
        )
    }

    // Guard: the UI category and the `api_request` telemetry outcome are
    // defined against the same buckets and must never drift apart.
    @Test
    fun `classifier agrees with apiRequestOutcome for every representative status`() {
        listOf(0, 200, 404, 409, 418, 500, 502, 503).forEach { status ->
            val expected =
                if (apiRequestOutcome(status) == "network_error") ErrorType.CONNECTION
                else ErrorType.SERVER
            assertEquals(expected, errorTypeForStatus(status), "status $status")
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :sharedLogic:testDebugUnitTest --tests '*ErrorTypeTest*'`
Expected: FAIL — compilation error, `ErrorType` / `errorTypeForStatus` / `toErrorType` unresolved.

- [ ] **Step 3: Write the implementation**

Create `sharedLogic/src/commonMain/kotlin/org/arcana/mobile/networking/ErrorType.kt` with exactly this content (salvaged verbatim from `a5e02bd`; the package is already correct for `:sharedLogic`):

```kotlin
package org.arcana.mobile.networking

import io.ktor.client.plugins.ResponseException
import org.arcana.mobile.analytics.apiRequestOutcome

/**
 * The member-facing category of a failed request. Two categories, because a
 * Member can only act on two things: their connection, or waiting for us.
 *
 * - [CONNECTION] — the request never got a valid answer from the server
 *   (offline, flaky, timeout, DNS). Copy talks about *their* connection and
 *   must never say "server error": the server may well be perfectly healthy.
 * - [SERVER] — the server answered badly (5xx, or an unexpected non-auth 4xx).
 *   Copy owns the fault.
 *
 * 401/403 never reach here in practice: the token-refresh interceptor handles
 * them upstream (see `refreshOutcomeForStatus`).
 */
enum class ErrorType { CONNECTION, SERVER }

/**
 * Pure status → category mapping, defined in terms of [apiRequestOutcome] so
 * the UI and the `api_request` telemetry event can never disagree about what
 * counts as a network failure. `0` means the request never completed.
 */
fun errorTypeForStatus(statusCode: Int): ErrorType =
    if (apiRequestOutcome(statusCode) == "network_error") ErrorType.CONNECTION else ErrorType.SERVER

/**
 * Classify any caught failure. A [ResponseException] means we received an HTTP
 * status, so the server answered: SERVER. Anything else (IO, timeout, transport
 * drop) never reached the server: CONNECTION.
 *
 * This is the single place that decision is made. Screens must not re-derive it
 * from exception messages.
 */
fun Throwable.toErrorType(): ErrorType = when (this) {
    is ResponseException -> errorTypeForStatus(response.status.value)
    else -> ErrorType.CONNECTION
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :sharedLogic:testDebugUnitTest --tests '*ErrorTypeTest*' --rerun-tasks`
Expected: PASS, 6 tests. Confirm from the output that the tests actually executed (not `UP-TO-DATE`).

**Note:** if `io.ktor.utils.io.errors.IOException` does not resolve on Ktor 3.1.2, use `kotlinx.io.IOException` instead — the triage note in the addendum §3.7 confirms Darwin connection-refused surfaces as `kotlinx.io.IOException`. Fix the import, do not delete the assertion.

- [ ] **Step 5: Compile both targets**

Run:
```bash
./gradlew :sharedLogic:compileDebugKotlinAndroid :sharedUI:compileDebugKotlinAndroid
./gradlew :sharedLogic:compileKotlinIosSimulatorArm64 :sharedUI:compileKotlinIosSimulatorArm64
```
Expected: BUILD SUCCESSFUL for both.

- [ ] **Step 6: Verify (do NOT commit)**

Run: `git status --short`
Expected: exactly two new untracked files under `sharedLogic/`. Leave them uncommitted.

---

## Task 2: `HttpTimeout` on `ArcanaApiClient` — the P0 ship-blocker

**Why this is first among the code changes:** SIGSTOPping the local server today produces an *indefinite* hang. Ktor's request never times out, so **no error state can fire at all** for a stalled socket, and none of Tasks 3-7's UI is reachable for that failure class. Independently confirmed on device three times in the 2026-08-11 run (learnings #17, #45, #75) and in prod telemetry (Felicia: `favorites` 89,549ms with `server_ms = 37ms`).

**Files:**
- Modify: `sharedLogic/src/commonMain/kotlin/org/arcana/mobile/networking/ArcanaApiClient.kt` (the `HttpClient { }` block, ~line 126)

**Interfaces:**
- Consumes: `ErrorType` from Task 1 (only via the test written there — no code dependency).
- Produces: no new public API. Behavior change only: a stalled request now throws `HttpRequestTimeoutException` / `SocketTimeoutException`, which Task 1's classifier already maps to `CONNECTION`.

- [ ] **Step 1: Read the current client block**

Run: `sed -n '120,150p' sharedLogic/src/commonMain/kotlin/org/arcana/mobile/networking/ArcanaApiClient.kt`
Expected: an `HttpClient { }` installing exactly three plugins — `ContentNegotiation`, `PerfTimingPlugin`, `Auth`. No `HttpTimeout`, no `engine { }`.

- [ ] **Step 2: Install `HttpTimeout`**

Add the import alongside the other `io.ktor.client.plugins` imports:

```kotlin
import io.ktor.client.plugins.HttpTimeout
```

Insert this as the **first** `install` inside the `HttpClient { }` block, above `install(ContentNegotiation)`:

```kotlin
        // Without this, a stalled socket hangs forever: Ktor applies no
        // app-level timeout and the platform engine defaults do not bound a
        // response that never finishes arriving. Observed in prod (a
        // `favorites` call took 89,549ms while the server answered in 37ms)
        // and on device (SIGSTOP on the API server hangs the app
        // indefinitely, then succeeds on SIGCONT).
        //
        // socketTimeout, NOT a tight requestTimeout: `booking_create` and
        // `booking_cancel` genuinely take up to ~6.5s SERVER-side, and an
        // aggressive overall timeout would kill real bookings. A socket
        // timeout fires only when no bytes flow for the window, so an
        // actively-transferring slow request is safe.
        //
        // These throw HttpRequestTimeoutException / SocketTimeoutException,
        // neither of which is a ResponseException, so `toErrorType()` maps
        // them to CONNECTION. That is correct: a timeout received no HTTP
        // response, so the copy must not blame the server.
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
            requestTimeoutMillis = 60_000
        }
```

- [ ] **Step 3: Give the booking calls extra headroom**

`booking_create` p95 is 5,560ms with real 6,546ms outliers, all server-side. Find `createBooking` and `cancelBooking` in the same file and add a per-request timeout override inside each request builder, immediately after the `url(...)`/method line:

```kotlin
            timeout {
                requestTimeoutMillis = 90_000
                socketTimeoutMillis = 60_000
            }
```

Add the import:

```kotlin
import io.ktor.client.plugins.timeout
```

If the per-request `timeout { }` extension does not resolve on Ktor 3.1.2 in `commonMain`, leave the global values in place and record the deviation in the plan's Deviations log at the bottom of this file rather than inventing an alternative. The global 60s request ceiling already clears every observed booking latency by ~9x.

- [ ] **Step 4: Compile both targets**

Run:
```bash
./gradlew :sharedLogic:compileDebugKotlinAndroid :sharedUI:compileDebugKotlinAndroid
./gradlew :sharedLogic:compileKotlinIosSimulatorArm64 :sharedUI:compileKotlinIosSimulatorArm64
```
Expected: BUILD SUCCESSFUL for both.

- [ ] **Step 5: Run the full suite (regression check)**

Run: `./gradlew :sharedLogic:testDebugUnitTest`
Expected: PASS. No existing test should break; this is additive.

- [ ] **Step 6: Confirm the plugin list**

Run: `grep -n "install(" sharedLogic/src/commonMain/kotlin/org/arcana/mobile/networking/ArcanaApiClient.kt`
Expected: four installs — `HttpTimeout`, `ContentNegotiation`, `PerfTimingPlugin`, `Auth`.

- [ ] **Step 7: Verify (do NOT commit)**

The on-device proof for this task is the SIGSTOP recipe in "QA recipes" below, run by Cole/the QA pass, not here.

---

## Task 3: The shared `ErrorState` UI family

**Files:**
- Create: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/ErrorState.kt`

**Interfaces:**
- Consumes: `org.arcana.mobile.networking.ErrorType` (Task 1); existing primitives `Overline`, `Heading2`, `BodyText` from `ui/Text.kt`, `DotMatrixLoaderCompact` from `ui/DotMatrixLoader.kt`, and theme tokens `Arcana`, `Ash`, `BurntNectar`, `Ink`, `Lime`, `Mist`, `Moss`, `Paper`, `Stone`.
- Produces, for Tasks 4-6:
  - `@Composable fun FullScreenError(type: ErrorType, onRetry: () -> Unit, modifier: Modifier = Modifier, retrying: Boolean = false)`
  - `@Composable fun InlineError(type: ErrorType, onRetry: () -> Unit, modifier: Modifier = Modifier, retrying: Boolean = false)`
  - `@Composable fun RefreshFailedToast(modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null, retrying: Boolean = false)`
  - `@Composable fun RetryButton(onClick: () -> Unit, modifier: Modifier = Modifier, retrying: Boolean = false, label: String = "Try again")`
  - `@Composable fun RetryLink(onClick: () -> Unit, modifier: Modifier = Modifier, retrying: Boolean = false, label: String = "Retry", color: Color = Moss)`
  - `internal object ErrorCopy` with `fullScreen(type)`, `inline(type)`, `const val REFRESH_FAILED`
  - `fun profileErrorCaption(type: ErrorType): String` — added by Task 6 Step 6, not by this task. Listed here so this file stays the single home for error copy.

- [ ] **Step 1: Confirm every primitive this file needs still exists on main**

Run:
```bash
grep -n "^fun Overline\|^fun Heading2\|^fun BodyText" sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/Text.kt
grep -n "fun DotMatrixLoaderCompact" sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/DotMatrixLoader.kt
grep -n "val Ash\|val Paper\|val Mist\|val Lime\|val Moss\|val Ink\|val Stone\|val BurntNectar" sharedUI/src/commonMain/kotlin/org/arcana/mobile/theme/AppColors.kt
```
Expected: all resolve. If any signature differs from what the salvaged file calls, adapt the call site — do not change the primitive.

- [ ] **Step 2: Create the file from the salvaged source**

```bash
git show a5e02bd:composeApp/src/commonMain/kotlin/org/arcana/mobile/ui/ErrorState.kt \
  > sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/ErrorState.kt
```

The package line (`package org.arcana.mobile.ui`) and every import are already correct for `:sharedUI` — the package path did not change in the split, only the module. Read the file end to end after copying and confirm that is true.

- [ ] **Step 3: Add the iOS floating-bar note to the `FullScreenError` KDoc**

The iOS shell now floats a native glass tab bar over tab-root content. Append this paragraph to `FullScreenError`'s KDoc block (immediately above `@Composable fun FullScreenError`):

```kotlin
 *
 * iOS note: inside a tab root this renders under the floating native tab bar.
 * The block is deliberately lower-third but bottom-bounded by its own 44dp
 * bottom padding plus a trailing weight(1f) spacer, so the retry button sits
 * clear of the bar. If a caller places this in a tab root that also applies
 * `LocalFloatingBarInset`, do not double-apply it here.
```

- [ ] **Step 4: Verify the copy has no em/en dashes**

Run: `grep -n '[—–]' sharedUI/src/commonMain/kotlin/org/arcana/mobile/ui/ErrorState.kt`
Expected: **no matches inside any string literal.** Matches inside KDoc comments are fine (the brand rule covers member-facing copy, not code comments). If a string literal contains one, replace it with a colon, period, or comma.

- [ ] **Step 5: Compile both targets**

Run:
```bash
./gradlew :sharedLogic:compileDebugKotlinAndroid :sharedUI:compileDebugKotlinAndroid
./gradlew :sharedLogic:compileKotlinIosSimulatorArm64 :sharedUI:compileKotlinIosSimulatorArm64
```
Expected: BUILD SUCCESSFUL for both. `ErrorState.kt` has no call sites yet, so this only proves it compiles — that is the point of the gate.

- [ ] **Step 6: Verify (do NOT commit)**

---

## Task 4: Migrate Home (ERR-05)

The regression run's headline finding: Home renders a single inline `server error` Caption with **no retry at all** on a connection failure, identical on all three devices. This task is the direct fix.

**Files:**
- Modify: `sharedLogic/src/commonMain/kotlin/org/arcana/mobile/home/HomeViewModel.kt` (state type ~line 25, catch ~line 71-75)
- Modify: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/home/HomeScreen.kt` (`is HomeUiState.Error` branch, ~line 185)
- Modify: `sharedLogic/src/commonTest/kotlin/org/arcana/mobile/booking/HomeViewModelTest.kt`

**Interfaces:**
- Consumes: `ErrorType`, `toErrorType()` (Task 1); `FullScreenError`, `RefreshFailedToast` (Task 3).
- Produces:
  - `HomeUiState.Error(val type: ErrorType)` — replaces `Error(val message: String)`
  - `HomeViewModel.retry()` — public, re-runs the cold load
  - `HomeViewModel.refreshFailed: StateFlow<Boolean>` — true when a refresh failed while `Success` content is on screen; cleared by a successful load or by `dismissRefreshFailed()`
  - `HomeViewModel.dismissRefreshFailed()`

- [ ] **Step 1: Read the current load path**

Run: `sed -n '1,110p' sharedLogic/src/commonMain/kotlin/org/arcana/mobile/home/HomeViewModel.kt`

Note whether `load()` is called from `init` only, or is also public. The refresh path must be distinguishable from the cold path — if there is only one entry point, add a `private suspend fun load(isRefresh: Boolean)` and keep the public surface thin.

- [ ] **Step 2: Write the failing tests**

Add to `sharedLogic/src/commonTest/kotlin/org/arcana/mobile/booking/HomeViewModelTest.kt` (keep the existing tests; this file already has fakes that inject failures):

```kotlin
    @Test
    fun `a network failure classifies as CONNECTION, never as a server error`() = runTest {
        val vm = HomeViewModel(
            membershipApi = FailingMembershipApi(Exception("network failure")),
            bookingApi = FakeBookingApi(),
        )
        advanceUntilIdle()
        assertEquals(HomeUiState.Error(ErrorType.CONNECTION), vm.uiState.value)
    }

    @Test
    fun `a 5xx classifies as SERVER`() = runTest {
        val vm = HomeViewModel(
            membershipApi = FailingMembershipApi(serverException(503)),
            bookingApi = FakeBookingApi(),
        )
        advanceUntilIdle()
        assertEquals(HomeUiState.Error(ErrorType.SERVER), vm.uiState.value)
    }

    @Test
    fun `retry re-runs the load and reaches Success once the API recovers`() = runTest {
        val api = FlakyMembershipApi(failuresBeforeSuccess = 1)
        val vm = HomeViewModel(membershipApi = api, bookingApi = FakeBookingApi())
        advanceUntilIdle()
        assertEquals(HomeUiState.Error(ErrorType.CONNECTION), vm.uiState.value)

        vm.retry()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is HomeUiState.Success)
    }

    @Test
    fun `a refresh failure keeps Success content and raises refreshFailed`() = runTest {
        val api = FlakyMembershipApi(failuresBeforeSuccess = 0)
        val vm = HomeViewModel(membershipApi = api, bookingApi = FakeBookingApi())
        advanceUntilIdle()
        assertTrue(vm.uiState.value is HomeUiState.Success)

        api.failNext = true
        vm.refresh()
        advanceUntilIdle()

        // The member keeps their last-good content; the failure is a notice,
        // not a takeover. This is the behavior the old caption silently lost.
        assertTrue(vm.uiState.value is HomeUiState.Success)
        assertTrue(vm.refreshFailed.value)
    }
```

Match the existing test file's fake-construction style exactly — read it first and reuse its helpers rather than inventing `FailingMembershipApi` / `FlakyMembershipApi` if equivalents already exist. If they do not exist, add them to the same file, minimal:

```kotlin
private class FailingMembershipApi(private val error: Throwable) : MembershipApi {
    override suspend fun membershipMe(): MembershipMeDto = throw error
}

private class FlakyMembershipApi(failuresBeforeSuccess: Int) : MembershipApi {
    private var remainingFailures = failuresBeforeSuccess
    var failNext = false
    override suspend fun membershipMe(): MembershipMeDto {
        if (remainingFailures > 0) { remainingFailures--; throw Exception("network failure") }
        if (failNext) { failNext = false; throw Exception("network failure") }
        return sampleMembership()
    }
}
```

For `serverException(503)`, build a real Ktor `ResponseException` the same way `ErrorTypeTest` does, or reuse an existing helper if the suite already has one.

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :sharedLogic:testDebugUnitTest --tests '*HomeViewModelTest*'`
Expected: FAIL — `HomeUiState.Error` takes a `String`, `retry()` and `refreshFailed` are unresolved.

- [ ] **Step 4: Change the state type and the catch**

In `HomeViewModel.kt`:

```kotlin
    data class Error(val type: ErrorType) : HomeUiState
```

Replace the catch body (currently `_uiState.value = HomeUiState.Error("server error")`) with:

```kotlin
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logWarning("HomeViewModel", e.message ?: "load failed")
            if (_uiState.value is HomeUiState.Success) {
                // Content is already good: a failed refresh must not wipe it.
                // Surface a dismissible notice instead of a takeover.
                _refreshFailed.value = true
            } else {
                _uiState.value = HomeUiState.Error(e.toErrorType())
            }
        }
```

Preserve whatever guard the existing catch has around `_uiState.value` (read it in Step 1 — do not drop an existing condition). Add:

```kotlin
    private val _refreshFailed = MutableStateFlow(false)
    val refreshFailed: StateFlow<Boolean> = _refreshFailed

    /** Error-state retry. Clears back to Loading so the retry button can show
     *  its in-flight state, then re-runs the cold load. */
    fun retry() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            load()
        }
    }

    fun dismissRefreshFailed() { _refreshFailed.value = false }
```

and clear `_refreshFailed.value = false` on every successful load.

Add imports: `org.arcana.mobile.networking.ErrorType`, `org.arcana.mobile.networking.toErrorType`.

- [ ] **Step 5: Render the new UI**

In `HomeScreen.kt`, replace the `is HomeUiState.Error ->` branch (currently an `item { Caption(...) }`) with:

```kotlin
            is HomeUiState.Error -> {
                item {
                    FullScreenError(
                        type = s.type,
                        onRetry = viewModel::retry,
                        retrying = false,
                        modifier = Modifier.fillParentMaxSize(),
                    )
                }
            }
```

`fillParentMaxSize()` is the `LazyListScope` item modifier that makes a single item fill the viewport — required because Home's error branch lives inside a `LazyColumn`. If the surrounding container is not a `LazyColumn`, use `Modifier.fillMaxSize()` instead.

Add the refresh-failed toast. Find Home's top-level container (the `Box`/`Scaffold` wrapping the `LazyColumn`) and overlay:

```kotlin
    val refreshFailed by viewModel.refreshFailed.collectAsStateWithLifecycle()
    if (refreshFailed) {
        RefreshFailedToast(
            onRetry = {
                viewModel.dismissRefreshFailed()
                viewModel.refresh()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeHorizontalPadding()
                .padding(bottom = 16.dp + LocalFloatingBarInset.current),
        )
    }
```

`LocalFloatingBarInset` (`ui/FloatingBarInset.kt`) is 0 on Android and non-zero on iOS, where the native glass tab bar floats over content. Without it the toast sits under the bar on iOS. Import `org.arcana.mobile.ui.LocalFloatingBarInset`.

Add imports: `org.arcana.mobile.ui.FullScreenError`, `org.arcana.mobile.ui.RefreshFailedToast`.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :sharedLogic:testDebugUnitTest --tests '*HomeViewModelTest*' --rerun-tasks`
Expected: PASS, including the four new tests.

- [ ] **Step 7: Compile both targets**

Run:
```bash
./gradlew :sharedLogic:compileDebugKotlinAndroid :sharedUI:compileDebugKotlinAndroid
./gradlew :sharedLogic:compileKotlinIosSimulatorArm64 :sharedUI:compileKotlinIosSimulatorArm64
```
Expected: BUILD SUCCESSFUL for both.

- [ ] **Step 8: Record the tab-re-entry finding**

The addendum §3.2 raises an unresolved question: ERR-05's Expected claims "leaving and re-entering the Home tab re-triggers the load," but learning #109 could not rely on it, and on iOS tab compositions persist across switches so `LaunchedEffect`s do not re-run.

Read `HomeViewModel`'s construction site and answer, in a comment at the top of `HomeViewModel.load()`:

```kotlin
    // Tab re-entry does NOT reliably re-run this: the ViewModel is retained
    // for the session (Android: session-scoped ViewModelStore in App.kt; iOS:
    // per-tab controllers whose compositions persist across TabView switches),
    // so `load()` runs once per VM instance, not once per tab visit. That is
    // why the error state needs its own retry() rather than relying on the
    // member navigating away and back.
```

Verify that claim against the actual code before writing it. If the code shows otherwise, write what is true instead, and note the correction in the Deviations log at the bottom of this plan. This sentence becomes ERR-05's rewritten Expected in Task 8.

**Deferred here, deliberately:** the cold-start auto-retry (old plan §4.2) and Home's missing resume-refresh (CLASS-25). Auto-retry would mask exactly the failure this task makes visible, and it needs its own telemetry to tune; CLASS-25 is a separate card explicitly marked unrelated. Neither is a reason to widen this PR.

- [ ] **Step 9: Verify (do NOT commit)**

---

## Task 5: Migrate Schedule + fix ERR-03 and SCHED-02

Three changes in one task because all three live inside the same refetch/retry plumbing and a reviewer cannot sensibly accept one and reject another.

**Files:**
- Modify: `sharedLogic/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleViewModel.kt` (state ~line 122, `reload()` ~274, `refetchForFilters` catches ~601-624, `applyRefetchFailure` ~630, `ensureSelectedDayLoaded` ~641)
- Modify: `sharedUI/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleScreen.kt` (error branch ~line 225, local `ErrorBlock` ~259, `!dayLoaded` branch ~490)
- Modify/Create: `sharedLogic/src/commonTest/kotlin/org/arcana/mobile/schedule/ScheduleViewModelTest.kt`

**Interfaces:**
- Consumes: `ErrorType`, `toErrorType()` (Task 1); `FullScreenError`, `InlineError` (Task 3).
- Produces:
  - `ScheduleUiState.Error(val type: ErrorType)` — replaces `Error(val message: String)`
  - `ScheduleUiState.Success.dayError: ErrorType?` — non-null when the selected day's page fetch failed (ERR-03)
  - `ScheduleViewModel.retryDay()` — re-attempts the selected day's page-1 fetch

### 5a — State type and refetch classification

- [ ] **Step 1: Write the failing test**

```kotlin
    @Test
    fun `cold-start network failure classifies as CONNECTION`() = runTest {
        val vm = scheduleViewModel(api = FailingScheduleApi(Exception("network failure")))
        advanceUntilIdle()
        assertEquals(ScheduleUiState.Error(ErrorType.CONNECTION), vm.uiState.value)
    }

    @Test
    fun `cold-start 5xx classifies as SERVER`() = runTest {
        val vm = scheduleViewModel(api = FailingScheduleApi(serverException(500)))
        advanceUntilIdle()
        assertEquals(ScheduleUiState.Error(ErrorType.SERVER), vm.uiState.value)
    }

    @Test
    fun `refetch failure with content on screen keeps the content`() = runTest {
        val api = FlakyScheduleApi()
        val vm = scheduleViewModel(api = api)
        advanceUntilIdle()
        assertTrue(vm.uiState.value is ScheduleUiState.Success)

        api.failNext = true
        vm.onScopeChanged(ScopeMode.AllStudios)
        advanceUntilIdle()

        // The staleness guard is the behavior most at risk in this refactor.
        assertTrue(vm.uiState.value is ScheduleUiState.Success)
    }
```

Reuse the existing test file's VM-construction helper if one exists; read the file first.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :sharedLogic:testDebugUnitTest --tests '*ScheduleViewModelTest*'`
Expected: FAIL — `Error` takes a `String`.

- [ ] **Step 3: Implement**

Change the state:

```kotlin
    data class Error(val type: ErrorType) : ScheduleUiState
```

Change `applyRefetchFailure` to take the category:

```kotlin
    /** Cold-start (or error-retry) failure → full-screen Error; failure with
     *  content already on screen → keep the content, just stop the dim. */
    private fun applyRefetchFailure(type: ErrorType) {
        if (_uiState.value is ScheduleUiState.Success) {
            refreshingFilters = false
            publish()
        } else {
            _uiState.value = ScheduleUiState.Error(type)
        }
    }
```

Update both call sites in `refetchForFilters`: `applyRefetchFailure("server error $code")` → `applyRefetchFailure(e.toErrorType())`, and `applyRefetchFailure("server error")` → `applyRefetchFailure(e.toErrorType())`. Keep the `if (generation == fetchGeneration)` guard and the `telemetry.screenLoadCompleted(...)` calls exactly as they are — the staleness rule and the telemetry are not part of this change.

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :sharedLogic:testDebugUnitTest --tests '*ScheduleViewModelTest*' --rerun-tasks`
Expected: PASS.

### 5b — SCHED-02: restore Favorites scope on retry

`reload()` calls `refetchForFilters()` directly and never re-runs favorites determination. When the cold-start outage killed both fetches, `favoritesRepository.refresh()` returned null and `scope` stayed at its `AllStudios` default. The member retries, the schedule loads, and their Favorites scope has silently vanished.

- [ ] **Step 5: Write the failing test**

```kotlin
    @Test
    fun `retry after a failed favorites fetch restores Favorites scope`() = runTest {
        // Both fetches fail on cold start, exactly as one outage does.
        val favorites = FlakyFavoritesRepository(failFirst = true)
        val api = FlakyScheduleApi(failuresBeforeSuccess = 1)
        val vm = scheduleViewModel(api = api, favoritesRepository = favorites)
        advanceUntilIdle()
        assertTrue(vm.uiState.value is ScheduleUiState.Error)

        vm.reload()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is ScheduleUiState.Success)
        // Without the fix this is AllStudios: the member's scope is silently
        // dropped by the very retry that appears to succeed.
        assertEquals(ScopeMode.Favorites, state.scope)
        assertTrue(state.favoritesKnown)
    }
```

`FlakyFavoritesRepository` returns null on the first `refresh()` and a non-empty `FavoritesDto` thereafter. Model it on the existing fake in the test file if one exists.

- [ ] **Step 6: Run to verify it fails**

Run: `./gradlew :sharedLogic:testDebugUnitTest --tests '*ScheduleViewModelTest*'`
Expected: FAIL — `AllStudios` expected `Favorites`.

- [ ] **Step 7: Implement**

```kotlin
    /** Full re-fetch with the shimmer placeholder — error-retry path.
     *
     *  Re-runs favorites determination first when favorites are still unknown:
     *  the outage that failed the schedule fetch almost always failed the
     *  favorites fetch too, leaving `scope` at its AllStudios default. Without
     *  this, a successful retry silently drops the member's Favorites scope
     *  (SCHED-02). Guarded on `favorites == null` so a member who deliberately
     *  switched to All Studios is never overridden. */
    fun reload() {
        viewModelScope.launch {
            _uiState.value = ScheduleUiState.Loading
            if (favoritesRepository.favorites.value == null) {
                val favorites = favoritesRepository.refresh()
                if (favorites != null && !favorites.isEmpty()) scope = ScopeMode.Favorites
                lastAppliedFavorites = favorites
            }
            refetchForFilters("cold_start")
        }
    }
```

- [ ] **Step 8: Run to verify it passes**

Run: `./gradlew :sharedLogic:testDebugUnitTest --tests '*ScheduleViewModelTest*' --rerun-tasks`
Expected: PASS.

### 5c — ERR-03: the silent uncached day-chip failure

Today `ensureSelectedDayLoaded`'s catch only `logWarning`s, so the day area sits on the `DotMatrixLoader` placeholder forever, visually identical to "still loading." Two devices confirmed literally zero UI. A trained regression driver misread it as an app freeze (learning #22).

Treatment: a per-day `InlineError` **inside the day's list area**, with a retry. `FullScreenError` is wrong (other days' content exists); a toast is wrong (it would leave the placeholder spinning, which is the actual bug).

- [ ] **Step 9: Write the failing test**

```kotlin
    @Test
    fun `an uncached day-chip fetch failure surfaces a day error instead of spinning forever`() = runTest {
        val api = FlakyScheduleApi()
        val vm = scheduleViewModel(api = api)
        advanceUntilIdle()
        assertTrue(vm.uiState.value is ScheduleUiState.Success)

        api.failNext = true
        vm.onDaySelected(vm.uiState.value.let { (it as ScheduleUiState.Success).days[3].date })
        advanceUntilIdle()

        val state = vm.uiState.value as ScheduleUiState.Success
        assertEquals(ErrorType.CONNECTION, state.dayError)
    }

    @Test
    fun `retryDay clears the day error and loads the day`() = runTest {
        val api = FlakyScheduleApi()
        val vm = scheduleViewModel(api = api)
        advanceUntilIdle()
        api.failNext = true
        val target = (vm.uiState.value as ScheduleUiState.Success).days[3].date
        vm.onDaySelected(target)
        advanceUntilIdle()
        assertEquals(ErrorType.CONNECTION, (vm.uiState.value as ScheduleUiState.Success).dayError)

        vm.retryDay()
        advanceUntilIdle()

        val state = vm.uiState.value as ScheduleUiState.Success
        assertEquals(null, state.dayError)
        assertTrue(state.sessions.isNotEmpty())
    }
```

Adapt `days[3].date` / `state.sessions` to the actual `Success` field names — read the `Success` data class (around line 90-110) first.

- [ ] **Step 10: Run to verify it fails**

Expected: FAIL — `dayError` and `retryDay` unresolved.

- [ ] **Step 11: Implement**

Add a backing field near the other per-day state (`dayStates`, `loadingDays`):

```kotlin
    /** Non-null when the SELECTED day's page-1 fetch failed. Keyed implicitly
     *  by selectedDate: cleared on every day switch, filter change and refetch,
     *  so it can never describe a day the member is no longer looking at. */
    private var dayError: ErrorType? = null
```

Add `val dayError: ErrorType?` to the `Success` data class, and set it in `publish()` from the backing field (find `publish()` and add the field to the `Success(...)` construction alongside `favoritesKnown` etc.).

Replace `ensureSelectedDayLoaded`'s catch:

```kotlin
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logWarning("ScheduleViewModel", e.message ?: "day page fetch failed")
                // Do not leave the day on its loading placeholder: that is
                // indistinguishable from a day still loading, and there is no
                // timeout behind it, so it can persist indefinitely (ERR-03).
                if (generation == fetchGeneration && date == selectedDate) {
                    dayError = e.toErrorType()
                    publish()
                }
            } finally {
                loadingDays.remove(date)
            }
```

Clear it at the head of the `try` and wherever the day/filters change:

```kotlin
        if (dayError != null) { dayError = null }
```

Concretely: clear in `ensureSelectedDayLoaded` before the fetch, and in `selectDay` (the day-switch entry point) before its own `publish()`.

**Do NOT clear unconditionally at the top of `refetchForFilters`.** That looks right and is wrong: `dayStates` is only rebuilt inside the atomic apply, which never runs when the refetch fails. So a pull-to-refresh or filter tap during a persisting outage clears the day error, fails, takes `applyRefetchFailure`'s content-keeping branch, and publishes a state with `dayError == null` *and* `dayLoaded == false` — dropping the day straight back onto the bare `DotMatrixLoader` with no fetch in flight. That is ERR-03 reintroduced, reachable in one ordinary gesture.

Instead, re-attach the error in `applyRefetchFailure`'s content-keeping branch, before `publish()`:

```kotlin
if (dayStates[selectedDate]?.loaded != true) dayError = type
```

This preserves the staleness contract exactly (content kept, dim cleared, cold start still becomes `Error`) while guaranteeing the day area never shows a placeholder with nothing in flight behind it. Also clear `dayError` in `onFiltersChanged()` so the error does not describe the previous filter set during the ~250ms debounce window.

Add a test for it: day error → refetch fails → assert the day is not left on a bare placeholder.

Add the retry:

```kotlin
    /** Retry just the selected day's page-1 fetch (ERR-03's recovery path). */
    fun retryDay() {
        dayError = null
        publish()
        ensureSelectedDayLoaded()
    }
```

- [ ] **Step 12: Run to verify it passes**

Run: `./gradlew :sharedLogic:testDebugUnitTest --tests '*ScheduleViewModelTest*' --rerun-tasks`
Expected: PASS, all Schedule tests.

### 5d — Screen wiring

- [ ] **Step 13: Replace the local `ErrorBlock` and render the day error**

In `ScheduleScreen.kt`:

Line ~225: `is ScheduleUiState.Error -> ErrorBlock(message = s.message, onRetry = viewModel::reload)` becomes:

```kotlin
            is ScheduleUiState.Error -> FullScreenError(
                type = s.type,
                onRetry = viewModel::reload,
            )
```

Delete the private `ErrorBlock` composable entirely (~line 259 to the end of that function) and remove any imports it alone used.

At the `!dayLoaded` branch (~line 490), the loader must now yield to the error when one exists:

```kotlin
        val dayError = state.dayError
        if (dayError != null) {
            InlineError(
                type = dayError,
                onRetry = viewModel::retryDay,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            )
        } else if (!dayLoaded) {
            // existing DotMatrixLoader placeholder, unchanged
        }
```

Match the surrounding scope: if this sits inside a `LazyListScope`, wrap each branch in `item { }`. Read the existing code before editing.

Add imports: `org.arcana.mobile.ui.FullScreenError`, `org.arcana.mobile.ui.InlineError`.

- [ ] **Step 14: Confirm no "server error" strings remain in Schedule**

Run: `grep -rn '"server error' sharedLogic/src/commonMain sharedUI/src/commonMain`
Expected: no matches in Schedule files. Remaining matches in ClassDetail/MyBookings/Profile are Task 6's.

- [ ] **Step 15: Compile both targets**

Run:
```bash
./gradlew :sharedLogic:compileDebugKotlinAndroid :sharedUI:compileDebugKotlinAndroid
./gradlew :sharedLogic:compileKotlinIosSimulatorArm64 :sharedUI:compileKotlinIosSimulatorArm64
```
Expected: BUILD SUCCESSFUL for both.

- [ ] **Step 16: Verify (do NOT commit)**

---

## Task 6: Migrate Class Detail, My Bookings, and Profile

Grouped: three mechanical, identical migrations with no cross-dependencies and one shared compile gate.

**Files:**
- Modify: `sharedLogic/.../schedule/ClassDetailViewModel.kt` (state ~18, catches ~81-93) + `sharedUI/.../schedule/ClassDetailScreen.kt`
- Modify: `sharedLogic/.../booking/MyBookingsViewModel.kt` (state ~15, catches ~28-31) + `sharedUI/.../booking/MyBookingsScreen.kt`
- Modify: `sharedLogic/.../profile/ProfileViewModel.kt` (state ~32, catch ~96-100)
- Modify: `sharedLogic/src/commonTest/.../profile/ProfileViewModelTest.kt`

**Interfaces:**
- Consumes: `ErrorType`, `toErrorType()` (Task 1); `FullScreenError`, `InlineError` (Task 3).
- Produces: `ClassDetailUiState.Error(val type: ErrorType)`, `MyBookingsUiState.Error(val type: ErrorType)`, `ProfileUiState.Error(val type: ErrorType)`.

- [ ] **Step 1: Write the failing tests**

Add to `ProfileViewModelTest.kt`:

```kotlin
    @Test
    fun `a network failure classifies as CONNECTION`() = runTest {
        val vm = ProfileViewModel(profileApi = FailingProfileApi(Exception("network failure")))
        advanceUntilIdle()
        assertEquals(ProfileUiState.Error(ErrorType.CONNECTION), vm.uiState.value)
    }

    @Test
    fun `a 5xx classifies as SERVER`() = runTest {
        val vm = ProfileViewModel(profileApi = FailingProfileApi(serverException(502)))
        advanceUntilIdle()
        assertEquals(ProfileUiState.Error(ErrorType.SERVER), vm.uiState.value)
    }
```

Adapt the constructor to `ProfileViewModel`'s real signature — read it first, and reuse the existing fakes in that file.

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :sharedLogic:testDebugUnitTest --tests '*ProfileViewModelTest*'`
Expected: FAIL — `Error` takes a `String`.

- [ ] **Step 3: Migrate all three ViewModels**

Each: change `data class Error(val message: String)` → `data class Error(val type: ErrorType)`, and replace every `Error("server error")` / `Error("server error $code")` with `Error(e.toErrorType())`. The separate `ResponseException` and generic `Exception` catch branches **collapse into one** now that the classifier does the discrimination — but keep any `catch (e: CancellationException) { throw e }` branch first, and keep every existing `logWarning` call.

`ClassDetailViewModel` catches (~81-93) become:

```kotlin
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logWarning("ClassDetailViewModel", e.message ?: "class load failed")
            _uiState.value = ClassDetailUiState.Error(e.toErrorType())
        }
```

`MyBookingsViewModel` catches (~28-31) become:

```kotlin
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = MyBookingsUiState.Error(e.toErrorType())
            }
```

`ProfileViewModel`'s catch (~96-100): swap the `Error("server error")` line for `Error(e.toErrorType())`, leaving the surrounding structure alone.

Add imports to each: `org.arcana.mobile.networking.ErrorType`, `org.arcana.mobile.networking.toErrorType`.

- [ ] **Step 4: Wire Class Detail's screen**

`ClassDetailScreen.kt` renders its own local `ErrorBlock` under the close bar. Replace the `ClassDetailUiState.Error` render with `FullScreenError(type = s.type, onRetry = viewModel::reload)` **keeping it below the existing top/close bar** so the X stays functional (ERR-04's verified behavior — do not regress it). Delete the now-unused local `ErrorBlock` composable and its orphaned imports.

- [ ] **Step 5: Wire My Bookings' screen**

`MyBookingsScreen.kt` renders a single burnt-nectar `Caption`. Replace with the inline card, under the "YOUR BOOKINGS" header, not full-screen:

```kotlin
            is MyBookingsUiState.Error -> InlineError(
                type = s.type,
                onRetry = viewModel::reload,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            )
```

If `MyBookingsViewModel` has no public reload/retry, add one following Home's `retry()` shape from Task 4.

- [ ] **Step 6: Leave Profile's screen alone**

`ProfileViewModel`'s *state type* changes but `ProfileScreen`'s render does not: the small caption on the Ink hero is intentional (a Stone error block would clash with the dark hero). Update only the render's message source — where it read `s.message`, it now needs a string. Use:

```kotlin
                is ProfileUiState.Error -> Caption(
                    text = profileErrorCaption(s.type),
                    ...
                )
```

and add to `ErrorState.kt` (Task 3's file), as a public helper next to `ErrorCopy`:

```kotlin
/** Profile keeps its small caption on the Ink hero rather than an error block
 *  (a Stone block would clash with the dark hero). It still must not say
 *  "server error" for a connection failure, so the copy routes through the
 *  same categories as everything else. Not @Composable: it is a pure
 *  ErrorType -> String mapping, same as the rest of [ErrorCopy]. */
fun profileErrorCaption(type: ErrorType): String = when (type) {
    ErrorType.CONNECTION -> "Couldn't reach Arcana."
    ErrorType.SERVER -> "Couldn't load your profile."
}
```

- [ ] **Step 7: Run the tests**

Run: `./gradlew :sharedLogic:testDebugUnitTest --rerun-tasks`
Expected: PASS, whole suite. Fix any existing test still asserting on `"server error"` strings by switching it to the `ErrorType` assertion.

- [ ] **Step 8: Confirm the strings are gone**

Run: `grep -rn '"server error' sharedLogic/src sharedUI/src`
Expected: **no matches at all**, in any module, outside explanatory comments.

- [ ] **Step 9: Compile both targets**

Run:
```bash
./gradlew :sharedLogic:compileDebugKotlinAndroid :sharedUI:compileDebugKotlinAndroid
./gradlew :sharedLogic:compileKotlinIosSimulatorArm64 :sharedUI:compileKotlinIosSimulatorArm64
```
Expected: BUILD SUCCESSFUL for both.

- [ ] **Step 10: Verify (do NOT commit)**

---

## Task 7: ERR-11 — stop conflating network failures with booking failures

`BookingViewModel` maps a *network* exception to the `booking_failed` code, so a connection failure and an unknown server failure produce **identical** copy ("We couldn't book that. Try again in a moment.") on the single most important action in the app. That is precisely the CONNECTION/SERVER conflation this whole branch exists to eliminate.

**Scope note:** this task fixes the classification and the copy. It does **not** add a dedicated RETRY affordance to the booking sheet, the cancel sheet, or Studio Selection's save. The Trello card "ERR-11 · Booking/cancel/save failures have no dedicated retry" covers both; the retry-affordance half needs Cole's design call and is flagged for him at review, not decided here.

**Files:**
- Modify: `sharedLogic/src/commonMain/kotlin/org/arcana/mobile/booking/BookingCopy.kt`
- Modify: `sharedLogic/src/commonMain/kotlin/org/arcana/mobile/booking/BookingViewModel.kt`
- Modify/Create: `sharedLogic/src/commonTest/kotlin/org/arcana/mobile/booking/BookingViewModelTest.kt`

**Interfaces:**
- Consumes: `ErrorType`, `toErrorType()` (Task 1).
- Produces: two new reason codes routed through the existing `bookingErrorCopy(code: String)` seam — `"connection_failed"` and `"server_failed"` — so `BookingSubmit.Failed(code)` and `CancelState.Failed(code)` need no shape change and `BookingSheet.kt` needs no edit.

- [ ] **Step 1: Write the failing tests**

```kotlin
    @Test
    fun `a network failure is not reported as a generic booking failure`() = runTest {
        val vm = bookingViewModel(api = FailingBookingApi(Exception("network failure")))
        vm.confirmBooking()
        advanceUntilIdle()
        assertEquals(BookingSubmit.Failed("connection_failed"), vm.submitState.value)
    }

    @Test
    fun `a 5xx is reported as a server failure`() = runTest {
        val vm = bookingViewModel(api = FailingBookingApi(serverException(500)))
        vm.confirmBooking()
        advanceUntilIdle()
        assertEquals(BookingSubmit.Failed("server_failed"), vm.submitState.value)
    }

    @Test
    fun `a typed server reason code still wins over the transport category`() = runTest {
        val vm = bookingViewModel(api = FailingBookingApi(BookingError("session_full")))
        vm.confirmBooking()
        advanceUntilIdle()
        // The server told us WHY. That is strictly more useful than "server".
        assertEquals(BookingSubmit.Failed("session_full"), vm.submitState.value)
    }

    @Test
    fun `connection and server booking copy are distinct and neither blames the wrong party`() {
        val connection = bookingErrorCopy("connection_failed")
        val server = bookingErrorCopy("server_failed")
        assertNotEquals(connection, server)
        assertFalse(connection.contains("our end"))
        assertFalse(connection.contains("server", ignoreCase = true))
    }

    @Test
    fun `no booking copy contains an em or en dash`() {
        val codes = listOf(
            "session_full", "credits_exhausted", "already_booked", "class_cancelled",
            "time_conflict", "spot_required", "spot_unavailable",
            "invalid_spot_preference", "session_outside_window", "no_active_payment",
            "payment_past_due", "booking_busy", "connection_failed", "server_failed",
            "cancel_failed", "unknown_code",
        )
        codes.forEach { code ->
            val copy = bookingErrorCopy(code)
            assertFalse(copy.contains('—'), "em dash in copy for $code: $copy")
            assertFalse(copy.contains('–'), "en dash in copy for $code: $copy")
        }
    }
```

Adapt `bookingViewModel(...)` / `FailingBookingApi` to the existing fakes in the booking test suite — read it first.

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :sharedLogic:testDebugUnitTest --tests '*BookingViewModelTest*'`
Expected: FAIL — the network case yields `booking_failed`, and the dash test fails on `spot_unavailable` and `booking_busy`.

- [ ] **Step 3: Add the two codes and strip the dashes**

In `BookingCopy.kt`, add two cases above the `else` branch and fix the three em dashes:

```kotlin
    "spot_unavailable" -> "That spot was just taken. Pick another."
    "invalid_spot_preference" -> "That option isn't available. Pick another."
    ...
    "booking_busy" -> "We're a little busy. Try that again."
    // Transport-level failures, kept distinct from each other and from the
    // generic fallback: telling a member "try again in a moment" when their
    // phone is offline sends them to wait on a server that is perfectly fine.
    "connection_failed" -> "Couldn't reach Arcana. Check your connection and try again."
    "server_failed" -> "Something went wrong on our end. Try again in a moment."
    "cancel_failed" -> "Couldn't cancel. Please try again."
    else -> "We couldn't book that. Try again in a moment."
```

Note `spot_unavailable`, `invalid_spot_preference` and `booking_busy` each lose an em dash; `cancel_failed` moves here from its inline site (see Step 4) and loses one too.

- [ ] **Step 4: Route the catch through the classifier**

In `BookingViewModel.kt`, find the booking-submit catch that currently maps any exception to `"booking_failed"`. Replace with:

```kotlin
        } catch (e: CancellationException) {
            throw e
        } catch (e: BookingError) {
            // The server named a reason. Always more useful than a category.
            _submitState.value = BookingSubmit.Failed(e.code)
        } catch (e: Exception) {
            _submitState.value = BookingSubmit.Failed(
                when (e.toErrorType()) {
                    ErrorType.CONNECTION -> "connection_failed"
                    ErrorType.SERVER -> "server_failed"
                },
            )
        }
```

Preserve the existing `BookingError` branch's exact shape if it already exists — read it first; the point is only that the generic branch stops collapsing into `booking_failed`.

Apply the same treatment to `confirmCancel`'s catch-all (`CancelState.Failed("cancel_failed")`), so a cancel failure also distinguishes the two categories:

```kotlin
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _cancelState.value = CancelState.Failed(
                when (e.toErrorType()) {
                    ErrorType.CONNECTION -> "connection_failed"
                    ErrorType.SERVER -> "server_failed"
                },
            )
        }
```

Check where `CancelState.Failed`'s code is rendered (`ClassDetailScreen.kt`'s cancel caption) — if it hard-codes the "Couldn't cancel" string rather than calling `bookingErrorCopy(code)`, change it to call `bookingErrorCopy(code)` so the new codes reach the member.

Add imports: `org.arcana.mobile.networking.ErrorType`, `org.arcana.mobile.networking.toErrorType`.

- [ ] **Step 5: Run to verify they pass**

Run: `./gradlew :sharedLogic:testDebugUnitTest --tests '*BookingViewModelTest*' --rerun-tasks`
Expected: PASS.

- [ ] **Step 6: Sweep the whole app for dashes in member-facing strings**

Run: `grep -rn '[—–]' sharedLogic/src/commonMain sharedUI/src/commonMain | grep -v '^\s*\*' | grep '"'`
Expected: review each hit. Any **string literal** a member can read must lose its dash. Comments and KDoc are exempt. Fix what you find in this task.

- [ ] **Step 7: Compile both targets**

Run:
```bash
./gradlew :sharedLogic:compileDebugKotlinAndroid :sharedUI:compileDebugKotlinAndroid
./gradlew :sharedLogic:compileKotlinIosSimulatorArm64 :sharedUI:compileKotlinIosSimulatorArm64
```
Expected: BUILD SUCCESSFUL for both.

- [ ] **Step 8: Verify (do NOT commit)**

---

## Task 8: Update the regression inventory

Hard PR requirement (CLAUDE.md). The suite's Phase 1 self-audit diffs the inventory against the source tree on every run — skipping this does not skip the check, it just surfaces this branch as drift on the next run.

**Files:**
- Modify: `docs/regression/inventory.md`

**Interfaces:**
- Consumes: the behavior established in Tasks 1-7.
- Produces: an inventory that `tools/regression/self_audit.sh` reports `FINDINGS: 0` against.

- [ ] **Step 1: Load the skill**

Read `.claude/skills/regression-inventory/SKILL.md` in full before editing. It carries the entry format, the ID scheme (IDs are permanent, never renumbered or reused), the Source-citation rules the audit script parses, the tombstone rule, and the iOS/Android KEEP-IN-SYNC trap.

- [ ] **Step 2: Rewrite the Expected on every entry whose behavior changed**

| Entry | New Expected, in one line |
|---|---|
| **ERR-01** | Full-screen `FullScreenError` keyed to `ErrorType`: CONNECTION shows "CAN'T REACH ARCANA." with the interrupted-line Lime motif and a "Connection" overline; SERVER shows "SOMETHING'S OFF ON OUR END." with a solid Burnt Nectar bar and a "Server" overline. TRY AGAIN calls `reload()`. The old `ErrorBlock` and the "server error" string are gone. |
| **ERR-02** | Unchanged behavior (content stays, staleness guard intact), but `applyRefetchFailure` now takes an `ErrorType` rather than a string. Update the **Source** line only if the function signature is cited. |
| **ERR-03** | **Behavior inverted.** The day area no longer sits silently on `DotMatrixLoader`. `ensureSelectedDayLoaded`'s catch sets `Success.dayError`, and the day's list area renders an `InlineError` card with an underlined Retry wired to `retryDay()`. The error clears on day switch, filter change, and refetch. |
| **ERR-04** | `FullScreenError` under the close bar (X still functional), CONNECTION/SERVER variants as ERR-01. RETRY reloads. |
| **ERR-05** | **Behavior changed.** No longer a bare caption. Cold-load failure renders `FullScreenError` with a working TRY AGAIN wired to `HomeViewModel.retry()`. A failure *while Success content is on screen* keeps the content and raises a `RefreshFailedToast` ("Couldn't refresh. Showing your last update.") with its own Retry. State the tab-re-entry finding from Task 4 Step 8 here, replacing the old "only recovery is leaving and re-entering the tab" claim. |
| **ERR-06** | **Behavior changed.** No longer a bare caption. Renders `InlineError` (card, under the "YOUR BOOKINGS" header, not full-screen) with an underlined Retry. |
| **ERR-11** | **Behavior changed.** A network exception no longer maps to `booking_failed`. `BookingSubmit.Failed("connection_failed")` renders "Couldn't reach Arcana. Check your connection and try again."; a 5xx renders `"server_failed"` → "Something went wrong on our end. Try again in a moment."; a typed server reason code (`session_full` etc.) still wins over both. The single GOT IT dismiss is **unchanged** (no in-place retry was added). |
| **ERR-12** | **Behavior changed.** `CancelState.Failed` now carries `connection_failed` / `server_failed` and renders `bookingErrorCopy(code)`, so the two categories read differently. Re-tapping CANCEL BOOKING is still the retry (unchanged). The old copy's em dash is gone: "Couldn't cancel. Please try again." |
| **ERR-20** | **This entry becomes false the moment this merges** and is the clearest illustration of why the rule exists. Rewrite: a shared classifier now exists at `networking/ErrorType.kt` (`ErrorType.CONNECTION/SERVER`, `Throwable.toErrorType()`, defined in terms of `apiRequestOutcome` with a guard test), consumed by `HomeViewModel`, `ScheduleViewModel`, `ClassDetailViewModel`, `MyBookingsViewModel`, `ProfileViewModel` and `BookingViewModel`; the shared UI family lives at `sharedUI/.../ui/ErrorState.kt`; no `"server error"` string remains anywhere. Auth/Signup keep their own copy (out of scope by design). |

- [ ] **Step 2b: Rewrite the Expected on the entries the silent-success fix changes**

The `bodyOrThrow()` change (see the Deviations log) altered behavior on surfaces beyond the ERR entries above, because a 5xx used to deserialize into an empty-but-valid DTO and be reported as **success**. Any entry whose Expected describes a 5xx as producing an empty state is now wrong.

| Entry | New Expected |
|---|---|
| **ERR-14 / ERR-15** (Studio Selection load + save) | A 5xx now reaches the error path instead of rendering an empty studio list. The `ErrorBlock` and the save caption are otherwise unchanged (this branch does not migrate them). |
| **ERR-16 / ERR-17** (Edit Profile load + save) | A 5xx on **save** now sets `formError` and shows the `FormErrorBanner` as ERR-17 already documents. Previously `updateProfile` returned an empty-but-valid `MeProfileDto` on a 5xx and the save reported success, so ERR-17's documented banner could not fire for that case at all. Load likewise now errors rather than rendering a blank profile. |
| **FAV-\*** entries describing an empty favorites list | Check every FAV entry: an outage no longer presents as "no favorites saved". Update any Expected that describes the empty state as the failure mode. |
| **SCHED-\*** entries describing an empty schedule on failure | Same check. The overview/page fetches now error rather than returning zero sessions. |

Grep the inventory for entries whose Expected mentions an empty list, "no results", or a blank state as a *failure* outcome, and correct each. State in the touched entries that this was a silent-success bug, so a future driver understands why the Expected changed.

- [ ] **Step 3: Add new entries for surfaces this branch creates**

Two surfaces have no entry today. Add them at the end of the `## ERR` section, taking the next free numbers (check the file — do **not** reuse ERR-01..20):

- **Home refresh-failed toast** — steps: load Home successfully, kill the server, pull to refresh. Expected: content stays, dark Ink `RefreshFailedToast` appears reading "Couldn't refresh. Showing your last update." with a Lime Retry; restoring the server and tapping Retry clears the toast and refreshes. Source: `sharedLogic/.../home/HomeViewModel.kt` (`refreshFailed`), `sharedUI/.../home/HomeScreen.kt`, `sharedUI/.../ui/ErrorState.kt`. Platforms: shared.
- **Client request timeout** — steps: with the app on a loading screen, `kill -STOP` the `manage.py runserver` PIDs. Expected: within ~30s the surface reaches its CONNECTION error state rather than hanging indefinitely; `kill -CONT` restores. Source: `sharedLogic/.../networking/ArcanaApiClient.kt` (`install(HttpTimeout)`). Platforms: shared. Note in the entry that before this change the hang was unbounded, which is what made the timeout P0.

- [ ] **Step 4: Update every touched entry's `Source:` line**

All ERR entries already cite `sharedLogic/…` / `sharedUI/…` paths (the split predates them), but any entry whose cited *function* changed name or signature needs its citation checked. Every path must resolve to a real file — the audit script checks this mechanically.

- [ ] **Step 5: Run the self-audit**

Run: `./tools/regression/self_audit.sh`
Expected: `FINDINGS: 0`. The script always exits 0, so **read the printed count** — do not rely on the exit status.

- [ ] **Step 6: If findings are non-zero, fix them and re-run**

Typical findings: a `Source:` path that no longer resolves (Task 5 deleted `ErrorBlock`), or a new composable with no covering entry. Fix the inventory, not the script.

- [ ] **Step 7: Verify (do NOT commit)**

---

## Task 9: Full build and test gate

**Files:** none modified.

- [ ] **Step 1: Full test suite, both modules**

Run:
```bash
./gradlew :sharedLogic:testDebugUnitTest :sharedUI:testDebugUnitTest --rerun-tasks
```
Expected: BUILD SUCCESSFUL, zero failures. Confirm from the output that tests actually ran.

- [ ] **Step 2: Compile both targets, both modules**

Run:
```bash
./gradlew :sharedLogic:compileDebugKotlinAndroid :sharedUI:compileDebugKotlinAndroid
./gradlew :sharedLogic:compileKotlinIosSimulatorArm64 :sharedUI:compileKotlinIosSimulatorArm64
```
Expected: BUILD SUCCESSFUL for both.

- [ ] **Step 3: Assemble the Android debug APK**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL. (`:composeApp:installDebug` from the old plan no longer exists.)

- [ ] **Step 4: Final string sweeps**

Run:
```bash
grep -rn '"server error' sharedLogic/src sharedUI/src
./tools/regression/self_audit.sh
```
Expected: no matches; `FINDINGS: 0`.

- [ ] **Step 5: Review the diff**

Run: `git status --short && git diff --stat`
Expected: changes confined to `sharedLogic/`, `sharedUI/`, and `docs/`. **Nothing under `iosApp/` or `androidApp/`** — if either appears, something went wrong.

- [ ] **Step 5b: BLOCKING — confirm the telemetry keys are restored**

```bash
./tools/regression/error-state-harness.sh telemetry-check
```

Expected: `ok telemetry keys match their pre-QA backup`, **exit code 0**.

Device QA blanks `ARCANA_POSTHOG_API_KEY` (Android `sharedUI/analytics.properties`, iOS `iosApp/Configuration/Secrets.xcconfig`) so local runs do not pollute the production PostHog project. **Both files are gitignored, so a forgotten restore is invisible in the PR diff and would silently ship a release with no product analytics.** Nothing else in the review process can catch this — this step is the only gate. Do not proceed to PR on a non-zero exit.

- [ ] **Step 6: Leave everything uncommitted for Cole's review**

---

## QA recipes

Replaces the old plan §5's two Developer Settings levers, which **cannot reach authenticated screens** — DevSettings is only reachable from the signed-out AuthScreen's 10-tap wordmark gesture, and using it mid-pass would force a sign-out that discards the loaded state several checks depend on.

### Start the local server safely

```bash
cd /Users/coletomlinson/Desktop/arcana/arcana-server
OPS_NOTIFIER_CLASS=notifications.telegram.NullOpsNotifier \
EMAIL_SENDER_CLASS=notifications.email.ConsoleEmailSender \
python manage.py runserver 0.0.0.0:8000
```

**This is not optional.** This checkout's `.env` points `OPS_NOTIFIER_CLASS` at the real `MultiOpsNotifier`, and `PushoverOpsNotifier.notify_ops` sends at hardcoded **emergency** priority regardless of the caller's `urgent` flag: DND-breaking, retried for up to 3 hours. Every driven booking and cancel would page Cole and Felicia for real.

Point the app at it via Developer Settings **once, before signing in**: `http://localhost:8000` (iOS sim) or `http://10.0.2.2:8000` (Android emulator).

### CONNECTION failure — kill the server process

```bash
ps aux | grep '[m]anage.py runserver'   # capture BOTH PIDs (parent + autoreload child)
kill -9 <pid> <pid>
```

- **Never `lsof -ti :8000 | xargs kill`.** A port-matched blanket kill also matches processes merely *connected* to that port; in the 2026-08-11 run it killed the Android emulator outright, requiring a full AVD relaunch and re-sign-in. Documented hazard, not a preference.
- **SIGKILL, not SIGSTOP** for this state: SIGSTOP leaves the TCP backlog accepting, so the request hangs instead of failing.

### SERVER failure (real 5xx, server still reachable) — stop Postgres

```bash
docker stop arcana_postgres
# ... drive the entry ...
docker start arcana_postgres
```

The only technique that produced a real 5xx in the entire 2026-08-11 run. Needs no base-URL change and therefore no sign-out. Limitation: it fails every DB-touching endpoint at once, so it cannot target one endpoint.

### Android network loss — toggle the radios

```bash
adb shell svc wifi disable && adb shell svc data disable
# ... drive ...
adb shell svc wifi enable && adb shell svc data enable
```

Preferred on Android: no emulator-process risk, recovers instantly. No iOS-simulator equivalent — use the server kill there.

### The timeout regression test (Task 2's sharpest check)

```bash
kill -STOP <manage.py pid>    # socket stalls, no bytes flow
# expect: the CONNECTION state after ~30s, NOT an indefinite hang
kill -CONT <manage.py pid>
```

Before Task 2 this hangs forever. **If it still hangs after Task 2, the timeout is not installed correctly.**

### Driving technique

`docs/regression/driver-playbook.md`. On iOS 26, attach the console:

```bash
xcrun simctl launch --console-pty booted org.arcana.mobile
```

The 2026-08-11 iOS 26.3 shift recorded ten consecutive connection-refused entries FAILing with the app terminating to the springboard; triage could not reproduce it in 15+ attempts and found no `.ips` file has ever existed, so it is **Unconfirmed** and is **not a work item for this branch**. Do **not** add a blanket `catch (e: Throwable)` — that would swallow real programming errors. But keep the console attached so a recurrence captures the escaping type; that would be new evidence worth a card.

### Checklist

**Home (ERR-05, the originating bug)**
- [ ] Kill server, cold-launch → "CAN'T REACH ARCANA.", Lime interrupted-line motif, "Connection" overline, working TRY AGAIN
- [ ] Tap TRY AGAIN while still down → dot-matrix loader, repeat taps ignored, returns to the error
- [ ] Restore server → TRY AGAIN → Home loads
- [ ] `docker stop arcana_postgres` → cold launch → "SOMETHING'S OFF ON OUR END.", Burnt Nectar bar, "Server" overline
- [ ] The two are unmistakably distinct; connection copy never says "server error"
- [ ] Load Home OK → kill server → pull-to-refresh → content **stays**, Ink toast appears, Retry works
- [ ] iOS: the toast clears the floating glass tab bar

**Schedule (ERR-01, ERR-02, ERR-03, SCHED-02)**
- [ ] Cold-start with server down → `FullScreenError`; old "COULDN'T LOAD SCHEDULE" block gone
- [ ] RETRY recovers once the server is back
- [ ] Loaded schedule → kill server → change a filter → content **stays** (staleness guard intact)
- [ ] **ERR-03:** loaded schedule → kill server → tap an unvisited day chip → `InlineError` card in the day area with a Retry, **not** a silent `DotMatrixLoader`
- [ ] ERR-03 Retry loads the day once the server is back
- [ ] **SCHED-02, on iOS specifically:** with favorites saved, cold-start with the server down (both fetches fail) → RETRY once the server is back → the scope toggle reads **Favorites**, not All Studios
- [ ] iOS: `FullScreenError` is not clipped by the floating tab bar

**Class Detail (ERR-04) / My Bookings (ERR-06)**
- [ ] Class Detail error renders **below** the close bar; X still works; RETRY reloads
- [ ] My Bookings renders the **inline card** under "YOUR BOOKINGS", not full-screen; Retry works
- [ ] Both show distinct CONNECTION and SERVER variants

**Booking (ERR-11, ERR-12)** — server started with the Null notifier, verified
- [ ] Kill server → confirm a booking → "Couldn't reach Arcana. Check your connection and try again."
- [ ] `docker stop arcana_postgres` → confirm a booking → "Something went wrong on our end. Try again in a moment."
- [ ] The two read differently (this is the whole point of ERR-11)
- [ ] Cancel failure likewise distinguishes the two

**Timeout (Task 2)**
- [ ] `kill -STOP` mid-load → CONNECTION state within ~30s on both platforms, no indefinite hang

**Silent-success fix (the `bodyOrThrow()` behavior change) — highest regression risk in the branch**

These surfaces used to render an *empty state* on a 5xx and now render an error. Drive each with `docker stop arcana_postgres` (real 5xx, server reachable), and confirm the error appears **and** that the happy path still works afterwards with Postgres back up.

- [ ] Favorites / Studio Selection: 5xx → error, NOT "no favorites saved"
- [ ] Schedule: 5xx → error, NOT an empty day with zero classes
- [ ] Profile: 5xx → error, NOT a blank profile
- [ ] Edit Profile **save**: 5xx → `FormErrorBanner` fires, NOT a silent "saved" with fields wiped
- [ ] With Postgres restored, every one of the above loads and saves normally (proves `bodyOrThrow` did not break the 2xx path)
- [ ] Any endpoint returning 2xx with an empty or non-JSON body still succeeds (watch for a 204-style response regressing)

**Cross-cutting**
- [ ] Both platforms: iOS 26 simulator + Android emulator
- [ ] No em/en dashes in any copy seen on screen
- [ ] Small screen: the full-screen block stays lower-third, nothing clipped
- [ ] Profile still shows its small caption on the Ink hero (intentionally unchanged)

---

## Post-merge

After the PR merges, move these cards on the **Arcana Regressions** board (https://trello.com/b/xfX4x4Vc/arcana-regressions) from `Run 2026-08-11` to `Done`, appending the disposition + PR link to each description (the Trello MCP has no comment action):

- ERR-05 · Home connection error is a bare caption, no retry
- ERR-06 · My Bookings connection error is likewise bare
- ERR-03 · Uncached day-chip fetch failure renders nothing
- SCHED-02 · Retry after failed favorites fetch keeps All Studios
- ERR-11 · Booking/cancel/save failures have no dedicated retry — **partial.** The network-vs-server conflation is fixed; the "dedicated retry affordance" half (ERR-11 booking sheet, ERR-12 cancel sheet, ERR-15 Studio Selection save) was **not** built and needs Cole's design call. Do not move this card to Done without confirming that with him; the disposition must say which half shipped.

Also worth doing: Trello card [150 Error states](https://trello.com/c/p2OCDf7T/150-error-states) has an empty description and names nothing. Paste the branch name, this plan's path, and the PR link onto it.

---

## Deviations log

Record here anything the implementation had to do differently from this plan, with the reason.

| Task | Deviation | Why |
|---|---|---|
| 1 | **Added `ApiHttpError(statusCode)` + a `bodyOrThrow()` helper, applied to 12 read endpoints, and extended `toErrorType()` to map `ApiHttpError` / `LoginError` / `PasswordResetRequestError`.** Not in the original plan. | The salvaged classifier branched on `ResponseException`, which **this client never throws**: `expectSuccess` is never assigned (Ktor 3.x defaults it false), there is no `HttpResponseValidator`, and read endpoints were bare `client.get(...).body()`. Every failure — including real 5xx — classified as CONNECTION, making the entire SERVER category unreachable. Without this the branch would ship error states that always say "check your connection" during a server outage. Corroborated by the 2026-08-11 run: SERVER was "not elicited" on every screen except Auth, and Auth is the one surface that inspects `response.status` manually. |
| 1 | Left 7 endpoints unconverted: `login`, `createBooking` (+ its inner read), token refresh, password reset, concierge, `completeSignup`, `submitSignupSurvey`. | Each already inspects `response.status` itself to surface a typed error (`LoginError`, `BookingError`, …) or, for refresh, deliberately tolerates a non-2xx. Converting them would break those paths. An unconverted endpoint merely degrades to today's behavior; a wrongly-converted one is a real regression. |
| 1 | `BookingError` / `ConciergeError` deliberately NOT mapped in `toErrorType()`. | They carry a server *reason code*, not an HTTP status. A `session_full` is not a server fault and must not render SERVER copy. |
| 1 | **Discovered and fixed a silent-success bug** larger than the original defect: `FavoritesDto`, `ScheduleOverviewDto`, `SchedulePageDto` and `MeProfileDto` default every field, so with `ignoreUnknownKeys = true` a 5xx error body deserialized into an empty-but-valid DTO and was reported as **success**. A server outage rendered "you have no favorites" / an empty schedule rather than any error. | This is also **SCHED-02's true root cause**: the favorites fetch never failed, it "succeeded" empty, so scope stayed at AllStudios and `favoritesKnown` was never false. **Behavior change to spot-check on device:** a 5xx on Schedule / Profile / Favorites now surfaces an error where it previously showed an empty state. |
| 1 | Orchestrator additionally converted `updateProfile` (`client.patch`), which the fix agent had left alone as outside its literal scoping rule. | `MeProfileDto` all-defaults too, so a 5xx on **save** reported success and returned an empty profile: the member was told their changes saved when they had not. Converting makes ERR-17's already-documented `formError` banner actually fire. Compiled both targets + full suite green after the change. |
| 4 | **Home's Error state renders `FullScreenError` INSTEAD of the `LazyColumn`, not as an item inside it.** This plan's Task 4 Step 5 prescribed `item { FullScreenError(modifier = Modifier.fillParentMaxSize()) }`; that was wrong. | Home emits `TopBar` + `Spacer(32)` + `HeroHeader` + `Spacer(28)` unconditionally before the state `when`, so a viewport-height error item starts ~200-240dp down the list and pushes RETRY — this task's entire deliverable — below the fold (~85dp clipped on a short screen). Worse, `HeroHeader` renders a shimmering name placeholder for its `else ->` branch, which includes Error, so a perpetual shimmer sat above a block named `FullScreenError`. The design spec defines `FullScreenError` as "cold load, nothing cached to show", so a persistent header above it contradicts the component's own contract. Correct shape: branch inside `PullToRefreshBox` — `if (state is Error) FullScreenError(...fillMaxSize()) else LazyColumn { ... }`. Pull-to-refresh still wraps both. |
| 4 | `RefreshFailedToast` gains an `onDismiss` affordance. | As built, the toast's only control was Retry, whose handler immediately re-refreshes — so a member in a dead zone got a permanently pinned Ink bar covering the last list row with no way to clear it. `dismissRefreshFailed()` existed on the ViewModel but was unreachable from the UI. Same stuck-state failure class the branch exists to eliminate. |
| 4 | Kept `collectAsState()` rather than the plan's `collectAsStateWithLifecycle()`. | `HomeScreen` collects three flows side by side; making one lifecycle-aware would freeze it at a stale value across a background/resume while its siblings advance. `refreshFailed` is a plain `MutableStateFlow` with no upstream producer to suspend, so there is nothing to save. Consistency within the composable is the stronger argument. |
| 1 | Added `ktor-client-mock` to `gradle/libs.versions.toml`, commonTest-only, `version.ref = "ktor"`. | Needed to prove the 500 → SERVER path end-to-end through a real Ktor response pipeline rather than by hand-constructing exceptions. Ktor is not subject to the CMP-CHANGELOG pinning rule (that binds `composeMultiplatform`, nav, lifecycle, material3 only). |
