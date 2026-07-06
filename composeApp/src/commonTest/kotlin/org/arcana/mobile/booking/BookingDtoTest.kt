package org.arcana.mobile.booking

import kotlinx.serialization.json.Json
import org.arcana.mobile.data.BookingDto
import org.arcana.mobile.data.MyBookingsDto
import org.arcana.mobile.data.MembershipMeDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val json = Json { ignoreUnknownKeys = true }

class BookingDtoTest {
    @Test
    fun `parses a booking with effective spot`() {
        val raw = """
          {"id":17,"status":"requested",
           "requested_spot":{"id":29,"label":"DF-21"},
           "fulfilled_spot":null,
           "spot":{"id":29,"label":"DF-21"},
           "session":{"id":482,"start_at":"2026-07-07T10:00:00Z","end_at":"2026-07-07T10:50:00Z","name":"RUN x LIFT","studio":"Barry's"},
           "cancel_policy":{"will_forfeit_credit":false,"cutoff_at":"2026-07-06T22:00:00Z"}}
        """.trimIndent()
        val b = json.decodeFromString(BookingDto.serializer(), raw)
        assertEquals(17, b.id)
        assertEquals("requested", b.status)
        assertEquals("DF-21", b.spot?.label)
        assertNull(b.fulfilledSpot)
        assertEquals("RUN x LIFT", b.session.name)
        assertEquals(false, b.cancelPolicy.willForfeitCredit)
    }

    @Test
    fun `parses a booking with a spot preference`() {
        val raw = """
          {"id":18,"status":"requested",
           "spot_preference":"Bag",
           "session":{"id":482,"start_at":"2026-07-07T10:00:00Z","end_at":"2026-07-07T10:50:00Z","name":"RUN x LIFT","studio":"Barry's"},
           "cancel_policy":{"will_forfeit_credit":false}}
        """.trimIndent()
        val b = json.decodeFromString(BookingDto.serializer(), raw)
        assertEquals("Bag", b.spotPreference)
    }

    @Test
    fun `booking without a spot preference defaults to null`() {
        val raw = """
          {"id":19,"status":"requested",
           "session":{"id":482,"start_at":"2026-07-07T10:00:00Z","end_at":"2026-07-07T10:50:00Z","name":"RUN x LIFT","studio":"Barry's"},
           "cancel_policy":{"will_forfeit_credit":false}}
        """.trimIndent()
        val b = json.decodeFromString(BookingDto.serializer(), raw)
        assertNull(b.spotPreference)
    }

    @Test
    fun `parses a booking with a member note`() {
        val raw = """
          {"id":20,"status":"confirmed",
           "member_note":"Door code 1234",
           "session":{"id":482,"start_at":"2026-07-07T10:00:00Z","end_at":"2026-07-07T10:50:00Z","name":"RUN x LIFT","studio":"Barry's"},
           "cancel_policy":{"will_forfeit_credit":false}}
        """.trimIndent()
        val b = json.decodeFromString(BookingDto.serializer(), raw)
        assertEquals("Door code 1234", b.memberNote)
    }

    @Test
    fun `booking without a member note defaults to null`() {
        val raw = """
          {"id":21,"status":"confirmed",
           "session":{"id":482,"start_at":"2026-07-07T10:00:00Z","end_at":"2026-07-07T10:50:00Z","name":"RUN x LIFT","studio":"Barry's"},
           "cancel_policy":{"will_forfeit_credit":false}}
        """.trimIndent()
        val b = json.decodeFromString(BookingDto.serializer(), raw)
        assertNull(b.memberNote)
    }

    @Test
    fun `parses my-bookings split`() {
        val raw = """{"upcoming":[],"past":[]}"""
        val m = json.decodeFromString(MyBookingsDto.serializer(), raw)
        assertEquals(0, m.upcoming.size)
        assertEquals(0, m.past.size)
    }

    @Test
    fun `parses memberships me with current period`() {
        val raw = """
          {"member":{"id":1,"member_number":"0002","email":"cole@arcana.fit","display_name":"Cole",
                     "avatar_initials":"CT","member_since":"2026-05-01","lifetime_sessions":3,"week_streak":2},
           "membership":{"id":1,"status":"active","tier":{"slug":"all-out-30","name":"All Out","credits_per_period":30},"stripe_customer_id":null},
           "current_period":{"payment_id":17,"window_start":"2026-07-01T04:00:00Z","window_end":"2026-08-01T04:00:00Z",
                             "credits_granted":30,"credits_used":7,"credits_remaining":23,"can_browse":true,"can_book":true},
           "upcoming_period":null,"featured_studios":null}
        """.trimIndent()
        val me = json.decodeFromString(MembershipMeDto.serializer(), raw)
        assertEquals(23, me.currentPeriod?.creditsRemaining)
        assertEquals(true, me.currentPeriod?.canBook)
        assertEquals(2, me.member.weekStreak)
    }
}
