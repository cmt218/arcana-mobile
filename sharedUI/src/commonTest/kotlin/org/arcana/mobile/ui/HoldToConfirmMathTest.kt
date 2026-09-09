package org.arcana.mobile.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HoldToConfirmMathTest {
    @Test
    fun completes_only_when_the_ring_is_full() {
        assertFalse(holdCompleted(0f))
        assertFalse(holdCompleted(0.99f))
        assertTrue(holdCompleted(1f))
    }
}
