package org.arcana.mobile.search

import kotlin.test.Test
import kotlin.test.assertEquals

class RecentSearchesTest {
    private fun store(): RecentSearches {
        var stored: String? = null
        return RecentSearches(
            loadRaw = { stored },
            saveRaw = { stored = it },
            deleteRaw = { stored = null },
        )
    }

    @Test fun `slow forward typing collapses intermediates into the final query`() {
        val recents = store()
        for (q in listOf("si", "sign", "signa", "signatur", "signature")) recents.record(q)

        assertEquals(listOf("signature"), recents.all())
    }

    @Test fun `backspacing collapses extensions into the shorter query`() {
        val recents = store()
        for (q in listOf("Emily bud", "Emily bu")) recents.record(q)

        assertEquals(listOf("Emily bu"), recents.all())
    }

    @Test fun `distinct queries are both kept newest first`() {
        val recents = store()
        recents.record("pilates")
        recents.record("boxing")

        assertEquals(listOf("boxing", "pilates"), recents.all())
    }

    @Test fun `collapse is case insensitive`() {
        val recents = store()
        recents.record("emily")
        recents.record("Emily Budd")

        assertEquals(listOf("Emily Budd"), recents.all())
    }

    @Test fun `clear empties the list`() {
        val recents = store()
        recents.record("pilates")
        recents.record("boxing")
        recents.clear()

        assertEquals(emptyList(), recents.all())
    }

    @Test fun `capped at max entries`() {
        val recents = store()
        for (i in 1..10) recents.record("query number $i")

        assertEquals(RecentSearches.MAX_ENTRIES, recents.all().size)
        assertEquals("query number 10", recents.all().first())
    }
}
