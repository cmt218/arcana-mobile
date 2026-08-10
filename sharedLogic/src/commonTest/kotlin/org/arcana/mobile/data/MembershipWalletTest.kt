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

    @Test fun coveringPeriodForClass_matches_only_when_in_window() {
        val july = wallet("July Beta", "2026-07-01T04:00:00Z", "2026-08-01T04:00:00Z")
        // July class → covered by the July wallet.
        assertEquals("July Beta", me(july).coveringPeriodForClass("2026-07-20T18:00:00Z")?.label)
        // August class, July-only member → NOT covered (null, unlike periodForClass
        // which falls back). This is what drives "outside your membership".
        assertNull(me(july).coveringPeriodForClass("2026-08-04T18:00:00Z"))
    }

    @Test fun coveringPeriodForClass_two_wallets_cover_both_months() {
        val july = wallet("July Beta", "2026-07-01T04:00:00Z", "2026-08-01T04:00:00Z")
        val august = wallet("August Beta", "2026-08-01T04:00:00Z", "2026-09-01T04:00:00Z")
        val both = me(july, august)
        assertEquals("July Beta", both.coveringPeriodForClass("2026-07-20T18:00:00Z")?.label)
        assertEquals("August Beta", both.coveringPeriodForClass("2026-08-04T18:00:00Z")?.label)
    }

    @Test fun coveringPeriodForClass_rolling_wallet_covers_every_date() {
        // A rolling (null-window) wallet covers all dates — rolling subscribers
        // never hit the out-of-window state.
        val rolling = wallet(null, null, null)
        assertEquals(rolling, me(rolling).coveringPeriodForClass("2026-08-04T18:00:00Z"))
    }

    @Test fun classCohortMonthName_resolves_in_eastern_time() {
        assertEquals("July", classCohortMonthName("2026-07-20T18:00:00Z"))
        assertEquals("August", classCohortMonthName("2026-08-04T18:00:00Z"))
        assertNull(classCohortMonthName("not-a-date"))
    }
}
