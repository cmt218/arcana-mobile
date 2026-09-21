package org.arcana.mobile.schedule

import org.arcana.mobile.data.ReviewDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What class detail shows about reviews, and above all whether the reserve
 * control (book, cancel) stays on screen.
 *
 * The server returns `my_review` for EVERY session of a combination the member
 * has reviewed (brand + class type + instructor), including ones that have not
 * happened yet. The screen once read "has my review" as "this class is over"
 * and hid the control, so a member who had reviewed a class could neither book
 * nor cancel its next session. These tests pin the rule that fixes it: only a
 * class that has ENDED gives its control up to the review card.
 */
class ClassDetailReviewPlacementTest {
    private val mine = ReviewDto(id = 7, bookingId = 40, again = "yes")

    @Test fun `an upcoming class the member has reviewed before keeps its reserve control`() {
        val placement = classDetailReviewPlacement(myReview = mine, promptEligible = false, reviewBookingId = null, isPast = false)
        assertTrue(placement.showsReserveControl, "the member must be able to book or cancel tomorrow's class")
        assertEquals(ReviewPlacement.Summary, placement.review)
        assertEquals(40, placement.bookingId, "edits go to the booking that carries the review")
    }

    @Test fun `a class that has ended gives the control's place to the review`() {
        val reviewed = classDetailReviewPlacement(myReview = mine, promptEligible = false, reviewBookingId = 41, isPast = true)
        assertFalse(reviewed.showsReserveControl)
        assertEquals(ReviewPlacement.Summary, reviewed.review)
        assertEquals(40, reviewed.bookingId)

        val unreviewed = classDetailReviewPlacement(myReview = null, promptEligible = true, reviewBookingId = 41, isPast = true)
        assertFalse(unreviewed.showsReserveControl)
        assertEquals(ReviewPlacement.Prompt, unreviewed.review)
        assertEquals(41, unreviewed.bookingId)
    }

    @Test fun `a class with nothing to review is untouched`() {
        for (isPast in listOf(false, true)) {
            val placement = classDetailReviewPlacement(myReview = null, promptEligible = false, reviewBookingId = null, isPast = isPast)
            assertTrue(placement.showsReserveControl, "past classes keep their inert CLASS ENDED control")
            assertEquals(ReviewPlacement.None, placement.review)
            assertNull(placement.bookingId)
        }
    }

    @Test fun `an eligible flag without a booking to post to shows nothing`() {
        val placement = classDetailReviewPlacement(myReview = null, promptEligible = true, reviewBookingId = null, isPast = true)
        assertEquals(ReviewPlacement.None, placement.review)
        assertTrue(placement.showsReserveControl)
    }

    @Test fun `a prompt never shows on a class that has not ended`() {
        // The server only says eligible once a class ends; a stale or wrong flag must not hide the control.
        val placement = classDetailReviewPlacement(myReview = null, promptEligible = true, reviewBookingId = 41, isPast = false)
        assertTrue(placement.showsReserveControl)
        assertEquals(ReviewPlacement.None, placement.review)
    }
}
