package org.arcana.mobile.signup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the confirm-to-leave copy shown when system back is pressed during
 * signup (regression NAV-13). There is no Compose UI test harness in this
 * module, so the copy lives as plain strings and is asserted here.
 */
class LeaveSignupCopyTest {

    private val allCopy: List<String>
        get() = listOf(
            LeaveSignupCopy.TITLE,
            LeaveSignupCopy.CONFIRM,
            LeaveSignupCopy.DISMISS,
        ) + SignupStep.entries.map { LeaveSignupCopy.body(it) }

    @Test
    fun `every string is member facing and non blank`() {
        allCopy.forEach { assertTrue(it.isNotBlank(), "blank copy string") }
    }

    @Test
    fun `no em or en dashes anywhere in the copy`() {
        // House rule: dashes read as machine-written. Use a comma or a period.
        allCopy.forEach { text ->
            assertFalse(text.contains('—'), "em dash in: $text")
            assertFalse(text.contains('–'), "en dash in: $text")
        }
    }

    @Test
    fun `each step explains what that screen actually loses`() {
        val survey = LeaveSignupCopy.body(SignupStep.Survey)
        val claim = LeaveSignupCopy.body(SignupStep.Claim)
        // The survey is discarded wholesale; the claim form sits after an
        // already-submitted survey, so only the typed fields go.
        assertTrue(survey.contains("answers"), "survey copy should name the answers")
        assertTrue(claim.contains("details"), "claim copy should name the details")
        assertTrue(survey != claim, "the two steps must not share one message")
    }

    @Test
    fun `both signup steps are covered`() {
        assertEquals(setOf(SignupStep.Survey, SignupStep.Claim), SignupStep.entries.toSet())
    }
}
