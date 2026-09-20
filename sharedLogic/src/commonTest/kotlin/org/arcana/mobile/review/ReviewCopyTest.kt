package org.arcana.mobile.review

import kotlinx.datetime.LocalDate
import org.arcana.mobile.data.FeedbackAgainDto
import org.arcana.mobile.data.FeedbackScopeDto
import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewCopyTest {
    private val today = LocalDate(2026, 9, 15)

    @Test fun `prompt eyebrow names the day and the studio`() {
        assertEquals("Yesterday · Soto Method", reviewPromptEyebrow(prompt(1, endAt = "2026-09-14T19:00:00Z"), today))
        assertEquals("Today · Soto Method", reviewPromptEyebrow(prompt(1, endAt = "2026-09-15T07:00:00Z"), today))
        assertEquals("Sat Sep 12 · Soto Method", reviewPromptEyebrow(prompt(1, endAt = "2026-09-12T19:00:00Z"), today))
        // An unreadable end time drops the day rather than the whole line.
        assertEquals("Soto Method", reviewPromptEyebrow(prompt(1, endAt = "garbage"), today))
    }

    @Test fun `the feed hint counts its reviews`() {
        assertEquals("What members say · 1 review", whatMembersSayLabel(1))
        assertEquals("What members say · 12 reviews", whatMembersSayLabel(12))
    }

    @Test fun `class line drops an unknown instructor`() {
        assertEquals("Sculpt 50 with Jess", classWithInstructor("Sculpt 50", "Jess"))
        assertEquals("Sculpt 50", classWithInstructor("Sculpt 50", null))
        assertEquals("Sculpt 50", classWithInstructor("Sculpt 50", " "))
    }

    @Test fun `scope stats line drops what it cannot say`() {
        assertEquals("No feedback here yet.", scopeStatsLine(FeedbackScopeDto()))
        assertEquals("1 review", scopeStatsLine(FeedbackScopeDto(count = 1)))
        val full = FeedbackScopeDto(
            count = 6, again = FeedbackAgainDto(5, 1, 0),
            intensityAvg = 2.6, instructorAvg = 4.75, classAvg = 4.5, studioAvg = 4.0,
        )
        assertEquals(
            "6 reviews · 5 would take a class again · usually Hard · Instructors 4.8 · Classes 4.5 · Studio 4.0",
            scopeStatsLine(full),
        )
        assertEquals("2 reviews · 1 would take a class again", scopeStatsLine(FeedbackScopeDto(count = 2, again = FeedbackAgainDto(1, 0, 1))))
    }

    // One review is its own average, and the all-studios feed pools every
    // studio: neither opens with the averages card.
    @Test fun `averages open a scoped feed with more than one review`() {
        val stats = FeedbackScopeDto(type = "brand", value = "id-hot-yoga", count = 4, again = FeedbackAgainDto(3, 1, 0))
        assertEquals(true, showsAverages(stats))
        assertEquals(false, showsAverages(stats.copy(count = 1)))
        assertEquals(false, showsAverages(stats.copy(count = 0)))
        assertEquals(false, showsAverages(stats.copy(type = "all", value = "")))
        assertEquals("Average of 4 reviews", averagesHeading(4))
    }

    // A maybe is half a yes. Cole's case: two yeses and two maybes must not
    // read as a coin flip.
    @Test fun `again averages with a maybe counting half`() {
        fun score(yes: Int, maybe: Int, no: Int) = againScore(FeedbackScopeDto(again = FeedbackAgainDto(yes, maybe, no)))
        assertEquals(0.75, score(2, 2, 0))
        assertEquals(1.0, score(3, 0, 0))
        assertEquals(0.5, score(0, 4, 0))
        assertEquals(0.0, score(0, 0, 2))
        assertEquals(0.5, score(1, 0, 1))
        assertEquals(null, score(0, 0, 0))
    }

    // The averages card shows ONE mark for the scope.
    @Test fun `again on balance is the mark the answers add up to`() {
        fun mark(yes: Int, maybe: Int, no: Int) = againOnBalance(FeedbackScopeDto(again = FeedbackAgainDto(yes, maybe, no)))
        assertEquals("yes", mark(2, 2, 0))      // Cole's case: not a coin flip
        assertEquals("yes", mark(2, 0, 1))
        assertEquals("maybe", mark(0, 3, 0))
        assertEquals("maybe", mark(1, 0, 1))
        assertEquals("no", mark(0, 2, 1))
        assertEquals("no", mark(0, 0, 4))
        assertEquals(null, mark(0, 0, 0))
    }

    @Test fun `the averages strip rounds the way the stats line does`() {
        val scope = FeedbackScopeDto(type = "brand", count = 6, intensityAvg = 2.5, classAvg = 4.45)
        assertEquals(3, usualIntensity(scope))
        assertEquals("4.5", oneDecimal(4.45))
        assertEquals("4.0", oneDecimal(4.0))
        assertEquals(null, usualIntensity(FeedbackScopeDto()))
    }

    @Test fun `answers line reads left to right in the card's order`() {
        assertEquals("Would take it again", reviewAnswersLine(review(1)))
        val full = review(1, again = "no", intensity = 3).copy(instructorScore = 5, classScore = 4, studioScore = 3)
        assertEquals("Would not take it again · Hard · Instructor 5 · Class 4 · Studio 3", reviewAnswersLine(full))
        assertEquals("Might take it again · Easy", reviewAnswersLine(review(1, again = "maybe", intensity = 1)))
    }

    @Test fun `date label and class line`() {
        assertEquals("Sep 12", reviewDateLabel("2026-09-12T14:00:00Z"))
        assertEquals("2026-13-4", reviewDateLabel("2026-13-4"))
        assertEquals("Sculpt 50 at Soto Method", classLine("Sculpt 50", "", "Soto Method"))
        assertEquals("Hard", intensityLabel(3))
        assertEquals(null, intensityLabel(9))
        assertEquals("Maybe", againLabel("maybe"))
    }
}
