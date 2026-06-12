package org.arcana.mobile.schedule

import org.arcana.mobile.data.InstructorBriefDto
import org.arcana.mobile.data.LocationFlatDto
import org.arcana.mobile.data.SchedulePageDto
import org.arcana.mobile.data.SessionFlatDto
import org.arcana.mobile.data.StudioBriefDto
import org.arcana.mobile.data.TemplateBriefDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScheduleMapperTest {

    private fun flatSession(
        id: Int = 9912,
        templateId: Int = 311,
        locationId: Int = 41,
        instructorIds: List<Int> = listOf(77),
    ) = SessionFlatDto(
        id = id,
        startAt = "2026-06-10T09:00:00-04:00",
        endAt = "2026-06-10T09:50:00-04:00",
        durationMinutes = 50,
        status = "scheduled",
        platformCapacity = 20,
        platformBooked = 14,
        arcanaSpotsOffered = 20,
        arcanaSpotsAvailable = 6,
        templateId = templateId,
        locationId = locationId,
        instructorIds = instructorIds,
    )

    private val template = TemplateBriefDto(
        id = 311, name = "Foundation 50", modality = "pilates",
        heroImageUrl = "", spotSelectionMode = "none",
    )
    private val studio = StudioBriefDto(
        id = 3, slug = "solidcore", name = "SolidCore", logoUrl = "",
        primaryColor = "#1A1A1A", lastSuccessfulSyncAt = "2026-06-10T07:32:11-04:00",
    )
    private val location = LocationFlatDto(
        id = 41, name = "SolidCore Williamsburg", timezone = "America/New_York", studioId = 3,
    )
    private val instructor = InstructorBriefDto(id = 77, name = "Maya R", photoUrl = "")

    private fun page(
        sessions: List<SessionFlatDto>,
        templates: Map<String, TemplateBriefDto> = mapOf("311" to template),
        locations: Map<String, LocationFlatDto> = mapOf("41" to location),
        studios: Map<String, StudioBriefDto> = mapOf("3" to studio),
        instructors: Map<String, InstructorBriefDto> = mapOf("77" to instructor),
    ) = SchedulePageDto(
        sessions = sessions,
        templates = templates,
        locations = locations,
        studios = studios,
        instructors = instructors,
    )

    @Test
    fun `rejoins one session - every field lands on the nested graph`() {
        val result = page(listOf(flatSession())).toSessions()

        val session = result.single()
        assertEquals(9912, session.id)
        assertEquals("2026-06-10T09:00:00-04:00", session.startAt)
        assertEquals("2026-06-10T09:50:00-04:00", session.endAt)
        assertEquals(50, session.durationMinutes)
        assertEquals("scheduled", session.status)
        assertEquals(20, session.platformCapacity)
        assertEquals(14, session.platformBooked)
        assertEquals(20, session.arcanaSpotsOffered)
        assertEquals(6, session.arcanaSpotsAvailable)
        assertEquals(template, session.template)
        assertEquals(listOf(instructor), session.instructors)
        assertEquals(41, session.location.id)
        assertEquals("SolidCore Williamsburg", session.location.name)
        assertEquals("America/New_York", session.location.timezone)
        assertEquals(studio, session.location.studio)
    }

    @Test
    fun `session referencing a missing template is dropped - others survive`() {
        val result = page(
            listOf(flatSession(id = 1, templateId = 999), flatSession(id = 2)),
        ).toSessions()

        assertEquals(listOf(2), result.map { it.id })
    }

    @Test
    fun `session referencing a missing location is dropped - others survive`() {
        val result = page(
            listOf(flatSession(id = 1, locationId = 999), flatSession(id = 2)),
        ).toSessions()

        assertEquals(listOf(2), result.map { it.id })
    }

    @Test
    fun `session whose location references a missing studio is dropped - others survive`() {
        val orphanLocation = location.copy(id = 55, studioId = 888)
        val result = page(
            listOf(flatSession(id = 1, locationId = 55), flatSession(id = 2)),
            locations = mapOf("41" to location, "55" to orphanLocation),
        ).toSessions()

        assertEquals(listOf(2), result.map { it.id })
    }

    @Test
    fun `missing instructor id omits the instructor but keeps the session`() {
        val result = page(
            listOf(flatSession(instructorIds = listOf(999, 77))),
        ).toSessions()

        val session = result.single()
        assertEquals(listOf(instructor), session.instructors)
    }

    @Test
    fun `empty page maps to an empty list`() {
        assertTrue(page(emptyList()).toSessions().isEmpty())
    }
}
