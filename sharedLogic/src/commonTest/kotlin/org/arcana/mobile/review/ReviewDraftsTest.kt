package org.arcana.mobile.review

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReviewDraftsTest {
    @Test fun `a draft round trips and clears by review`() {
        val drafts = ReviewDrafts.inMemory()
        assertNull(drafts.load(1))
        drafts.save(1, "Line one.\nLine \"two\", with a comma.")
        drafts.save(2, "Other")
        assertEquals("Line one.\nLine \"two\", with a comma.", drafts.load(1))
        drafts.clear(1)
        assertNull(drafts.load(1))
        assertEquals("Other", drafts.load(2))
        drafts.clearAll()
        assertNull(drafts.load(2))
    }

    @Test fun `a blank draft is no draft`() {
        val drafts = ReviewDrafts.inMemory()
        drafts.save(1, "Something")
        drafts.save(1, "   ")
        assertNull(drafts.load(1))
    }

    // One storage key, so it cannot grow without bound: the oldest goes first
    // and rewriting a draft counts as touching it.
    @Test fun `only the newest few drafts are kept`() {
        val drafts = ReviewDrafts.inMemory()
        (1..ReviewDrafts.MAX_DRAFTS).forEach { drafts.save(it, "draft $it") }
        drafts.save(1, "draft 1 again")
        drafts.save(99, "newest")
        assertNull(drafts.load(2))
        assertEquals("draft 1 again", drafts.load(1))
        assertEquals("newest", drafts.load(99))
    }

    @Test fun `unreadable storage reads as no drafts`() {
        var raw: String? = "not json"
        val drafts = ReviewDrafts(loadRaw = { raw }, saveRaw = { raw = it }, deleteRaw = { raw = null })
        assertNull(drafts.load(1))
        drafts.save(1, "Recovered")
        assertEquals("Recovered", drafts.load(1))
    }
}
