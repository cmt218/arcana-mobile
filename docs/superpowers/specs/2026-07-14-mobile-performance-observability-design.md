# Mobile Performance & Latency Observability — Design

**Date:** 2026-07-14
**Author:** Cole Tomlinson (+ Claude)
**Repos touched:** `arcana-mobile` (client instrumentation), `arcana-server` (Server-Timing header), PostHog project 439926 (new dashboard)
**Status:** Approved design, pending implementation plan

## 1. Problem & goal

We have anecdotal reports (Felicia, and Cole's own observation) of **user-facing load-time variance** in the mobile app:

- Cold start / splash → Home UI taking a variable, sometimes long, time to appear.
- Schedule: swiping between days, applying filters, and infinite-scroll pagination performing inconsistently.

Today we have **zero objective data** on any of this. Mobile telemetry (the `Telemetry` facade over PostHog) is deliberately scoped to state-transitions + errors; the only latency signal that exists is `class_viewed.load_ms`. The server emits only the three scheduled-sync events (`sync_studio_run` / `sync_platform_run` / `sync_fleet_run`) — no per-request API latency anywhere.

**Goal:** a new PostHog dashboard, loosely modeled on the existing **Sync Health** dashboard (id 1691952), that gives us objective, over-time observability into user-facing performance — so we can keep it good, catch regressions early, and tell *where* variance comes from (client, network, or server / upstream studio API).

## 2. Non-goals

- **Not** a replacement for Sentry crash/nonfatal reporting — this is latency/throughput, not errors-as-crashes (though request outcomes are tracked).
- **Not** full distributed tracing. We attribute latency to three coarse buckets (client / network / server); we do not thread a trace id end-to-end in v1.
- **Not** `network_type` (wifi/cellular) segmentation — considered and **explicitly dropped for v1** to avoid the native `expect/actual` addition. May revisit if variance data points at connectivity.
- **No** new server-side PostHog events — the server contributes latency data via a response header only, so we add zero server event volume/cost.

## 3. Core strategy

Two complementary layers:

1. **Transport layer (the workhorse).** Every mobile HTTP call already funnels through one seam — `ArcanaApiClient`'s Ktor `HttpClient`. A Ktor client plugin times each call and emits one `api_request` event. The server stamps its own processing time on `X-Arcana-Server-Ms`; the client reads it into the same event. Thus each request records **`total_ms` (client wall-clock), `server_ms` (from header), and `network_ms = total_ms − server_ms`** — the split that answers "is it us, the network, or the studio's upstream?"

2. **Journey layer (semantic, felt latency).** A small set of always-on events measure the end-to-end experience of the exact moments reported as janky: cold start → Home, per-screen content load (incl. day-switch and filter apply via a `source` dimension), and infinite-scroll pagination. These include render time, not just transport.

This mirrors Sync Health's discipline: duration-centric events with a few sliceable dimensions, dashboard tiles that are mostly "duration over time" + "slowest X" + "outcomes by status."

## 4. Event taxonomy (arcana-mobile)

All events go through the existing type-safe `Telemetry` facade (new typed methods + `Events` constants), never raw `capture()` strings. Every event auto-carries the existing `platform` super-property. Property values stay primitives (Kotlin↔Swift bridge rule). Taxonomy is locked by `commonTest` regression tests, per the repo convention.

### 4.1 `api_request` — transport-level, every HTTP call

Fired by a Ktor client plugin installed on the `ArcanaApiClient` `HttpClient`, so it covers *every* call including token refresh and login — no per-call-site changes.

| Property | Type | Notes |
|---|---|---|
| `endpoint` | String | **Normalized** route name, never the raw URL. See §4.5 map. Keeps cardinality bounded (path ids collapsed). |
| `method` | String | `GET` / `POST` / `PUT` / `PATCH` / `DELETE` |
| `status_code` | Int | HTTP status; `0` when the request never completed (network/IO/timeout exception). |
| `outcome` | String | `success` (2xx), `client_error` (4xx), `server_error` (5xx), `network_error` (no response). |
| `total_ms` | Long | Client wall-clock, request send → full response received (transport round-trip; excludes caller-side `.body()` deserialization, which the journey events capture). |
| `server_ms` | Long? | Parsed from `X-Arcana-Server-Ms`. Null if absent (network error, or a response that never reached the middleware). |
| `network_ms` | Long? | Derived `= max(0, total_ms − server_ms)` when both present; else null. |
| `response_bytes` | Long? | Response `Content-Length` when present (post-gzip = on-the-wire size). Best-effort; null under chunked/unknown. |

Measurement point: the plugin brackets the request from just before send to response-received inside the `HttpClient` pipeline. This is intentionally transport-only; felt end-to-end latency (incl. JSON parse + Compose render) is the journey layer's job.

### 4.2 `app_start_completed` — startup → Home interactive

| Property | Type | Notes |
|---|---|---|
| `duration_ms` | Long | From the earliest point we control at the platform entry point (Koin start in `ArcanaApplication.onCreate` / `MainViewController`) to the first Home content frame. |
| `start_type` | String | `cold` (first composition of the process) vs `warm` (return from background into a fresh Home). Best-effort; a process-level flag distinguishes first launch. |
| `authenticated` | Boolean | Whether startup resolved to Home (`true`) or the Auth screen (`false`). Unauthenticated starts take a different path and should be sliced out of the "cold start → Home" headline. |
| `splash_ms` | Long? | Time the splash was actually on screen (has a min-display floor; useful to confirm the splash isn't the bottleneck). |

**Caveat (documented, acceptable for v1):** `duration_ms` starts at our entry-point hook, not true process/`main`/dyld start, so it slightly *under*-counts pre-main time. It captures the dominant, controllable portion (init → first data → render). Noted so nobody reads it as OS-level TTID.

### 4.3 `screen_load_completed` — a screen showed new content

One event covers Home load, Schedule load, day-switch, filter apply, and pull-to-refresh via the `source` dimension. Fires when a ViewModel reaches a rendered `Success` (or `Error`) state for the surface.

| Property | Type | Notes |
|---|---|---|
| `screen` | String | `Home` / `Schedule` / `ClassDetail` / `MyBookings` / `Profile` (aligns with `Telemetry.Screens`). |
| `source` | String | `cold_start` / `tab_switch` / `refresh` / `day_switch` / `filter`. Distinguishes a fresh entry from an in-screen re-render. |
| `duration_ms` | Long | Start-of-load → content rendered. For client-side re-bucketing (most day-switch/filter changes are client-side per the Schedule architecture) this should be tiny — the event *proves* that, or catches it if not. |
| `outcome` | String | `success` / `error`. |
| `session_count` | Int? | Rows rendered, where meaningful (Schedule). |

`ClassDetail`'s sync-on-read latency is already captured by the existing `class_viewed.load_ms`; we still allow `screen=ClassDetail` here for symmetry but the dashboard's detail tile reads `class_viewed`.

### 4.4 `schedule_page_loaded` — infinite-scroll append

Distinct from `screen_load_completed` because it appends a page rather than rendering a whole screen; pairs with the existing (duration-less) `schedule_load_more`.

| Property | Type | Notes |
|---|---|---|
| `duration_ms` | Long | Load-more triggered → appended rows rendered. |
| `page_index` | Int | Which page (0-based continuation index). |
| `session_count` | Int | Rows in this page. |
| `outcome` | String | `success` / `error`. |
| `day` | String | The day being paginated (ISO date). |

### 4.5 Endpoint normalization map

A single pure `normalizeEndpoint(method, path)` helper (unit-tested) collapses ids and maps to stable names. Initial map:

| Method + path | `endpoint` |
|---|---|
| `GET classes/` | `schedule_window` |
| `GET classes/overview/` | `schedule_overview` |
| `GET classes/sessions/` | `schedule_page` |
| `GET classes/{id}/` | `class_detail` |
| `GET memberships/me` | `membership_me` |
| `GET bookings/me/` | `my_bookings` |
| `POST bookings/` | `booking_create` |
| `GET bookings/{id}/` | `booking_detail` |
| `DELETE bookings/{id}/` | `booking_cancel` |
| `POST auth/token/` | `login` |
| `POST auth/token/refresh/` | `token_refresh` |
| `POST auth/complete-signup` | `complete_signup` |
| `POST auth/request-password-reset` | `password_reset` |
| `POST beta/signup-survey` | `signup_survey` |
| `GET users/me/` | `profile` |
| `PATCH users/me/` | `profile_update` |
| `GET users/me/favorites/` | `favorites` |
| `PUT users/me/favorites/` | `favorites_update` |
| `POST concierge-requests/` | `concierge_create` |
| (unmapped) | `other` (with the raw path dropped) |

### 4.6 Volume posture

Beta is ~50 users, iOS-only in practice. Even one `api_request` per call is low volume at this scale. We add a **sampling knob** on `api_request` (a `single` config value, default **1.0 = 100%** now) so we can dial it down later if we approach the PostHog free-tier ceiling without changing call sites. Journey events are always-on (low frequency). No sampling on journey events.

## 5. Server side (arcana-server)

### 5.1 `ServerTimingMiddleware`

A new Django middleware (in the `arcana` app, registered in `MIDDLEWARE`) that:

- Records `time.monotonic()` at the start of `__call__`, computes elapsed ms after `get_response(request)`, and sets `response['X-Arcana-Server-Ms'] = str(int(elapsed_ms))`.
- Is wrapped so it **can never break a response** (any failure to stamp is swallowed; the response passes through untouched).
- Adds `X-Arcana-Server-Ms` to `Access-Control-Expose-Headers` (harmless for the native client; correct for any browser origin, i.e. arcana-web).
- Measures view + serialization wall-clock (middleware sees the whole response cycle inside it). DB-time breakdown is **deferred** (a `X-Arcana-Db-Ms` split can be added later via a cursor timer; v1 is total server ms only).

Placement in the stack: outermost practical position so the measured span is as close to "time the request spent in Django" as possible, while still inside the WSGI boundary.

### 5.2 No migration, standard workflow

This is middleware + a test only — no model change, no migration. It ships via the required arcana-server flow: feature branch → PR → **green CI** → merge (merge auto-deploys to prod). The header is inert for the current mobile app (which doesn't read it yet), so it can ship independently and ahead of the mobile build.

### 5.3 Tests

- Middleware stamps a plausible integer header on a normal 200.
- Middleware never raises / never suppresses the underlying response on an exception path.
- Header value parses as a non-negative int.

## 6. Dashboard ("Mobile Performance & Latency")

New PostHog dashboard, project 439926, same 2-column duration-centric grid as Sync Health. **Emphasis on p50 / p90 / p95, not averages** — variance is the point. **Internal/test users excluded** at the dashboard `filters.properties` level (the standing exclusion list: `@arcana.fit`, `tomlinson631+*`, `felicia.dodge@gmail`, `test@test`). Default date range `-14d` (matches Sync Health).

Tiles (order, ~2-wide each):

1. **Cold start → Home over time** — `app_start_completed.duration_ms`, p50 + p90, filtered `authenticated=true`, `start_type=cold`. Headline.
2. **Screen load latency over time** — `screen_load_completed.duration_ms` p90, breakdown by `screen`.
3. **Slowest screens (p90, 7d)** — bar, `screen_load_completed.duration_ms` p90 breakdown by `screen`+`source` (the "slowest studios" analog).
4. **API request latency over time** — `api_request.total_ms` p50/p90/p95.
5. **Slowest endpoints (p90, 7d)** — bar, `api_request.total_ms` p90 breakdown by `endpoint`.
6. **Server vs network split over time** — HogQL, two series (avg/p90 `server_ms` vs `network_ms`). The crux tile.
7. **Per-request latency scatter** — HogQL, one point per `api_request`, line broken down by `endpoint` (the `sync_studio_run` scatter analog).
8. **Request outcomes by status** — `api_request` count, breakdown by `outcome` (regression / error-rate catch).
9. **Infinite-scroll pagination latency** — `schedule_page_loaded.duration_ms` p90, optionally by `page_index`.
10. **Class detail latency over time** — existing `class_viewed.load_ms` p50/p90 (reuse; no new instrumentation).

Percentiles via Trends `math: p90`/`p95` or HogQL `quantile(0.9)(...)`. Built via the PostHog MCP (`dashboard-create` + `insight-create` / `dashboard-add-insight`), mirroring the Sync Health tile shapes.

## 7. Rollout & sequencing

The dashboard reads **empty until a mobile build ships** — same pattern as the pre-created August-cohort survey tiles. That's expected, not a bug.

- **Phase 0** — this spec + implementation plan.
- **Phase 1 (server)** — `ServerTimingMiddleware` + tests on `server-timing-header`. Left uncommitted for Cole's review; then PR → CI → merge. Ships the header (inert for current app).
- **Phase 2 (mobile)** — on `mobile-perf-telemetry`: Ktor timing plugin + `api_request`, `normalizeEndpoint` + test, `app_start_completed`, `screen_load_completed`, `schedule_page_loaded`, typed `Telemetry` methods + `Events` constants, taxonomy regression tests, sampling knob. **Compile both targets** (Android JVM + iOS Native). iOS `SwiftAnalytics` needs no change (events flow through the existing `Analytics.capture`). Left uncommitted for Cole's review + local testing.
- **Phase 3 (dashboard)** — build "Mobile Performance & Latency" via PostHog MCP now, so it's ready.
- **Phase 4 (verify)** — after the next mobile TestFlight/App Store build, confirm events land (iOS), sanity-check `server_ms`/`network_ms` split, tune queries/percentiles.

**Working-tree convention (per Cole's standing preference):** all code work is done on feature branches off `main` and **left uncommitted** for manual review + local testing. Nothing is committed or pushed until Cole gives an explicit go. (This spec file itself is likewise left uncommitted.)

## 8. Testing summary

- **Mobile:** `normalizeEndpoint` unit tests (id collapsing, method disambiguation, unmapped→`other`); `FakeAnalytics`-backed taxonomy tests for each new event (names + property keys), matching the existing `*TelemetryTest` pattern; both-target compile.
- **Server:** middleware header-stamping + never-break tests.
- **Dashboard:** validated by real events post-build (Phase 4); query shapes sanity-checked against Sync Health analogs at build time.

## 9. Open questions

None blocking. Deferred-by-choice: `network_type` segmentation, `X-Arcana-Db-Ms` server DB-time split, `api_request` sampling below 100% — all additive later without breaking the taxonomy.
