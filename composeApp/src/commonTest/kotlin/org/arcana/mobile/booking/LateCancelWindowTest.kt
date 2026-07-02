package org.arcana.mobile.booking

import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LateCancelWindowTest {

    @Test
    fun `formats whole-hour windows`() {
        assertEquals("1 hour", lateCancelWindowLabel(60))
        assertEquals("12 hours", lateCancelWindowLabel(720))
        assertEquals("24 hours", lateCancelWindowLabel(1440))
    }

    @Test
    fun `falls back to minutes for non-whole-hour windows`() {
        assertEquals("90 minutes", lateCancelWindowLabel(90))
        assertEquals("30 minutes", lateCancelWindowLabel(30))
    }

    @Test
    fun `booking copy names the concrete window when known`() {
        assertEquals(
            "Free to cancel up to 24 hours before class. After that, cancelling still costs the credit.",
            bookingCancelCopy(1440).text,
        )
    }

    @Test
    fun `booking copy bolds the window so it is not fine print`() {
        val copy = bookingCancelCopy(1440)
        // Exactly one emphasized span, and it covers the window label "24 hours".
        val bold = copy.spanStyles.single { it.item.fontWeight == FontWeight.Bold }
        assertEquals("24 hours", copy.text.substring(bold.start, bold.end))
    }

    @Test
    fun `booking copy falls back to generic when the window is unknown`() {
        val copy = bookingCancelCopy(null)
        assertTrue(copy.text.startsWith("Free to cancel until the studio cutoff"))
    }
}
