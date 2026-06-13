package org.arcana.mobile.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MembershipWalletTest {
    private fun member() = MemberDto(1, "0001", "c@x.com", "Cole", "C", "2026-05-01", 0, 0)
    private fun membership() = MembershipBriefDto(1, "active", TierDto("std", "Standard", 12))

    private fun wallet(label: String?, start: String?, end: String?, remaining: Int = 12) =
        CurrentPeriodDto(
            paymentId = 1, creditsGranted = 12, creditsUsed = 12 - remaining,
            creditsRemaining = remaining, canBrowse = true, canBook = true,
            label = label, windowStart = start, windowEnd = end,
        )

    private fun me(current: CurrentPeriodDto?, upcoming: CurrentPeriodDto? = null) =
        MembershipMeDto(member(), membership(), current, upcoming)

    @Test fun monthName_extracts_first_word() {
        assertEquals("July", wallet("July Beta", null, null).monthName)
        assertEquals("August", wallet("August Influencer", null, null).monthName)
        assertNull(wallet(null, null, null).monthName)
    }

    @Test fun periodForClass_picks_wallet_by_class_month() {
        val july = wallet("July Beta", "2026-07-01T04:00:00Z", "2026-08-01T04:00:00Z")
        val august = wallet("August Beta", "2026-08-01T04:00:00Z", "2026-09-01T04:00:00Z")
        val both = me(july, august)
        // The class's month decides the wallet — not the day it is booked.
        assertEquals("July Beta", both.periodForClass("2026-07-20T18:00:00Z")?.label)
        assertEquals("August Beta", both.periodForClass("2026-08-04T18:00:00Z")?.label)
    }

    @Test fun periodForClass_falls_back_to_current_when_no_window_matches() {
        val july = wallet("July Beta", "2026-07-01T04:00:00Z", "2026-08-01T04:00:00Z")
        // A June class matches no window → fall back to current (keeps the CTA
        // enabled so the attempt reaches the server, which returns the popup).
        assertEquals("July Beta", me(july).periodForClass("2026-06-20T18:00:00Z")?.label)
    }

    @Test fun coveredMonthsPhrase_single_both_and_none() {
        val july = wallet("July Beta", "2026-07-01T04:00:00Z", "2026-08-01T04:00:00Z")
        val august = wallet("August Beta", "2026-08-01T04:00:00Z", "2026-09-01T04:00:00Z")
        assertEquals("July", me(july).coveredMonthsPhrase())
        assertEquals("July and August", me(july, august).coveredMonthsPhrase())
        assertNull(me(null).coveredMonthsPhrase())
    }
}
