package org.arcana.mobile.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReviewerRedirectTest {

    private class Harness {
        val store = mutableMapOf<String, String>()
        val urlCalls = mutableListOf<String>()
        val redirect = ReviewerRedirect(
            setUrl = { urlCalls.add("set:$it") },
            resetUrl = { urlCalls.add("reset") },
            loadKey = { store[it] },
            saveKey = { k, v -> store[k] = v },
            deleteKey = { store.remove(it) },
        )
    }

    @Test
    fun `recognizes both reviewer emails and nothing else`() {
        assertTrue(ReviewerRedirect.isReviewer("apple-reviewer@test.com"))
        assertTrue(ReviewerRedirect.isReviewer("google-reviewer@test.com"))
        // Normalization: case and surrounding whitespace must not matter.
        assertTrue(ReviewerRedirect.isReviewer("  Apple-Reviewer@Test.com "))
        assertFalse(ReviewerRedirect.isReviewer("test@test.com"))
        assertFalse(ReviewerRedirect.isReviewer("member@example.com"))
        assertFalse(ReviewerRedirect.isReviewer(""))
    }

    @Test
    fun `reviewer login points the app at staging and persists the marker`() {
        val h = Harness()
        h.redirect.applyFor("apple-reviewer@test.com")
        assertEquals(listOf("set:${ReviewerRedirect.STAGING_URL}"), h.urlCalls)
        assertTrue(h.store.isNotEmpty())
    }

    @Test
    fun `non reviewer login without a marker touches nothing`() {
        val h = Harness()
        h.redirect.applyFor("member@example.com")
        assertEquals(emptyList(), h.urlCalls)
        assertTrue(h.store.isEmpty())
    }

    @Test
    fun `non reviewer login after a reviewer session resets to prod`() {
        val h = Harness()
        h.redirect.applyFor("google-reviewer@test.com")
        h.redirect.applyFor("member@example.com")
        assertEquals(listOf("set:${ReviewerRedirect.STAGING_URL}", "reset"), h.urlCalls)
        assertTrue(h.store.isEmpty())
    }

    @Test
    fun `sign out clears a reviewer redirect`() {
        val h = Harness()
        h.redirect.applyFor("apple-reviewer@test.com")
        h.redirect.onSessionEnded()
        assertEquals(listOf("set:${ReviewerRedirect.STAGING_URL}", "reset"), h.urlCalls)
        assertTrue(h.store.isEmpty())
    }

    @Test
    fun `sign out without a marker leaves a developer override alone`() {
        val h = Harness()
        h.redirect.onSessionEnded()
        assertEquals(emptyList(), h.urlCalls)
    }

}
