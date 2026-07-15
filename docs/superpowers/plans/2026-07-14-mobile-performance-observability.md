# Mobile Performance & Latency Observability — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Instrument arcana-mobile + arcana-server so a new PostHog dashboard gives objective, over-time visibility into user-facing load-time variance (cold start, schedule, filters, pagination), with each request's latency split into client / network / server.

**Architecture:** A Ktor client plugin on the single `ArcanaApiClient` seam times every HTTP call and emits `api_request`; the server stamps `X-Arcana-Server-Ms` so the client can derive `network_ms = total_ms − server_ms`. A few semantic journey events (`app_start_completed`, `screen_load_completed`, `schedule_page_loaded`) capture felt latency. A new PostHog dashboard (built via MCP) mirrors the Sync Health dashboard's duration-centric shape, leaning on p50/p90/p95.

**Tech Stack:** Kotlin Compose Multiplatform (Ktor 3.1.2, Koin, `kotlin.time`), Django 4.2 middleware, PostHog (project 439926) via MCP.

**Spec:** `arcana-mobile/docs/superpowers/specs/2026-07-14-mobile-performance-observability-design.md`

## Global Constraints

- **DO NOT COMMIT.** Per Cole's standing preference, all work stays **uncommitted** on the feature branches (`mobile-perf-telemetry`, `server-timing-header`) for manual review + local testing. Each task ends at a **green test state**; the final "step" of every task is "leave uncommitted — checkpoint for review," never a `git commit`. Commit/push happens only on Cole's explicit go.
- **Mobile: compile BOTH targets after any `commonMain` change** — `./gradlew :composeApp:compileDebugKotlinAndroid` AND `./gradlew :composeApp:compileKotlinIosSimulatorArm64`. JVM-only APIs (`String.format`, `java.*`, `Locale`) break iOS. Use `kotlin.time.TimeSource.Monotonic` for durations (multiplatform).
- **Mobile: all instrumentation goes through the `Telemetry` facade** — add a typed method + `Events` constant; never scatter raw `capture("...")` strings. Property values must be primitives (String/Int/Long/Double/Boolean) for the Kotlin↔Swift bridge.
- **Mobile taxonomy is locked by `commonTest`** — every new event gets a `FakeAnalytics`-backed assertion.
- **Server: never merge straight to `main`.** Branch → PR → green CI → merge (merge auto-deploys prod). This plan leaves the server change uncommitted; the PR flow happens after Cole's review.
- **No `network_type`** (dropped in design). **No new server-side PostHog events** (header only).
- **ScheduleViewModel is regression-sensitive** — additive instrumentation only; do not alter existing control flow, generation guards, or state transitions.

---

## Task 1: Server `ServerTimingMiddleware` (arcana-server)

**Repo:** `arcana-server` (branch `server-timing-header`)

**Files:**
- Create: `arcana/middleware.py`
- Modify: `arcana/settings/base.py` (register in `MIDDLEWARE`, just after `SecurityMiddleware`, above `GZipMiddleware`)
- Test: `arcana/tests/test_server_timing.py`

**Interfaces:**
- Produces: response header `X-Arcana-Server-Ms` (string integer, milliseconds) on every response. Read by mobile Task 4.

- [ ] **Step 1: Write the failing tests**

Create `arcana/tests/test_server_timing.py`:

```python
"""ServerTimingMiddleware stamps a server-processing-time header and never
breaks the response it wraps."""
from django.http import HttpResponse
from django.test import RequestFactory

from arcana.middleware import ServerTimingMiddleware

HEADER = 'X-Arcana-Server-Ms'


def test_stamps_nonnegative_integer_header_on_normal_response():
    mw = ServerTimingMiddleware(lambda req: HttpResponse('ok'))
    resp = mw(RequestFactory().get('/anything'))
    assert HEADER in resp
    assert resp[HEADER].isdigit()
    assert int(resp[HEADER]) >= 0


def test_exposes_header_via_cors():
    mw = ServerTimingMiddleware(lambda req: HttpResponse('ok'))
    resp = mw(RequestFactory().get('/anything'))
    assert HEADER in resp['Access-Control-Expose-Headers']


def test_never_swallows_underlying_response_when_stamping_fails(monkeypatch):
    # A response object whose __setitem__ raises must still be returned intact,
    # not replaced or suppressed — analytics must never break a real response.
    sentinel = HttpResponse('body')

    def boom(*a, **k):
        raise RuntimeError('header set failed')

    monkeypatch.setattr(sentinel, '__setitem__', boom, raising=False)
    mw = ServerTimingMiddleware(lambda req: sentinel)
    resp = mw(RequestFactory().get('/anything'))
    assert resp is sentinel
    assert resp.content == b'body'
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd arcana-server && source .venv/bin/activate && pytest arcana/tests/test_server_timing.py -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'arcana.middleware'`

- [ ] **Step 3: Write the middleware**

Create `arcana/middleware.py`:

```python
"""Request-timing middleware.

Stamps `X-Arcana-Server-Ms` (integer milliseconds the request spent in Django,
including view + serialization + response middleware below this one) on every
response, so the mobile client can split its measured round-trip into server
vs network time. Best-effort: any failure to measure or stamp is swallowed and
the underlying response passes through untouched — analytics must never break a
real response.
"""
import time

SERVER_MS_HEADER = 'X-Arcana-Server-Ms'


class ServerTimingMiddleware:
    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        start = time.monotonic()
        response = self.get_response(request)
        try:
            elapsed_ms = int((time.monotonic() - start) * 1000)
            response[SERVER_MS_HEADER] = str(elapsed_ms)
            # Let browser clients (arcana-web) read the header cross-origin.
            # Harmless for the native mobile client, which can read any header.
            existing = response.get('Access-Control-Expose-Headers', '')
            exposed = [h.strip() for h in existing.split(',') if h.strip()]
            if SERVER_MS_HEADER not in exposed:
                exposed.append(SERVER_MS_HEADER)
                response['Access-Control-Expose-Headers'] = ', '.join(exposed)
        except Exception:
            # Never let instrumentation break the response.
            pass
        return response
```

