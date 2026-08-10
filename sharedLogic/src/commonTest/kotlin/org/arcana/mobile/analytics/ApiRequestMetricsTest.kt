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

    /** Locks the ENTIRE endpoint map so any rename/removal fails the build.
     *  When you add an endpoint to normalizeEndpoint, add its assertion here. */
    @Test fun `every endpoint mapping is locked`() {
        val expected = mapOf(
            ("GET" to "/api/v1/classes/") to "schedule_window",
            ("GET" to "/api/v1/classes/overview/") to "schedule_overview",
            ("GET" to "/api/v1/classes/sessions/") to "schedule_page",
            ("GET" to "/api/v1/classes/8412/") to "class_detail",
            ("GET" to "/api/v1/memberships/me") to "membership_me",
            ("GET" to "/api/v1/bookings/me/") to "my_bookings",
            ("POST" to "/api/v1/bookings/") to "booking_create",
            ("GET" to "/api/v1/bookings/55/") to "booking_detail",
            ("DELETE" to "/api/v1/bookings/55/") to "booking_cancel",
            ("POST" to "/api/v1/auth/token/") to "login",
            ("POST" to "/api/v1/auth/token/refresh/") to "token_refresh",
            ("POST" to "/api/v1/auth/complete-signup") to "complete_signup",
            ("POST" to "/api/v1/auth/request-password-reset") to "password_reset",
            ("POST" to "/api/v1/beta/signup-survey") to "signup_survey",
            ("GET" to "/api/v1/users/me/") to "profile",
            ("PATCH" to "/api/v1/users/me/") to "profile_update",
            ("GET" to "/api/v1/users/me/favorites/") to "favorites",
            ("PUT" to "/api/v1/users/me/favorites/") to "favorites_update",
            ("POST" to "/api/v1/concierge-requests/") to "concierge_create",
        )
        for ((call, name) in expected) {
            assertEquals(name, normalizeEndpoint(call.first, call.second), "mapping for ${call.first} ${call.second}")
        }
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
