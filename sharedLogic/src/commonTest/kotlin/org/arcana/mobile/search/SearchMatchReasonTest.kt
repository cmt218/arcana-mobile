package org.arcana.mobile.search

import org.arcana.mobile.data.InstructorBriefDto
import org.arcana.mobile.schedule.pageOf
import org.arcana.mobile.schedule.toSessions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchMatchReasonTest {
    private fun session(
        templateName: String = "Power Yoga",
        studioName: String = "Barrys",
        locationName: String = "NoHo",
        instructorNames: List<String> = emptyList(),
    ) = pageOf(1).toSessions().single().let { base ->
        base.copy(
            template = base.template.copy(name = templateName),
            instructors = instructorNames.mapIndexed { i, name ->
                InstructorBriefDto(id = i + 1, name = name, photoUrl = "")
            },
            location = base.location.copy(
                name = locationName,
                studio = base.location.studio.copy(name = studioName),
            ),
        )
    }

    @Test fun `class name match needs no caption`() {
        assertNull(searchMatchReason(session(templateName = "Power Yoga"), "power"))
    }

    @Test fun `instructor match names the instructor`() {
        val s = session(instructorNames = listOf("Sarah Chen"))
        assertEquals("Matches instructor Sarah Chen", searchMatchReason(s, "sarah"))
    }

    @Test fun `studio match names the studio`() {
        assertEquals("Matches studio Barrys", searchMatchReason(session(), "barrys"))
    }

    @Test fun `location match names the neighborhood`() {
        assertEquals("Matches NoHo", searchMatchReason(session(), "noho"))
    }

    @Test fun `no field match yields no caption`() {
        assertNull(searchMatchReason(session(), "address-only-match"))
    }

    @Test fun `blank query yields no caption`() {
        assertNull(searchMatchReason(session(), "   "))
    }

    @Test fun `consecutive identical reasons show once per run`() {
        val sarah = session(instructorNames = listOf("Sarah Chen"))
        val reasons = searchMatchReasons(listOf(sarah, sarah, sarah), "sarah")

        assertEquals(listOf("Matches instructor Sarah Chen", null, null), reasons)
    }

    @Test fun `a run broken by an unexplained row restarts the caption`() {
        val sarah = session(instructorNames = listOf("Sarah Chen"))
        val named = session(templateName = "Sarah Signature Sculpt")
        val reasons = searchMatchReasons(listOf(sarah, named, sarah), "sarah")

        assertEquals(
            listOf("Matches instructor Sarah Chen", null, "Matches instructor Sarah Chen"),
            reasons,
        )
    }

    @Test fun `different reasons each show`() {
        val sarah = session(instructorNames = listOf("Sarah Chen"))
        val studio = session(studioName = "Sarah Studio")
        val reasons = searchMatchReasons(listOf(sarah, studio), "sarah")

        assertEquals(
            listOf("Matches instructor Sarah Chen", "Matches studio Sarah Studio"),
            reasons,
        )
    }
}