- [ ] **Step 4: Register the middleware**

In `arcana/settings/base.py`, insert into `MIDDLEWARE` immediately after `SecurityMiddleware` and before `GZipMiddleware` (outermost practical position so the measured span covers the whole Django stack, incl. gzip):

```python
MIDDLEWARE = [
    'django.middleware.security.SecurityMiddleware',
    # Measure server processing time and stamp X-Arcana-Server-Ms. Sits high so
    # its span covers view + serialization + gzip below it.
    'arcana.middleware.ServerTimingMiddleware',
    'django.middleware.gzip.GZipMiddleware',
    # ... rest unchanged ...
]
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `pytest arcana/tests/test_server_timing.py -v`
Expected: PASS (3 tests)

- [ ] **Step 6: Full suite sanity check**

Run: `pytest -q`
Expected: existing suite still green (no behavior change to any endpoint).

- [ ] **Step 7: Leave uncommitted — checkpoint for review.** Do NOT commit. (PR flow happens after Cole reviews.)

---

## Task 2: `ApiRequestMetrics` pure helpers (arcana-mobile)

**Repo:** `arcana-mobile` (branch `mobile-perf-telemetry`)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/org/arcana/mobile/analytics/ApiRequestMetrics.kt`
- Test: `composeApp/src/commonTest/kotlin/org/arcana/mobile/analytics/ApiRequestMetricsTest.kt`

**Interfaces:**
- Produces:
  - `fun normalizeEndpoint(method: String, encodedPath: String): String`
  - `fun apiRequestOutcome(statusCode: Int): String` — `success`/`client_error`/`server_error`/`network_error`
  - `fun deriveNetworkMs(totalMs: Long, serverMs: Long?): Long?`
  - Used by Task 3 (Telemetry method args) and Task 4 (the Ktor plugin).

- [ ] **Step 1: Write the failing tests**

Create `ApiRequestMetricsTest.kt`:

```kotlin
package org.arcana.mobile.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ApiRequestMetricsTest {
    @Test fun `collapses class detail id to class_detail`() {
        assertEquals("class_detail", normalizeEndpoint("GET", "/api/v1/classes/8412/"))
    }

    @Test fun `distinguishes schedule window page and overview`() {
        assertEquals("schedule_window", normalizeEndpoint("GET", "/api/v1/classes/"))
        assertEquals("schedule_page", normalizeEndpoint("GET", "/api/v1/classes/sessions/"))
        assertEquals("schedule_overview", normalizeEndpoint("GET", "/api/v1/classes/overview/"))
    }

    @Test fun `disambiguates method on the same path`() {
        assertEquals("favorites", normalizeEndpoint("GET", "/api/v1/users/me/favorites/"))
        assertEquals("favorites_update", normalizeEndpoint("PUT", "/api/v1/users/me/favorites/"))
    }

    @Test fun `booking id paths collapse and split by method`() {
        assertEquals("booking_detail", normalizeEndpoint("GET", "/api/v1/bookings/55/"))
        assertEquals("booking_cancel", normalizeEndpoint("DELETE", "/api/v1/bookings/55/"))
        assertEquals("booking_create", normalizeEndpoint("POST", "/api/v1/bookings/"))
        assertEquals("my_bookings", normalizeEndpoint("GET", "/api/v1/bookings/me/"))
    }

    @Test fun `unmapped path is other`() {
        assertEquals("other", normalizeEndpoint("GET", "/api/v1/something/new/"))
    }

    @Test fun `outcome buckets by status class`() {
        assertEquals("success", apiRequestOutcome(200))
        assertEquals("success", apiRequestOutcome(201))
        assertEquals("client_error", apiRequestOutcome(404))
        assertEquals("server_error", apiRequestOutcome(503))
        assertEquals("network_error", apiRequestOutcome(0))
    }

    @Test fun `network ms is total minus server clamped at zero`() {
        assertEquals(120L, deriveNetworkMs(200L, 80L))
        assertEquals(0L, deriveNetworkMs(50L, 80L))   // clock skew → clamp
        assertNull(deriveNetworkMs(200L, null))       // no server header
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*ApiRequestMetricsTest*"`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement the helpers**

Create `ApiRequestMetrics.kt`:

```kotlin
package org.arcana.mobile.analytics

/**
 * Pure, testable helpers for the `api_request` transport-timing event. Kept
 * separate from the Ktor plugin so the mapping logic is unit-tested without an
 * HttpClient.
 */

/** Map (method, path) to a stable, low-cardinality endpoint name. Path ids are
 *  collapsed so `/classes/8412/` and `/classes/99/` both become `class_detail`.
 *  The raw path is never sent to PostHog. Unmapped routes → `other`. */
fun normalizeEndpoint(method: String, encodedPath: String): String {
    // Strip the version prefix and any leading/trailing slashes, then collapse
    // pure-integer segments to `{id}` so ids don't explode cardinality.
    val path = encodedPath
        .substringAfter("/api/v1/", encodedPath)
        .trim('/')
    val shape = path.split('/')
        .joinToString("/") { seg -> if (seg.toIntOrNull() != null) "{id}" else seg }
    val m = method.uppercase()
    return when (m to shape) {
        "GET" to "classes" -> "schedule_window"
        "GET" to "classes/overview" -> "schedule_overview"
        "GET" to "classes/sessions" -> "schedule_page"
        "GET" to "classes/{id}" -> "class_detail"
        "GET" to "memberships/me" -> "membership_me"
        "GET" to "bookings/me" -> "my_bookings"
        "POST" to "bookings" -> "booking_create"
        "GET" to "bookings/{id}" -> "booking_detail"
        "DELETE" to "bookings/{id}" -> "booking_cancel"
        "POST" to "auth/token" -> "login"
        "POST" to "auth/token/refresh" -> "token_refresh"
        "POST" to "auth/complete-signup" -> "complete_signup"
        "POST" to "auth/request-password-reset" -> "password_reset"
        "POST" to "beta/signup-survey" -> "signup_survey"
        "GET" to "users/me" -> "profile"
        "PATCH" to "users/me" -> "profile_update"
        "GET" to "users/me/favorites" -> "favorites"
        "PUT" to "users/me/favorites" -> "favorites_update"
        "POST" to "concierge-requests" -> "concierge_create"
        else -> "other"
    }
}

/** Bucket an HTTP status into an outcome class. `0` = the request never
 *  completed (network/IO/timeout — the plugin passes 0 on exception). */
fun apiRequestOutcome(statusCode: Int): String = when {
    statusCode == 0 -> "network_error"
    statusCode in 200..399 -> "success"
    statusCode in 400..499 -> "client_error"
    else -> "server_error"
}

/** Network time = client round-trip minus server processing, clamped ≥ 0.
 *  Null when the server header was absent (can't attribute the split). */
fun deriveNetworkMs(totalMs: Long, serverMs: Long?): Long? =
    if (serverMs == null) null else (totalMs - serverMs).coerceAtLeast(0L)
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*ApiRequestMetricsTest*"`
Expected: PASS.

