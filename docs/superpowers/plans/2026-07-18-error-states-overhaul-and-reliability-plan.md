# Arcana Mobile — Error States Overhaul + Reliability Follow-ups

**Pickup plan. Written 2026-07-18. Self-contained: a fresh session needs only this file.**

---

## 0. How to use this document

This is a resume-from-cold plan. It captures work already done, work remaining, and the supporting evidence. Start here:

1. Check out the remote branch (see §9). The bulk of the UI work is already implemented, compiling, and unit-tested on it.
2. Read §2 (what's done) so you don't redo it.
3. Work §3 in priority order. Implementation detail for each item is in §4.
4. QA against §5 before merging. Nothing has been visually spot-checked on-device yet.

Dev workflow (standing preference): branch off latest `main`, leave changes uncommitted for review, no push/PR until explicit go. Compile **both** targets after any `commonMain` change. This branch has already been pushed (that was explicitly authorized as a backlog snapshot); treat future changes with the normal uncommitted-for-review flow.

---

## 1. Background — why this exists

On 2026-07-18, Chad (our main Android tester) opened the freshly-auto-updated app cold to demo it, and the Home screen showed a **"server error."** Investigation (PostHog event stream + GCloud Cloud Logging) established:

- The Home cold-start calls (`membership_me`, `favorites`) failed with **client-side network errors**: `status_code = 0`, `server_ms` null, resolved in 16–179ms, both network flags null.
- The server logged **zero 5xx** and **no record of those requests arriving** — they never left the device.
- Cause: a transient cold-start connectivity blip right after the Play Store auto-update. It self-resolved ~35s later.

So the app told a member the server failed when the server was perfectly healthy. Three underlying problems:

1. **Misclassification.** Five screens emit a bare `"server error"` in their catch blocks — including the generic `catch (e: Exception)` path where a dropped connection lands.
2. **Inconsistent UI.** Schedule had a full error block with retry; Home had a tiny caption with **no retry**; others rolled their own.
3. **No client request timeout.** A stalled request never throws, so it never surfaces an error at all (89-second hangs observed in prod — see §6).

---

## 2. Current status — what is DONE (on the branch)

**Decisions locked:**
- Two member-facing categories: **CONNECTION** (no valid response from server: offline, flaky, timeout, DNS, status 0) vs **SERVER** (5xx or unexpected non-auth 4xx). Auth (401/403) is **out of scope** — the token-refresh interceptor owns it. Form-field validation errors are out of scope (already have good copy).
- Visual direction: **type-forward minimal**, from the Claude Design "Error State System" handoff (light + dark, full redlines). Light implemented; dark deferred (see §3 P2).

**Implemented, compiling, and unit-tested on `feature/error-states-overhaul`:**
- `networking/ErrorType.kt` — the classifier. `Throwable.toErrorType()`: a `ResponseException` (HTTP status received) → SERVER; anything else → CONNECTION. Defined in terms of `analytics/ApiRequestMetrics.apiRequestOutcome` so UI and telemetry can't drift; a guard test fails if they ever disagree.
- `ui/ErrorState.kt` — the shared UI family: `FullScreenError`, `InlineError`, `RefreshFailedToast`, `RetryButton` (solid), `RetryLink` (underlined), and a centralized `ErrorCopy` object (no screen hand-writes an error string anymore). Built from existing `ui/`+`theme/` primitives.
- Migrated `Error(String)` → `Error(ErrorType)` in: `HomeViewModel`, `ScheduleViewModel`, `ClassDetailViewModel`, `MyBookingsViewModel`, `ProfileViewModel`. Deleted the two local `ErrorBlock` composables (Schedule + Class Detail). **Home gained a working retry and a refresh-failed toast** (previously had neither).
- Tests: `networking/ErrorTypeTest.kt` (new) + strengthened `HomeViewModelTest` / `ProfileViewModelTest` to assert the CONNECTION classification (the exact regression) and the retry/refresh-failed behavior.
- **Verified:** `:composeApp:compileDebugKotlinAndroid` and `:composeApp:compileKotlinIosSimulatorArm64` both BUILD SUCCESSFUL; `:composeApp:testDebugUnitTest` green (forced `--rerun-tasks`, confirmed the new tests executed).
- No `"server error"` strings remain outside explanatory comments.

**Reference docs on the branch:**
- Spec: `docs/superpowers/specs/2026-07-18-error-states-overhaul-design.md` (§10 records the two deviations: light-only, and redlines snapped to the 4dp/2sp house grid).
- Design brief: `docs/error-states-design-brief.md`.

---

## 3. Remaining work (priority order)

### P0 — required before this branch ships
1. **Manual QA / on-device spot-check.** Nothing has been visually verified. Full checklist in §5.
2. **Add `HttpTimeout` to the Ktor client.** This is what makes the new error states actually *fire* — without it a stalled request hangs indefinitely (89s observed in prod). Client-side; ships with this branch's mobile release. Detail in §4.1. **Do this on this branch, with this work.**

### P1 — strong, evidence-backed follow-ups
3. **Auto-retry the cold-start calls.** The cold-start network-error trio hits real members, not just Chad — `ariel.leichi.huang@gmail.com` logged 13 failures across ~13s spanning Home *and* Schedule (§6). A short bounded retry/backoff on the initial Home/Schedule loads would self-heal the blip before any error UI shows. Detail in §4.2.
4. **Retry "failed again" state.** The design specced a third retry state (idle → retrying → failed-again, with a "Still no connection. Try again." note). Only idle + retrying are built. Detail in §4.3.

### P2 — optional / later
5. **Dark variants.** Deferred: the app has no dark mode (`ArcanaTheme` installs `lightColorScheme`; nothing reads `isSystemInDarkTheme`). Shipping dark error screens inside a light-only app would look broken. Color resolution in `ui/ErrorState.kt` is already funnelled through `accentFor()` / `overlineColorFor()` + surface constants, so adding dark is a localized change when app-wide dark mode arrives. The dark mockups in the Design project remain valid.
6. **StudioSelection error migration.** `StudioSelectionViewModel` still carries its own `"Couldn't load Studios."` copy. Onboarding-only, copy already category-neutral and correct; fold into the shared `InlineError` for consistency when convenient.
7. **Profile error treatment.** Profile intentionally still shows a small caption on its Ink hero (a Stone error block would clash with the dark hero). Revisit only if you want it unified.

### P3 — investigations surfaced by the dashboard (separate from the UI work; mostly server-side)
8. **`login` 400 burst.** 18× HTTP 400 on `/auth/token` inside a 2-minute window (2026-07-17 16:50–52), distinct from ordinary 401 bad-password (only 2 of those). A 400 suggests a *malformed* request (empty field?), not wrong credentials — someone likely stuck hammering login. Check whether the login form can submit empty and what the server 400s on. Cheap, possibly a real lockout.
9. **`booking_create` / `booking_cancel` server latency.** p50 `booking_create` ≈ 1.5s with real 6.5s outliers, and it's **server_ms**, not network — almost certainly the synchronous upstream studio-platform call. Reserving is the most important action in the app. Profile it; consider async/optimistic handling. Low sample size — confirm with more data first.

---

## 4. Implementation detail for the remaining code changes

### 4.1 `HttpTimeout` (mobile, `networking/ArcanaApiClient.kt`)

Install `HttpTimeout` on the `HttpClient` (alongside `ContentNegotiation` / `PerfTimingPlugin` / `Auth`, which are the only plugins there today).

**Critical nuance — do not break legitimately slow calls.** `booking_create` / `booking_cancel` genuinely take up to ~6.5s **server-side** (§6). An aggressive overall request timeout would kill real bookings. Prefer a **socket** timeout (fires when no bytes flow for N seconds — catches the 89s stall) over a tight overall request timeout.

Recommended starting values (tune with data):
- `connectTimeoutMillis = 10_000`
- `socketTimeoutMillis = 30_000` (kills the 89s hangs; still allows a 6.5s booking that is actively transferring)
- `requestTimeoutMillis`: leave unset, or a generous `60_000`. Do **not** set it near the booking latencies.
- If needed, use Ktor's per-request timeout override to give `booking_create`/`booking_cancel` extra headroom.

**Classification check:** `HttpRequestTimeoutException` / `SocketTimeoutException` are **not** `ResponseException`, so `toErrorType()` already maps them to **CONNECTION** (correct — a timeout received no HTTP response). Add a unit test asserting this, so a future refactor can't regress it.

### 4.2 Auto-retry cold-start (mobile)

- Where: the initial `load()` path in `HomeViewModel` and `ScheduleViewModel` (consider a small shared helper).
- Retry **only on a CONNECTION failure**, only for the cold/initial load, before surfacing `FullScreenError`. E.g. 2–3 attempts with short backoff (~300ms, ~900ms). Keep it bounded so a genuinely-offline device still reaches the error state quickly.
- Do **not** auto-retry SERVER (5xx) errors — that risks hammering a struggling backend. CONNECTION only.
- Interacts with §4.1: the socket timeout is the per-attempt ceiling.

### 4.3 Retry "failed again" state (mobile, `ui/ErrorState.kt`)

- Add a `failed` state to `RetryButton` (idle / retrying / failed) plus an optional note line ("Still no connection. Try again."), per `RetryButton.dc.html` in the Claude Design project.
- Track it in the VMs: simplest is a "has failed at least once since last success" flag, or a consecutive-failure count.

---

## 5. Full manual test plan (QA checklist)

### The two levers

**Lever A — Connection errors:** airplane mode, OR Developer Settings → base URL `http://10.255.255.1` (routable but dead). (Developer Settings is reachable from the auth-screen footer; the base URL is re-read per request, so no rebuild.)

**Lever B — Server errors:** run a local always-500 server and point Developer Settings at it:

```bash
python3 -c "
from http.server import BaseHTTPRequestHandler, HTTPServer
class H(BaseHTTPRequestHandler):
    def do_GET(s): s.send_error(500)
    def do_POST(s): s.send_error(500)
    def do_PUT(s): s.send_error(500)
    def do_DELETE(s): s.send_error(500)
HTTPServer(('0.0.0.0', 8000), H).serve_forever()"
```
Base URL: `http://localhost:8000` (iOS sim) or `http://10.0.2.2:8000` (Android emulator). Debug builds permit cleartext to both.

**Gotchas:** ① Log in *before* switching to the 500 server (login will 500 too). ② You won't be logged out — a 500 on `token_refresh` maps to TRANSIENT and keeps tokens.

### Build & run
- Android: `./gradlew :composeApp:installDebug`, or run `composeApp` from Android Studio.
- iOS: open `iosApp/iosApp.xcodeproj` in Xcode, run on a simulator.

### Surfaces where the new UI appears

| # | Screen | How to reach | Component | States |
|---|---|---|---|---|
| 1 | Home | launch (default tab) | `FullScreenError` | Connection + Server |
| 2 | Home | load OK, break URL, pull-to-refresh | `RefreshFailedToast` | shared |
| 3 | Schedule | tab 2 | `FullScreenError` | Connection + Server |
| 4 | Class Detail | Schedule → tap a class row | `FullScreenError` under the close bar | Connection + Server |
| 5 | My Bookings | Home → "See all" | `InlineError` (card) | Connection + Server |
| 6 | Profile | tab 3 | **unchanged** (small caption) | — |
| 7 | Studio Selection | onboarding only | **unchanged** | — |

### Checklist

**Home — full screen (the Chad bug)**
- [ ] Kill app → airplane mode → cold launch → "CAN'T REACH ARCANA.", lime interrupted-line motif, "CONNECTION" overline, working TRY AGAIN
- [ ] Tap Try Again while offline → dot-matrix loader shows, button ignores repeat taps, returns to error
- [ ] Restore network → Try Again → Home loads
- [ ] 500 server → cold launch → "SOMETHING'S OFF ON OUR END.", Burnt Nectar solid bar, "SERVER" overline
- [ ] The two are clearly distinct; Connection copy never says "server error"

**Home — refresh toast**
- [ ] Load Home OK → switch to dead URL → pull-to-refresh → content **stays**, dark Ink bar: "Couldn't refresh. Showing your last update." + Retry
- [ ] Restore URL → tap toast Retry → toast clears, content refreshes

**Schedule**
- [ ] Airplane → Schedule tab → Connection full screen (old "Couldn't load schedule" block gone)
- [ ] 500 → Schedule → Server full screen
- [ ] Retry restores schedule
- [ ] Regression: load Schedule OK, then break URL and change day/filter → content should **stay** (staleness guard), not flip to full-screen error

**Class Detail**
- [ ] Schedule (loaded) → tap a class → break connection → back out and re-enter → error renders **below the close (X) bar**, X still works
- [ ] Both Connection and Server variants
- [ ] Retry reloads the class

**My Bookings**
- [ ] Home → "See all" → airplane → Connection **inline card** ("CAN'T LOAD THIS RIGHT NOW." + "Check your connection." + underlined Retry)
- [ ] Server variant ("THIS DIDN'T LOAD." / "On our end. Try again.")
- [ ] Card sits under the "Your bookings" heading, X still usable, not full-screen

**Profile (expect no change)**
- [ ] Break connection → Profile → still the small "Could not load profile." caption on the Ink hero (intentional)

**Cross-cutting**
- [ ] Both platforms (iOS sim + Android emulator)
- [ ] No em/en dashes in any copy
- [ ] Small screen / rotation: full-screen block stays lower-third, no clipping

### Known gaps (don't hunt for these — they're deferred, see §3)
- Retry "failed again" state not implemented (idle + retrying only).
- Dark variants not implemented (no app-wide dark mode).
- `HttpTimeout` not yet added, so a stalled request still hangs rather than surfacing the Connection state — add §4.1 first, then this QA is meaningful for hang cases.

---

## 6. Dashboard findings (supporting evidence — 7-day prod snapshot, `environment='prod'`)

PostHog "Mobile Performance & Latency" dashboard, id **1849473**, project 439926.

**Backend is healthy:** 95.9% success, **zero 5xx all week**. Error slice is small (2.15% client_error, 1.94% network_error) and clusters, not spread. (n≈891 requests; p95/p99 on low-count endpoints are directional only.)

**🔴 No client HTTP timeout (highest-value fix).** `ArcanaApiClient` installs no `HttpTimeout`. Evidence: Felicia on cellular — `favorites` **89,549ms** with `server_ms = 37ms` (server answered in 37ms; request hung 89s); same session `token_refresh` **28,109ms**, server 38ms. A hung request never throws, so no error state and no retry ever appears. The 28s refresh is also a plausible contributor to the separate **forced-logout** investigation (cellular + refresh pattern). → §4.1.

**🔴 Cold-start network errors reach real members.** 16 of 18 network_errors are the app-open trio (`membership_me`/`favorites`/`my_bookings`) in same-second bursts. `ariel.leichi.huang@gmail.com` (real beta member) hit 13 failures across ~13s spanning Home + Schedule. → §4.2.

**🟡 Booking path is slow, server-side.** `booking_create` p50 1516ms / p95 5560ms (p50 server 1325ms); `booking_cancel` p50 947ms / p95 5691ms; `class_detail` p50 1248ms. Real outliers: 6546ms and 3730ms booking_creates, almost entirely server_ms → synchronous upstream studio-platform call. → §3 P3 #9.

**🟡 `login` 400 burst.** 18× HTTP 400 on login in a 2-min window (2026-07-17 16:50–52) + 2 separate 401s. 400 ≠ bad password; smells like a malformed/empty submission. → §3 P3 #8.

---

## 7. Scope decisions / non-goals (so they aren't re-litigated)
- Auth / 401 / session expiry: out (refresh interceptor owns it).
- Form-field validation errors (Signup, Edit Profile, Concierge): out (already good, specific copy).
- Dark mode: deferred until the app has app-wide dark mode.
- API-layer retry/backoff was originally a "someday" idea; §6 evidence promotes it to P1 (§4.2).

---

## 8. Build / verify reference
- Compile both targets after any `commonMain` change: `:composeApp:compileDebugKotlinAndroid` **and** `:composeApp:compileKotlinIosSimulatorArm64` (Android-only won't catch Kotlin/Native breaks).
- Tests: `:composeApp:testDebugUnitTest` (filter with `--tests` only on this task, not the aggregate `:composeApp:test`).

---

## 9. Branch & references
- **Remote branch:** `feature/error-states-overhaul` — https://github.com/cmt218/arcana-mobile/tree/feature/error-states-overhaul
- **Spec:** `docs/superpowers/specs/2026-07-18-error-states-overhaul-design.md`
- **Design brief:** `docs/error-states-design-brief.md`
- **Claude Design project:** "Error states design brief" (id `d5af5f1c-e4b2-47df-bec1-5a77c4901410`) — files `ArcanaErrorStates.dc.html`, `ErrorState.dc.html`, `RetryButton.dc.html`. Also "Arcana Design System" (id `019e2ccb-71fd-718c-bcee-e7d4af30319a`) as a future home for these components.
- **PostHog dashboard:** Mobile Performance & Latency, id 1849473 (project 439926).
