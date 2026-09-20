@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.analytics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.arcana.mobile.review.FakeReviewApi
import org.arcana.mobile.review.ReviewViewModel
import org.arcana.mobile.review.ScoreTarget
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Locks the reviews taxonomy: names and property keys are dashboard contracts. */
class ReviewTelemetryTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    @Test fun `event names are stable`() {
        assertEquals("review_prompt_shown", Telemetry.Events.REVIEW_PROMPT_SHOWN)
        assertEquals("review_step_saved", Telemetry.Events.REVIEW_STEP_SAVED)
        assertEquals("review_dismissed", Telemetry.Events.REVIEW_DISMISSED)
        assertEquals("review_completed", Telemetry.Events.REVIEW_COMPLETED)
        assertEquals("review_edited", Telemetry.Events.REVIEW_EDITED)
        assertEquals("feedback_feed_opened", Telemetry.Events.FEEDBACK_FEED_OPENED)
        assertEquals("feedback_item_tapped", Telemetry.Events.FEEDBACK_ITEM_TAPPED)
        assertEquals("FeedbackFeed", Telemetry.Screens.FEEDBACK_FEED)
    }

    @Test fun `a fresh card reports shown then each save then completion`() = runTest {
        val fake = FakeAnalytics()
        val vm = ReviewViewModel(42, "home", null, FakeReviewApi(), Telemetry(fake, NoopCrashReporter))
        vm.onShown()
        vm.answerAgain("yes")
        vm.answerIntensity(3)
        vm.setScore(ScoreTarget.Class, 4)
        vm.setComment("Great.")
        vm.done()
        assertEquals(
            listOf("review_prompt_shown", "review_step_saved", "review_step_saved", "review_step_saved", "review_step_saved", "review_completed"),
            fake.names(),
        )
        assertEquals(mapOf<String, Any?>("surface" to "home", "booking_id" to 42), fake.events[0].properties)
        assertEquals(
            mapOf<String, Any?>("step" to "again", "again" to "yes", "scores_set" to 0, "has_comment" to false),
            fake.events[1].properties,
        )
        assertEquals(
            mapOf<String, Any?>("step" to "comment", "again" to "yes", "intensity" to 3, "scores_set" to 1, "has_comment" to true),
            fake.events[4].properties,
        )
        assertEquals(4, fake.events[5].properties["steps"])
    }

    // A Reservations row re-enters composition on every scroll, and each entry
    // calls onShown again: the card must still count as shown once.
    @Test fun `a card scrolled back into view is shown once`() = runTest {
        val fake = FakeAnalytics()
        val vm = ReviewViewModel(42, "past", null, FakeReviewApi(), Telemetry(fake, NoopCrashReporter))
        repeat(3) { vm.onShown() }
        assertEquals(listOf("review_prompt_shown"), fake.names())
        assertEquals(mapOf<String, Any?>("surface" to "past", "booking_id" to 42), fake.events[0].properties)
    }

    @Test fun `direct telemetry methods carry their keys`() {
        val (telemetry, fake, _) = fakeTelemetry()
        telemetry.reviewDismissed("home")
        telemetry.feedbackFeedOpened("class_type", "class_detail")
        telemetry.feedbackItemTapped("all", "studio_page")
        assertEquals(mapOf<String, Any?>("surface" to "home"), fake.events[0].properties)
        assertEquals(mapOf<String, Any?>("scope_type" to "class_type", "source" to "class_detail"), fake.events[1].properties)
        assertEquals(mapOf<String, Any?>("scope_type" to "all", "target" to "studio_page"), fake.events[2].properties)
    }
}