- [ ] **Step 5: Leave uncommitted — checkpoint for review.**

---

## Task 3: `Telemetry` methods + `Events` constants + taxonomy tests (arcana-mobile)

**Repo:** `arcana-mobile`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/org/arcana/mobile/analytics/Telemetry.kt`
- Test: `composeApp/src/commonTest/kotlin/org/arcana/mobile/analytics/PerformanceTelemetryTest.kt`

**Interfaces:**
- Produces (consumed by Tasks 4, 5, 6):
  - `fun apiRequest(endpoint: String, method: String, statusCode: Int, outcome: String, totalMs: Long, serverMs: Long?, networkMs: Long?, responseBytes: Long?)`
  - `fun appStartCompleted(durationMs: Long, startType: String, authenticated: Boolean, splashMs: Long?)`
  - `fun screenLoadCompleted(screen: String, source: String, durationMs: Long, outcome: String, sessionCount: Int?)`
  - `fun schedulePageLoaded(durationMs: Long, pageIndex: Int, sessionCount: Int, outcome: String, day: String)`
  - `Events.API_REQUEST`, `Events.APP_START_COMPLETED`, `Events.SCREEN_LOAD_COMPLETED`, `Events.SCHEDULE_PAGE_LOADED`

- [ ] **Step 1: Write the failing tests**

Create `PerformanceTelemetryTest.kt`:

```kotlin
package org.arcana.mobile.analytics

import kotlin.test.Test
import kotlin.test.assertEquals

/** Locks the performance-observability taxonomy (event names + property keys). */
class PerformanceTelemetryTest {
    @Test fun `api_request carries transport split`() {
        val (telemetry, analytics, _) = fakeTelemetry()
        telemetry.apiRequest(
            endpoint = "schedule_page", method = "GET", statusCode = 200,
            outcome = "success", totalMs = 200, serverMs = 80, networkMs = 120,
            responseBytes = 4096,
        )
        val ev = analytics.first(Telemetry.Events.API_REQUEST)!!
        assertEquals("schedule_page", ev.properties["endpoint"])
        assertEquals(200, ev.properties["status_code"])
        assertEquals("success", ev.properties["outcome"])
        assertEquals(200L, ev.properties["total_ms"])
        assertEquals(80L, ev.properties["server_ms"])
        assertEquals(120L, ev.properties["network_ms"])
        assertEquals(4096L, ev.properties["response_bytes"])
    }

    @Test fun `app_start_completed carries duration and start type`() {
        val (telemetry, analytics, _) = fakeTelemetry()
        telemetry.appStartCompleted(durationMs = 1400, startType = "cold", authenticated = true, splashMs = null)
        val ev = analytics.first(Telemetry.Events.APP_START_COMPLETED)!!
        assertEquals(1400L, ev.properties["duration_ms"])
        assertEquals("cold", ev.properties["start_type"])
        assertEquals(true, ev.properties["authenticated"])
    }

    @Test fun `screen_load_completed carries screen and source`() {
        val (telemetry, analytics, _) = fakeTelemetry()
        telemetry.screenLoadCompleted(screen = "Schedule", source = "day_switch", durationMs = 12, outcome = "success", sessionCount = 30)
        val ev = analytics.first(Telemetry.Events.SCREEN_LOAD_COMPLETED)!!
        assertEquals("Schedule", ev.properties["screen"])
        assertEquals("day_switch", ev.properties["source"])
        assertEquals(12L, ev.properties["duration_ms"])
        assertEquals("success", ev.properties["outcome"])
        assertEquals(30, ev.properties["session_count"])
    }

