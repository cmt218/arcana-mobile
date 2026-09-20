@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.arcana.mobile.review

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.arcana.mobile.analytics.FakeAnalytics
import org.arcana.mobile.analytics.NoopCrashReporter
import org.arcana.mobile.analytics.Telemetry
import org.arcana.mobile.networking.ApiHttpError
import org.arcana.mobile.networking.CONNECTION_FAILED
import org.arcana.mobile.networking.ReviewError
import org.arcana.mobile.networking.SERVER_FAILED
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReviewViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    private fun vm(
        api: FakeReviewApi,
        initial: org.arcana.mobile.data.ReviewDto? = null,
        fake: FakeAnalytics = FakeAnalytics(),
        surface: String = "home",
        drafts: ReviewDrafts = ReviewDrafts.inMemory(),
    ) = ReviewViewModel(
        bookingId = 42, surface = surface, initial = initial, api = api,
        telemetry = Telemetry(fake, NoopCrashReporter), drafts = drafts,
    )

    @Test fun `step one posts and moves to intensity`() = runTest {
        val api = FakeReviewApi()
        val vm = vm(api)
        assertEquals(ReviewStep.Again, vm.uiState.value.step)
        vm.answerAgain("maybe")
        assertEquals(listOf(Triple(42, "maybe", "home")), api.created)
        val s = vm.uiState.value
        assertEquals("maybe", s.review?.again)
        assertEquals(ReviewStep.Intensity, s.step)
        assertFalse(s.saving)
    }

    @Test fun `every later answer is one patch of one field`() = runTest {
        val api = FakeReviewApi()
        val vm = vm(api)
        vm.answerAgain("yes")
        vm.answerIntensity(3)
        assertEquals(ReviewStep.Recommend, vm.uiState.value.step)
        vm.setScore(ScoreTarget.Instructor, 5)
        vm.setScore(ScoreTarget.Class, 4)
        vm.setScore(ScoreTarget.Studio, 4)
        vm.setScore(ScoreTarget.Studio, 4)   // unchanged: no request
        assertEquals(listOf(3, 5, 4, 4), api.patched.map { it.second.values.single().toString().toInt() })
        assertEquals(listOf("intensity", "instructor_score", "class_score", "studio_score"), api.patched.map { it.second.keys.single() })
        assertEquals(1, api.created.size)
    }

    @Test fun `done saves a dirty comment then closes and reports completion`() = runTest {
        val api = FakeReviewApi()
        val fake = FakeAnalytics()
        val vm = vm(api, fake = fake)
        vm.answerAgain("yes")
        vm.answerIntensity(2)
        vm.setComment("  Loved the pace.  ")
        assertTrue(vm.uiState.value.commentDirty)
        vm.done()
        assertEquals("Loved the pace.", api.patched.last().second.str("comment"))
        assertEquals(ReviewStep.Done, vm.uiState.value.step)
        // The card shows "saved" for a beat before Home removes it.
        assertTrue(vm.uiState.value.settled)
        assertEquals(1, vm.uiState.value.doneCount)
        val completed = fake.first("review_completed")!!
        assertEquals(3, completed.properties["steps"])
        assertEquals(listOf("review_prompt_shown", "review_step_saved", "review_step_saved", "review_step_saved", "review_completed").drop(1),
            fake.names().filter { it != "review_prompt_shown" })
    }

    @Test fun `done with nothing pending closes without a request`() = runTest {
        val api = FakeReviewApi()
        val vm = vm(api)
        vm.answerAgain("yes")
        vm.done()
        assertTrue(api.patched.isEmpty())
        assertEquals(ReviewStep.Done, vm.uiState.value.step)
    }

    @Test fun `an existing review opens in edit mode and edits patch`() = runTest {
        val api = FakeReviewApi()
        val existing = review(id = 9, bookingId = 42, again = "yes", intensity = 3, comment = "Hot.")
        api.reviews[42] = existing
        val fake = FakeAnalytics()
        val vm = vm(api, initial = existing, fake = fake)
        val s = vm.uiState.value
        assertTrue(s.editing)
        assertEquals(ReviewStep.Recommend, s.step)
        assertEquals("Hot.", s.comment)
        vm.answerAgain("no")
        assertTrue(api.created.isEmpty())
        assertEquals("no", api.patched.single().second.str("again"))
        assertEquals(listOf("review_step_saved", "review_edited"), fake.names())
        vm.done()
        assertNull(fake.first("review_completed"))
    }

    // ---- A tap shows at once; its request follows (no dimming, no flash) ----

    @Test fun `a tap shows before its request lands and nothing dims it`() = runTest {
        val api = FakeReviewApi()
        val vm = vm(api)
        vm.answerAgain("yes")
        vm.answerIntensity(2)
        api.gate = CompletableDeferred()
        vm.setScore(ScoreTarget.Class, 4)
        // On screen already, with the request still held.
        assertEquals(4, vm.uiState.value.review?.classScore)
        assertTrue(vm.uiState.value.saving)
        assertTrue(api.patched.none { "class_score" in it.second })
        api.gate!!.complete(Unit)
        assertEquals(4, vm.uiState.value.review?.classScore)
        assertFalse(vm.uiState.value.saving)
    }

    @Test fun `step one shows its pick while the review is being created`() = runTest {
        val api = FakeReviewApi().apply { gate = CompletableDeferred() }
        val vm = vm(api)
        vm.answerAgain("maybe")
        vm.answerAgain("no")   // a second tap cannot create a second review
        assertEquals("maybe", vm.uiState.value.again)
        assertEquals(ReviewStep.Again, vm.uiState.value.step)
        api.gate!!.complete(Unit)
        assertEquals(listOf(Triple(42, "maybe", "home")), api.created)
        assertEquals(ReviewStep.Intensity, vm.uiState.value.step)
        assertNull(vm.uiState.value.pendingAgain)
    }

    @Test fun `quick taps go out in order and the last one wins`() = runTest {
        val api = FakeReviewApi()
        val vm = vm(api)
        vm.answerAgain("yes")
        api.gate = CompletableDeferred()
        vm.setScore(ScoreTarget.Studio, 2)
        vm.setScore(ScoreTarget.Studio, 5)
        assertEquals(5, vm.uiState.value.review?.studioScore)
        api.gate!!.complete(Unit)
        assertEquals(listOf(2, 5), api.patched.map { it.second.int("studio_score") })
        assertEquals(5, vm.uiState.value.review?.studioScore)
    }

    @Test fun `a failed answer falls back to the last confirmed one`() = runTest {
        val api = FakeReviewApi()
        val vm = vm(api)
        vm.answerAgain("yes")
        vm.setScore(ScoreTarget.Class, 3)
        api.failWith = ApiHttpError(503)
        vm.setScore(ScoreTarget.Class, 5)
        assertEquals(3, vm.uiState.value.review?.classScore)
        assertEquals(SERVER_FAILED, vm.uiState.value.failureCode)
        api.failWith = null
        vm.setScore(ScoreTarget.Class, 5)
        assertEquals(5, vm.uiState.value.review?.classScore)
        assertNull(vm.uiState.value.failureCode)
    }

    // ---- Class detail keeps the card open: Done settles it, a change reopens it ----

    @Test fun `on class detail done settles the card and keeps every answer`() = runTest {
        val api = FakeReviewApi()
        val fake = FakeAnalytics()
        val vm = vm(api, fake = fake, surface = "detail")
        vm.answerAgain("yes")
        vm.answerIntensity(3)
        assertFalse(vm.uiState.value.settled)
        vm.done()
        val s = vm.uiState.value
        assertTrue(s.settled && s.editing)
        assertEquals(ReviewStep.Recommend, s.step)
        assertEquals(1, s.doneCount)
        assertEquals(1, fake.names().count { it == "review_completed" })
        vm.done()   // settled: nothing to do, no second haptic
        assertEquals(1, vm.uiState.value.doneCount)
    }

    @Test fun `an existing review opens settled and any change unsettles it`() = runTest {
        val api = FakeReviewApi()
        val existing = review(id = 9, bookingId = 42, again = "yes", intensity = 3, comment = "Hot.")
        api.reviews[42] = existing
        val vm = vm(api, initial = existing, surface = "detail")
        assertTrue(vm.uiState.value.settled)
        vm.setScore(ScoreTarget.Instructor, 5)
        assertFalse(vm.uiState.value.settled)
        vm.done()
        assertTrue(vm.uiState.value.settled)
        vm.setComment("Hot. And loud.")
        assertFalse(vm.uiState.value.settled)
        vm.setComment("Hot.")   // typed back to what is saved
        assertFalse(vm.uiState.value.settled)
        vm.setComment("Hot. And loud.")
        vm.done()
        assertEquals("Hot. And loud.", api.patched.last().second.str("comment"))
        assertTrue(vm.uiState.value.settled)
        assertEquals(2, vm.uiState.value.doneCount)
    }

    @Test fun `done waits for an answer still in flight and stays open if it fails`() = runTest {
        val api = FakeReviewApi()
        val vm = vm(api, surface = "detail")
        vm.answerAgain("yes")
        api.gate = CompletableDeferred()
        api.failWith = ApiHttpError(500)
        vm.setScore(ScoreTarget.Class, 5)
        vm.done()
        assertTrue(vm.uiState.value.finishing)
        api.gate!!.complete(Unit)
        val s = vm.uiState.value
        assertFalse(s.finishing)
        assertFalse(s.settled)
        assertEquals(0, s.doneCount)
        assertEquals(SERVER_FAILED, s.failureCode)
    }

    @Test fun `a named refusal closes the card by name`() = runTest {
        val api = FakeReviewApi().apply { failWith = ReviewError("not_eligible") }
        val vm = vm(api)
        vm.answerAgain("yes")
        assertEquals("not_eligible", vm.uiState.value.refusedCode)
        assertNull(vm.uiState.value.failureCode)
    }

    @Test fun `a transport failure keeps the step and shows the error type`() = runTest {
        val api = FakeReviewApi().apply { failWith = ApiHttpError(503) }
        val vm = vm(api)
        vm.answerAgain("yes")
        val s = vm.uiState.value
        assertEquals(SERVER_FAILED, s.failureCode)
        assertEquals(ReviewStep.Again, s.step)
        assertNull(s.review)
        api.failWith = null
        vm.answerAgain("yes")
        assertEquals(ReviewStep.Intensity, vm.uiState.value.step)
        assertNull(vm.uiState.value.failureCode)
    }

    @Test fun `save comment on focus loss sends only a changed comment`() = runTest {
        val api = FakeReviewApi()
        val vm = vm(api)
        vm.answerAgain("yes")
        vm.saveComment()
        assertTrue(api.patched.isEmpty())
        vm.setComment("Great room.")
        vm.saveComment()
        assertEquals("Great room.", api.patched.single().second.str("comment"))
        vm.saveComment()
        assertEquals(1, api.patched.size)
    }

    // ---- The nightmare case: a long comment, Done, no signal ------------------

    @Test fun `a failed save keeps every word and says why once`() = runTest {
        val api = FakeReviewApi()
        val vm = vm(api, surface = "detail")
        vm.answerAgain("yes")
        val words = "w".repeat(COMMENT_MAX_LENGTH)
        vm.setComment(words)
        api.failWith = Exception("no route to host")
        vm.done()
        val s = vm.uiState.value
        assertEquals(words, s.comment)
        assertEquals(CONNECTION_FAILED, s.failureCode)
        assertTrue(s.commentDirty)          // Done stays awake
        assertFalse(s.settled || s.finishing)
        assertEquals(0, s.doneCount)
        vm.failureShown()
        assertNull(vm.uiState.value.failureCode)
        // Back in signal: the same tap sends the same words.
        api.failWith = null
        vm.done()
        assertEquals(words, api.patched.last().second.str("comment"))
        assertTrue(vm.uiState.value.settled)
    }

    // Done dismisses the keyboard first, and losing focus sends the comment. If
    // that send fails, the screen shows the notice and clears the code at once:
    // Done must still not read "saved" over words the server never got.
    @Test fun `done never reads saved over a comment that failed to send`() = runTest {
        val api = FakeReviewApi()
        val vm = vm(api, surface = "detail")
        vm.answerAgain("yes")
        vm.setComment("The words that matter.")
        api.gate = CompletableDeferred()
        api.failWith = Exception("offline")
        vm.saveComment()            // focus left the field
        vm.done()                   // the same tap
        vm.failureShown()           // the screen was quick
        api.gate!!.complete(Unit)
        vm.failureShown()
        val s = vm.uiState.value
        assertFalse(s.settled)
        assertFalse(s.finishing)
        assertEquals(0, s.doneCount)
        assertEquals("The words that matter.", s.comment)
        assertTrue(s.commentDirty)
    }

    @Test fun `words the server never got come back when the card reopens`() = runTest {
        val api = FakeReviewApi()
        val drafts = ReviewDrafts.inMemory()
        val existing = review(id = 9, bookingId = 42, comment = "Hot.")
        api.reviews[42] = existing
        val first = vm(api, initial = existing, surface = "detail", drafts = drafts)
        first.setComment("Hot. And the playlist was perfect.")
        api.failWith = ApiHttpError(503)
        first.done()
        assertEquals("Hot. And the playlist was perfect.", drafts.load(9))

        // The member gives up and leaves; later the class opens again.
        api.failWith = null
        val second = vm(api, initial = existing, surface = "detail", drafts = drafts)
        assertEquals("Hot. And the playlist was perfect.", second.uiState.value.comment)
        assertFalse(second.uiState.value.settled)   // Done is awake: there is something to send
        second.done()
        assertEquals("Hot. And the playlist was perfect.", api.patched.last().second.str("comment"))
        assertNull(drafts.load(9))                  // the server has it now
    }

    @Test fun `typing is kept once it pauses without waiting for a save`() = runTest {
        val api = FakeReviewApi()
        val drafts = ReviewDrafts.inMemory()
        val vm = vm(api, drafts = drafts)
        vm.answerAgain("yes")
        vm.setComment("Half a thou")
        assertNull(drafts.load(100))                // mid-typing: no Keychain write per keystroke
        testScheduler.advanceTimeBy(1_000)
        assertEquals("Half a thou", drafts.load(100))
        vm.setComment("")
        testScheduler.advanceTimeBy(1_000)
        assertNull(drafts.load(100))                // deleting it all leaves nothing behind
    }

    @Test fun `the comment is capped at the server length`() = runTest {
        val vm = vm(FakeReviewApi())
        vm.setComment("x".repeat(COMMENT_MAX_LENGTH + 50))
        assertEquals(COMMENT_MAX_LENGTH, vm.uiState.value.comment.length)
    }
}