    @Test fun `schedule_page_loaded carries page index and day`() {
        val (telemetry, analytics, _) = fakeTelemetry()
        telemetry.schedulePageLoaded(durationMs = 90, pageIndex = 2, sessionCount = 50, outcome = "success", day = "2026-07-20")
        val ev = analytics.first(Telemetry.Events.SCHEDULE_PAGE_LOADED)!!
        assertEquals(90L, ev.properties["duration_ms"])
        assertEquals(2, ev.properties["page_index"])
        assertEquals(50, ev.properties["session_count"])
        assertEquals("2026-07-20", ev.properties["day"])
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*PerformanceTelemetryTest*"`
Expected: FAIL — unresolved `apiRequest` / `Events.API_REQUEST` etc.

- [ ] **Step 3: Add methods + constants to `Telemetry.kt`**

Add these methods in `Telemetry` (place after the `// ---- Screens & navigation ----` block, before Signup funnel):

```kotlin
    // ---- Performance & latency -------------------------------------------

    /** One per HTTP call (via the ArcanaApiClient Ktor plugin). `serverMs` is
     *  from the `X-Arcana-Server-Ms` header (null if absent); `networkMs` is the
     *  derived `total − server`. `statusCode` 0 ⇒ the request never completed. */
    fun apiRequest(
        endpoint: String,
        method: String,
        statusCode: Int,
        outcome: String,
        totalMs: Long,
        serverMs: Long?,
        networkMs: Long?,
        responseBytes: Long?,
    ) = track(
        Events.API_REQUEST,
        mapOf(
            "endpoint" to endpoint,
            "method" to method,
            "status_code" to statusCode,
            "outcome" to outcome,
            "total_ms" to totalMs,
            "server_ms" to serverMs,
            "network_ms" to networkMs,
            "response_bytes" to responseBytes,
        ),
    )

    /** Fired once per process when Home (or the auth screen) first renders. */
    fun appStartCompleted(durationMs: Long, startType: String, authenticated: Boolean, splashMs: Long?) =
        track(
            Events.APP_START_COMPLETED,
            mapOf(
                "duration_ms" to durationMs,
                "start_type" to startType,
                "authenticated" to authenticated,
                "splash_ms" to splashMs,
            ),
        )

    /** A screen reached rendered content. `source` distinguishes a fresh entry
     *  (cold_start/tab_switch) from an in-screen change (day_switch/filter/refresh). */
    fun screenLoadCompleted(screen: String, source: String, durationMs: Long, outcome: String, sessionCount: Int?) =
        track(
            Events.SCREEN_LOAD_COMPLETED,
            mapOf(
                "screen" to screen,
                "source" to source,
                "duration_ms" to durationMs,
                "outcome" to outcome,
                "session_count" to sessionCount,
            ),
        )

    /** Infinite-scroll pagination append completed. */
    fun schedulePageLoaded(durationMs: Long, pageIndex: Int, sessionCount: Int, outcome: String, day: String) =
        track(
            Events.SCHEDULE_PAGE_LOADED,
            mapOf(
                "duration_ms" to durationMs,
                "page_index" to pageIndex,
                "session_count" to sessionCount,
                "outcome" to outcome,
                "day" to day,
            ),
        )
```

Add to `object Events` (after `SCHEDULE_FILTER_CHANGED`):

```kotlin
        const val API_REQUEST = "api_request"
        const val APP_START_COMPLETED = "app_start_completed"
        const val SCREEN_LOAD_COMPLETED = "screen_load_completed"
        const val SCHEDULE_PAGE_LOADED = "schedule_page_loaded"
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*PerformanceTelemetryTest*"`
Expected: PASS.

- [ ] **Step 5: Compile both targets**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Leave uncommitted — checkpoint for review.**

---

## Task 4: `PerfTimingPlugin` Ktor plugin — emit `api_request` (arcana-mobile)

**Repo:** `arcana-mobile`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/org/arcana/mobile/networking/PerfTimingPlugin.kt`
- Modify: `composeApp/src/commonMain/kotlin/org/arcana/mobile/networking/ArcanaApiClient.kt` (install the plugin in the `HttpClient {}` block)

**Interfaces:**
- Consumes: `Telemetry.apiRequest(...)` (Task 3), `normalizeEndpoint` / `apiRequestOutcome` / `deriveNetworkMs` (Task 2).

> No unit test here — the mapping logic is already tested in Task 2, and exercising a Ktor `Send` hook needs a MockEngine harness this repo doesn't have. Verification is: both-target compile (this task) + real events post-build (Task 8). Keep the plugin a thin wire over the tested pure helpers.

- [ ] **Step 1: Write the plugin**

Create `PerfTimingPlugin.kt`:

```kotlin
package org.arcana.mobile.networking

import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.contentLength
import kotlin.random.Random
import kotlin.time.TimeSource
import org.arcana.mobile.analytics.Telemetry
import org.arcana.mobile.analytics.apiRequestOutcome
import org.arcana.mobile.analytics.deriveNetworkMs
import org.arcana.mobile.analytics.normalizeEndpoint

class PerfTimingConfig {
    /** Telemetry sink. Left as Noop until installed by ArcanaApiClient. */
    var telemetry: Telemetry = Telemetry.Noop

    /** Fraction of requests to record (0.0–1.0). 1.0 = every request. The knob
     *  lets us dial volume down later without touching call sites. */
    var sampleRate: Double = 1.0
}

/**
 * Times every request on the client it's installed on and emits one
 * `api_request` event: total round-trip (send → response available), the
 * server's self-reported processing time (`X-Arcana-Server-Ms`), and the
 * derived network time. Best-effort — a failure to record must never affect
 * the request. A network/IO exception is recorded as `network_error` (status 0)
 * and re-thrown so the caller's own error handling is unchanged.
 */
val PerfTimingPlugin = createClientPlugin("PerfTiming", ::PerfTimingConfig) {
    val telemetry = pluginConfig.telemetry
    val sampleRate = pluginConfig.sampleRate

    on(Send) { request ->
        val sampled = sampleRate >= 1.0 || Random.nextDouble() < sampleRate
        if (!sampled) return@on proceed(request)

        val method = request.method.value
        val path = request.url.encodedPath
        val mark = TimeSource.Monotonic.markNow()
        val call = try {
            proceed(request)
        } catch (e: Throwable) {
            recordSafely(telemetry) {
                telemetry.apiRequest(
                    endpoint = normalizeEndpoint(method, path),
                    method = method,
                    statusCode = 0,
                    outcome = apiRequestOutcome(0),
                    totalMs = mark.elapsedNow().inWholeMilliseconds,
                    serverMs = null,
                    networkMs = null,
                    responseBytes = null,
                )
            }
            throw e
        }
        val totalMs = mark.elapsedNow().inWholeMilliseconds
        recordSafely(telemetry) {
            val status = call.response.status.value
            val serverMs = call.response.headers["X-Arcana-Server-Ms"]?.toLongOrNull()
            telemetry.apiRequest(
                endpoint = normalizeEndpoint(method, path),
                method = method,
                statusCode = status,
                outcome = apiRequestOutcome(status),
                totalMs = totalMs,
                serverMs = serverMs,
                networkMs = deriveNetworkMs(totalMs, serverMs),
                responseBytes = call.response.contentLength(),
            )
        }
        call
    }
}

/** Swallow any analytics-path failure — instrumentation must never break a call. */
private inline fun recordSafely(telemetry: Telemetry, block: () -> Unit) {
    try {
        block()
    } catch (e: Throwable) {
        telemetry.recordError(e, mapOf("where" to "PerfTimingPlugin"))
    }
}
```

- [ ] **Step 2: Install it in `ArcanaApiClient`**

In `ArcanaApiClient.kt`, inside `private val client = HttpClient { ... }`, add the install (e.g. right after the `install(ContentNegotiation) { ... }` block):

```kotlin
        install(PerfTimingPlugin) {
            telemetry = this@ArcanaApiClient.telemetry
            // 100% during beta (tiny volume); dial down here if we approach
            // the PostHog free-tier ceiling.
            sampleRate = 1.0
        }
```

- [ ] **Step 3: Compile both targets**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL. (If `Send` / `contentLength` imports resolve differently in Ktor 3.1.2, fix the import path — the symbols exist: `io.ktor.client.plugins.api.Send`, `io.ktor.http.contentLength`.)

- [ ] **Step 4: Run the full common test suite (nothing should regress)**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Leave uncommitted — checkpoint for review.**

---

## Task 5: `AppStartTracker` — emit `app_start_completed` (arcana-mobile)

**Repo:** `arcana-mobile`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/org/arcana/mobile/analytics/AppStartTracker.kt`
- Modify: `composeApp/src/commonMain/kotlin/org/arcana/mobile/App.kt` (fire when first content renders)
- Modify: `composeApp/src/androidMain/kotlin/org/arcana/mobile/ArcanaApplication.kt` (mark process start)
- Modify: `composeApp/src/iosMain/kotlin/org/arcana/mobile/MainViewController.kt` (mark process start)
- Test: `composeApp/src/commonTest/kotlin/org/arcana/mobile/analytics/AppStartTrackerTest.kt`

**Interfaces:**
- Produces: `AppStartTracker.markStart()`, `AppStartTracker.onFirstContent(telemetry, authenticated)`, `AppStartTracker.resetForTest()`.

> **v1 scope:** fires **once per process** with `start_type = "cold"`. Warm-start re-fires are deferred (noted in spec §4.2). `splash_ms` is passed `null` (the splash has a fixed min-display floor, so it isn't a useful variance signal in v1).

- [ ] **Step 1: Write the failing test**

Create `AppStartTrackerTest.kt`:

```kotlin
package org.arcana.mobile.analytics

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AppStartTrackerTest {
    @BeforeTest fun setUp() = AppStartTracker.resetForTest()
    @AfterTest fun tearDown() = AppStartTracker.resetForTest()

    @Test fun `fires app_start_completed once with cold start type`() {
        val (telemetry, analytics, _) = fakeTelemetry()
        AppStartTracker.markStart()
        AppStartTracker.onFirstContent(telemetry, authenticated = true)
        AppStartTracker.onFirstContent(telemetry, authenticated = true) // must not double-fire

        val starts = analytics.all(Telemetry.Events.APP_START_COMPLETED)
        assertEquals(1, starts.size)
        assertEquals("cold", starts.first().properties["start_type"])
        assertEquals(true, starts.first().properties["authenticated"])
    }

    @Test fun `does nothing when start was never marked`() {
        val (telemetry, analytics, _) = fakeTelemetry()
        AppStartTracker.onFirstContent(telemetry, authenticated = false)
        assertEquals(0, analytics.all(Telemetry.Events.APP_START_COMPLETED).size)
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*AppStartTrackerTest*"`
Expected: FAIL — unresolved `AppStartTracker`.

- [ ] **Step 3: Implement the tracker**

Create `AppStartTracker.kt`:

```kotlin
package org.arcana.mobile.analytics

import kotlin.time.TimeSource

/**
 * Measures cold start: from the platform entry point ([markStart], called in
 * ArcanaApplication.onCreate / MainViewController) to the first rendered content
 * ([onFirstContent], called from App.kt). Fires `app_start_completed` exactly
 * once per process. Not true process/dyld start — starts at the earliest point
 * we control — so it under-counts pre-main time (documented; don't read as OS
 * TTID).
 */
object AppStartTracker {
    private var startMark: TimeSource.Monotonic.ValueTimeMark? = null
    private var fired = false

    /** Record t0 as early as possible at the platform entry point. Idempotent. */
    fun markStart() {
        if (startMark == null) startMark = TimeSource.Monotonic.markNow()
    }

    /** Fire once, when the first screen's content renders. No-op if already
     *  fired or if [markStart] never ran. */
    fun onFirstContent(telemetry: Telemetry, authenticated: Boolean) {
        if (fired) return
        val mark = startMark ?: return
        fired = true
        telemetry.appStartCompleted(
            durationMs = mark.elapsedNow().inWholeMilliseconds,
            startType = "cold",
            authenticated = authenticated,
            splashMs = null,
        )
    }

    fun resetForTest() {
        startMark = null
        fired = false
    }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*AppStartTrackerTest*"`
Expected: PASS.

- [ ] **Step 5: Wire the fire point in `App.kt`**

In `App.kt`, in the authenticated branch, fire when MainScaffold first renders. Change:

```kotlin
        if (isAuthenticated) {
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides sessionStoreOwner
            ) {
                MainScaffold()
            }
        }
```

to:

```kotlin
        if (isAuthenticated) {
            LaunchedEffect(Unit) {
                org.arcana.mobile.analytics.AppStartTracker.onFirstContent(telemetry, authenticated = true)
            }
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides sessionStoreOwner
            ) {
                MainScaffold()
            }
        }
```

And in the final `else` auth branch, next to the existing `LaunchedEffect(Unit) { telemetry.screen(Telemetry.Screens.AUTH) }`, add a sibling effect (fires cold-start for an unauthenticated launch; the once-guard means whichever path renders first wins):

```kotlin
                LaunchedEffect(Unit) {
                    org.arcana.mobile.analytics.AppStartTracker.onFirstContent(telemetry, authenticated = false)
                }
```

- [ ] **Step 6: Mark process start at both entry points**

In `androidMain/.../ArcanaApplication.kt`, as the **first line** of `onCreate()` (before `super.onCreate()` is fine — it only reads a clock):

```kotlin
        org.arcana.mobile.analytics.AppStartTracker.markStart()
```

In `iosMain/.../MainViewController.kt`, at the **top of the `MainViewController(...)` function body**, before Koin start:

```kotlin
    org.arcana.mobile.analytics.AppStartTracker.markStart()
```

- [ ] **Step 7: Compile both targets**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Leave uncommitted — checkpoint for review.**

---

## Task 6: Schedule journey events — `screen_load_completed` + `schedule_page_loaded` (arcana-mobile)

**Repo:** `arcana-mobile`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/org/arcana/mobile/schedule/ScheduleViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/org/arcana/mobile/analytics/ScheduleTelemetryTest.kt` (extend)

**Interfaces:**
- Consumes: `Telemetry.screenLoadCompleted(...)`, `Telemetry.schedulePageLoaded(...)` (Task 3).

> **Additive only.** Do not change existing control flow, the generation guards, or the debounce pipeline. Only add a `source` parameter to `refetchForFilters` (with call-site updates) and fire events at existing completion points.

- [ ] **Step 1: Write the failing tests (extend `ScheduleTelemetryTest`)**

Add these imports to `ScheduleTelemetryTest.kt` if missing: none new beyond existing. Add tests:

```kotlin
    @Test fun `cold start fires screen_load_completed with source cold_start`() = runTest {
        val (telemetry, analytics, _) = fakeTelemetry()
        vm(telemetry)
        advanceUntilIdle()
        val ev = analytics.all(Telemetry.Events.SCREEN_LOAD_COMPLETED)
            .firstOrNull { it.properties["source"] == "cold_start" }
        assertTrue(ev != null)
        assertEquals("Schedule", ev.properties["screen"])
        assertEquals("success", ev.properties["outcome"])
    }

    @Test fun `switching to a cached day fires screen_load_completed with source day_switch`() = runTest {
        val (telemetry, analytics, _) = fakeTelemetry()
        val v = vm(telemetry)
        advanceUntilIdle()
        val today = Clock.System.todayIn(ScheduleViewModel.ScheduleTimeZone)
        v.selectDay(today.plus(2, DateTimeUnit.DAY))
        advanceUntilIdle()
        val ev = analytics.all(Telemetry.Events.SCREEN_LOAD_COMPLETED)
            .firstOrNull { it.properties["source"] == "day_switch" }
        assertTrue(ev != null)
        assertEquals("Schedule", ev.properties["screen"])
    }

    @Test fun `changing a filter fires screen_load_completed with source filter`() = runTest {
        val (telemetry, analytics, _) = fakeTelemetry()
        val v = vm(telemetry)
        advanceUntilIdle()
        v.toggleModality("cycle")
        advanceUntilIdle()
        val ev = analytics.all(Telemetry.Events.SCREEN_LOAD_COMPLETED)
            .firstOrNull { it.properties["source"] == "filter" }
        assertTrue(ev != null)
    }
```

> **Pagination test note:** whether `schedule_page_loaded` can be asserted depends on `FakeScheduleApi` returning a non-null `nextCursor` on page 1. Inspect `composeApp/src/commonTest/kotlin/org/arcana/mobile/schedule/FakeScheduleApi.kt` first. If it already returns a cursor, add:
>
> ```kotlin
>     @Test fun `loadMore fires schedule_page_loaded`() = runTest {
>         val (telemetry, analytics, _) = fakeTelemetry()
>         val v = vm(telemetry)
>         advanceUntilIdle()
>         v.loadMore()
>         advanceUntilIdle()
>         assertTrue(analytics.first(Telemetry.Events.SCHEDULE_PAGE_LOADED) != null)
>     }
> ```
>
> If the fake returns no cursor, `loadMore` correctly no-ops — in that case add a second page to `FakeScheduleApi` (a `nextCursor` on the first page, empty on the second) so the path is exercised, keeping the change confined to the test fake.

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*ScheduleTelemetryTest*"`
Expected: FAIL — no `screen_load_completed` events captured.

- [ ] **Step 3: Add the `import` and instrument `refetchForFilters`**

At the top of `ScheduleViewModel.kt`, add:

```kotlin
import kotlin.time.TimeSource
```

Change the signature and body of `refetchForFilters`. Replace the header line:

```kotlin
    private suspend fun refetchForFilters() {
        val generation = ++fetchGeneration
```

with:

```kotlin
    private suspend fun refetchForFilters(source: String = "cold_start") {
        val generation = ++fetchGeneration
        val loadMark = TimeSource.Monotonic.markNow()
```

Then, in the success path, immediately after the existing `publish()` that follows `refreshingFilters = false` (the atomic-apply block), add the fire:

```kotlin
            refreshingFilters = false
            publish()
            telemetry.screenLoadCompleted(
                screen = Telemetry.Screens.SCHEDULE,
                source = source,
                durationMs = loadMark.elapsedNow().inWholeMilliseconds,
                outcome = "success",
                sessionCount = page.toSessions().size,
            )
            if (dayStates[selectedDate]?.loaded != true) ensureSelectedDayLoaded()
```

And in `applyRefetchFailure`, the failure fire must know source + mark. Simplest: fire directly in the two `catch` branches (they already gate on `generation == fetchGeneration`). Replace:

```kotlin
        } catch (e: ResponseException) {
            val code = e.response.status.value
            logWarning("ScheduleViewModel", e.message ?: "HTTP $code")
            if (generation == fetchGeneration) applyRefetchFailure("server error $code")
        } catch (e: Exception) {
            logWarning("ScheduleViewModel", e.message ?: "Unknown error")
            if (generation == fetchGeneration) applyRefetchFailure("server error")
        }
```

with:

```kotlin
        } catch (e: ResponseException) {
            val code = e.response.status.value
            logWarning("ScheduleViewModel", e.message ?: "HTTP $code")
            if (generation == fetchGeneration) {
                applyRefetchFailure("server error $code")
                telemetry.screenLoadCompleted(
                    screen = Telemetry.Screens.SCHEDULE, source = source,
                    durationMs = loadMark.elapsedNow().inWholeMilliseconds,
                    outcome = "error", sessionCount = null,
                )
            }
        } catch (e: Exception) {
            logWarning("ScheduleViewModel", e.message ?: "Unknown error")
            if (generation == fetchGeneration) {
                applyRefetchFailure("server error")
                telemetry.screenLoadCompleted(
                    screen = Telemetry.Screens.SCHEDULE, source = source,
                    durationMs = loadMark.elapsedNow().inWholeMilliseconds,
                    outcome = "error", sessionCount = null,
                )
            }
        }
```

- [ ] **Step 4: Update the `refetchForFilters` call sites with a `source`**

There are four call sites. Update each:
- In `reload()`: `refetchForFilters()` → `refetchForFilters("cold_start")`
- In `refresh()`: `refetchForFilters()` → `refetchForFilters("refresh")`
- In `init`'s favorites job (`lastAppliedFavorites = favorites` then `refetchForFilters()`): → `refetchForFilters("cold_start")`
- In `init`'s debounce pipeline (`collectLatest { refetchForFilters() }`): → `collectLatest { refetchForFilters("filter") }`

(The `refreshBookedSessions()`/`publish()` init job does not call `refetchForFilters` — leave it.)

- [ ] **Step 5: Instrument `selectDay` (day_switch) and `loadMore` (pagination)**

In `selectDay`, wrap the day-changed block to time the synchronous switch. Replace:

```kotlin
        if (date != selectedDate) {
            val previous = selectedDate
            val today = Clock.System.todayIn(ScheduleTimeZone)
            telemetry.scheduleDayChanged(
                method = method,
                direction = if (date > previous) "forward" else "backward",
                dayOffsetFromToday = (date.toEpochDays() - today.toEpochDays()).toInt(),
            )
            selectedDate = date
            publish()
        }
```

with:

```kotlin
        if (date != selectedDate) {
            val switchMark = TimeSource.Monotonic.markNow()
            val previous = selectedDate
            val today = Clock.System.todayIn(ScheduleTimeZone)
            telemetry.scheduleDayChanged(
                method = method,
                direction = if (date > previous) "forward" else "backward",
                dayOffsetFromToday = (date.toEpochDays() - today.toEpochDays()).toInt(),
            )
            selectedDate = date
            publish()
            // Felt latency of the swipe. For a cached day this is a client-side
            // re-bucket (near-0, proving swipes are instant); an uncached day's
            // network fetch shows up separately as api_request / a page load.
            telemetry.screenLoadCompleted(
                screen = Telemetry.Screens.SCHEDULE,
                source = "day_switch",
                durationMs = switchMark.elapsedNow().inWholeMilliseconds,
                outcome = "success",
                sessionCount = dayStates[date]?.sessions?.size,
            )
        }
```

In `loadMore`, capture a mark at the start and fire on success next to the existing `scheduleLoadMore`. Change the start of `loadMore`:

```kotlin
    fun loadMore() {
        val date = selectedDate
        val day = dayStates[date] ?: return
        if (!day.loaded || day.nextCursor == null || day.loadingMore) return
```

to add the mark after the guard:

```kotlin
    fun loadMore() {
        val date = selectedDate
        val day = dayStates[date] ?: return
        if (!day.loaded || day.nextCursor == null || day.loadingMore) return
        val pageMark = TimeSource.Monotonic.markNow()
```

Then after the existing success line `telemetry.scheduleLoadMore(pageIndex = pageIndex, day = date.toString())`, add:

```kotlin
                telemetry.scheduleLoadMore(pageIndex = pageIndex, day = date.toString())
                telemetry.schedulePageLoaded(
                    durationMs = pageMark.elapsedNow().inWholeMilliseconds,
                    pageIndex = pageIndex,
                    sessionCount = page.toSessions().size,
                    outcome = "success",
                    day = date.toString(),
                )
```

- [ ] **Step 6: Run the extended tests**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*ScheduleTelemetryTest*"`
Expected: PASS (existing + new).

- [ ] **Step 7: Run the FULL common suite (regression gate for ScheduleViewModel)**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: PASS — all existing schedule/booking/favorites/etc. tests still green.

- [ ] **Step 8: Compile both targets**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Leave uncommitted — checkpoint for review.**

---

## Task 7: Build the "Mobile Performance & Latency" PostHog dashboard (MCP)

**Not a code task** — executed via the PostHog MCP (`posthog:exec` → `dashboard-create`, `insight-create`, and the tile-add tool). Build against project 439926. Model each tile on the Sync Health analog (dashboard 1691952). This can be done **now**; tiles read empty until a mobile build ships (Task 8).

**Dashboard-level config:**
- Name: `Mobile Performance & Latency`
- Description: `User-facing app performance: cold start, screen loads, API latency (client/network/server split), pagination. Fed by arcana-mobile api_request / app_start_completed / screen_load_completed / schedule_page_loaded + reused class_viewed.load_ms. Server split via X-Arcana-Server-Ms.`
- `filters.date_from = "-14d"`
- `filters.properties`: exclude internal/test users (standing list): `email NOT contains @arcana.fit`, `email NOT contains tomlinson631+`, `email != felicia.dodge@gmail.com`, `email NOT contains test@test`. (Match the exclusion the other beta dashboards use; confirm exact property + operator via `read-data-schema` for `person.properties.email`.)

- [ ] **Step 1: Confirm the events exist before building queries.** Run `read-data-schema {"query":{"kind":"events"}}` and verify `api_request` etc. are present. **They will NOT exist until a mobile build ships** — that's expected. Build the dashboard + insights anyway (queries are valid); they render empty until Task 8. Note this in the dashboard description.

- [ ] **Step 2: Create the dashboard** via `dashboard-create` with the config above. Record the returned dashboard id.

- [ ] **Step 3: Create + attach the 10 tiles.** Mirror the Sync Health tile query shapes (HogQL `DataVisualizationNode` for per-event scatter; `InsightVizNode`/`TrendsQuery` for aggregates). Use `math: "p90"` / `"p95"` on Trends, or `quantile(0.9)(...)` in HogQL.

  1. **Cold start → Home (p50/p90 over time)** — Trends, `app_start_completed`, two series `math=p50` + `math=p90` on `duration_ms`, `interval=day`, properties filter `authenticated = true` and `start_type = cold`.
  2. **Screen load latency over time (p90 by screen)** — Trends, `screen_load_completed`, `math=p90` `duration_ms`, breakdown `screen`.
  3. **Slowest screen+source (p90, 7d)** — Trends `ActionsBarValue`, `screen_load_completed` `math=p90` `duration_ms`, breakdown `source`, date `-7d`.
  4. **API latency over time (p50/p90/p95)** — Trends, `api_request`, three series `math=p50/p90/p95` on `total_ms`, `interval=day`.
  5. **Slowest endpoints (p90, 7d)** — Trends `ActionsBarValue`, `api_request` `math=p90` `total_ms`, breakdown `endpoint`, date `-7d`.
  6. **Server vs network split over time** — HogQL: `SELECT toStartOfHour(timestamp) AS t, round(avg(toFloat(properties.server_ms)),0) AS server_ms, round(avg(toFloat(properties.network_ms)),0) AS network_ms FROM events WHERE event = 'api_request' AND properties.server_ms IS NOT NULL AND {filters} GROUP BY t ORDER BY t`. Line graph, two series.
  7. **Per-request latency scatter (by endpoint)** — HogQL: `SELECT timestamp, properties.endpoint AS endpoint, toFloat(properties.total_ms) AS total_ms FROM events WHERE event = 'api_request' AND {filters} ORDER BY timestamp LIMIT 100000`. `ActionsLineGraph`, `seriesBreakdownColumn: endpoint` (mirrors Sync Health tile `kT7JAMOT`).
  8. **Request outcomes by status (count over time)** — Trends, `api_request` `math=total`, breakdown `outcome`, `interval=day`, `ActionsLineGraph`.
  9. **Infinite-scroll pagination (p90 by page_index)** — Trends, `schedule_page_loaded` `math=p90` `duration_ms`, breakdown `page_index`.
  10. **Class detail latency (p50/p90 over time)** — Trends, existing `class_viewed`, two series `math=p50/p90` on `load_ms`, `interval=day`.

  Use a 2-wide layout (`w:6`) like Sync Health.

- [ ] **Step 4: Open the dashboard URL** (`_posthogUrl` from the create response) and confirm all 10 tiles exist and render (empty is fine pre-data). Report the URL to Cole.

---

## Task 8: Post-build verification (after the next mobile release)

**Not runnable until a mobile TestFlight/App Store build ships** (real beta is iOS-only). Sequenced last; parked until the build is out.

- [ ] **Step 1:** After a build with these changes is on a device and used, run `read-data-schema {"query":{"kind":"events"}}` and confirm `api_request`, `app_start_completed`, `screen_load_completed`, `schedule_page_loaded` are ingesting.
- [ ] **Step 2:** Spot-check one `api_request` via `read-data-schema {"query":{"kind":"event_properties","event_name":"api_request"}}` — confirm `server_ms` is populated (proves the header round-trips) and `network_ms` looks sane (≈ `total − server`).
- [ ] **Step 3:** Confirm dashboard tiles now render real data; tune percentiles / date ranges / any endpoint that shows as `other` (add it to `normalizeEndpoint`).
- [ ] **Step 4:** Report findings to Cole.

---

## Self-Review

**Spec coverage:**
- api_request transport event + server/network split → Tasks 2, 3, 4 (client) + Task 1 (header). ✓
- app_start_completed → Task 5. ✓
- screen_load_completed (Schedule: cold_start/day_switch/filter/refresh) → Task 6. ✓ (Home/Profile/MyBookings screen_load deferred; cold-start→home is covered by `app_start_completed`. Named pain points — schedule swipes/filters/pagination/cold start — are all covered.)
- schedule_page_loaded → Task 6. ✓
- class_viewed.load_ms reuse → Task 7 tile 10. ✓
- ServerTimingMiddleware, no migration, PR flow → Task 1. ✓
- Dashboard (10 tiles, p50/p90/p95, internal-user exclusion, Sync Health shape) → Task 7. ✓
- network_type dropped; no new server events → honored throughout. ✓
- Sampling knob (default 1.0) → Task 4 config. ✓
- Rollout: server ships independently, dashboard pre-built empty, mobile needs a build, verify last → Tasks 1/7/8. ✓

**Placeholder scan:** No "TBD/TODO/handle edge cases." The two conditional notes (FakeScheduleApi cursor in Task 6; exact CORS/exclusion operator confirmation in Task 7) each specify the exact check to run and both branches of the outcome — not open-ended.

**Type consistency:** `screenLoadCompleted(screen, source, durationMs, outcome, sessionCount)`, `apiRequest(endpoint, method, statusCode, outcome, totalMs, serverMs, networkMs, responseBytes)`, `schedulePageLoaded(durationMs, pageIndex, sessionCount, outcome, day)`, `appStartCompleted(durationMs, startType, authenticated, splashMs)` — signatures identical across the Telemetry definition (Task 3) and every call site (Tasks 4, 5, 6). `X-Arcana-Server-Ms` string matches between Task 1 (set) and Task 4 (read). `normalizeEndpoint`/`apiRequestOutcome`/`deriveNetworkMs` signatures match between Task 2 (defined) and Task 4 (used).
