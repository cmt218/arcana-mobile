package org.arcana.mobile.data

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

/** The raw JSON blocks below are the wire contract examples from
 *  `docs/superpowers/specs/2026-06-10-schedule-loading-design.md`
 *  (§GET /api/v1/classes/overview/, §GET /api/v1/classes/sessions/). */
class SchedulePageDtoTest {

    @Test
    fun `overview decodes categories`() {
        val raw = """
          {"studios": [], "categories": [
            {"slug": "yoga", "name": "Yoga"},
            {"slug": "cycle", "name": "Cycle"}
          ]}
        """.trimIndent()
        val dto = json.decodeFromString(ScheduleOverviewDto.serializer(), raw)
        assertEquals(2, dto.categories.size)
        assertEquals("yoga", dto.categories[0].slug)
        assertEquals("Yoga", dto.categories[0].name)
    }

    @Test
    fun `parses the overview shape from the spec example`() {
        val raw = """
          {
            "studios": [
              {"id": 3, "slug": "solidcore", "name": "SolidCore", "logo_url": "", "primary_color": "#1A1A1A",
               "publishes_capacity": true, "last_successful_sync_at": "2026-06-10T07:32:11-04:00",
               "locations": [{"id": 41, "name": "SolidCore Williamsburg", "timezone": "America/New_York"}]}
            ]
          }
        """.trimIndent()
        val overview = json.decodeFromString(ScheduleOverviewDto.serializer(), raw)
        val studio = overview.studios.single()
        assertEquals(3, studio.id)
        assertEquals("solidcore", studio.slug)
        assertEquals("SolidCore", studio.name)
        assertEquals("", studio.logoUrl)
        assertEquals("#1A1A1A", studio.primaryColor)
        assertTrue(studio.publishesCapacity)
        assertEquals("2026-06-10T07:32:11-04:00", studio.lastSuccessfulSyncAt)
        val location = studio.locations.single()
        assertEquals(41, location.id)
        assertEquals("SolidCore Williamsburg", location.name)
        assertEquals("America/New_York", location.timezone)
    }

    @Test
    fun `overview studio missing optional fields defaults cleanly`() {
        val raw = """{"studios":[{"id":3,"slug":"solidcore","name":"SolidCore"}]}"""
        val overview = json.decodeFromString(ScheduleOverviewDto.serializer(), raw)
        val studio = overview.studios.single()
        assertEquals("", studio.logoUrl)
        assertEquals("", studio.primaryColor)
        assertTrue(studio.publishesCapacity)
        assertNull(studio.lastSuccessfulSyncAt)
        assertTrue(studio.locations.isEmpty())
    }

    @Test
    fun `parses the sessions page shape from the spec example`() {
        val raw = """
          {
            "sessions": [
              {"id": 9912, "start_at": "2026-06-10T09:00:00-04:00", "end_at": "2026-06-10T09:50:00-04:00",
               "duration_minutes": 50, "status": "scheduled",
               "platform_capacity": 20, "platform_booked": 14,
               "arcana_spots_offered": 20, "arcana_spots_available": 6,
               "template_id": 311, "location_id": 41, "instructor_ids": [77]}
            ],
            "templates":   {"311": {"id": 311, "name": "Foundation 50", "modality": "pilates",
                                    "hero_image_url": "", "spot_selection_mode": "none"}},
            "locations":   {"41": {"id": 41, "name": "SolidCore Williamsburg",
                                    "timezone": "America/New_York", "studio_id": 3}},
            "studios":     {"3": {"id": 3, "slug": "solidcore", "name": "SolidCore", "logo_url": "",
                                    "primary_color": "#1A1A1A", "publishes_capacity": true,
                                    "last_successful_sync_at": "2026-06-10T07:32:11-04:00"}},
            "instructors": {"77": {"id": 77, "name": "Maya R", "photo_url": ""}},
            "next_cursor": "MjAyNi0wNi0xMFQwOTowMDowMC0wNDowMHw5OTEy"
          }
        """.trimIndent()
        val page = json.decodeFromString(SchedulePageDto.serializer(), raw)

        val session = page.sessions.single()
        assertEquals(9912, session.id)
        assertEquals("2026-06-10T09:00:00-04:00", session.startAt)
        assertEquals("2026-06-10T09:50:00-04:00", session.endAt)
        assertEquals(50, session.durationMinutes)
        assertEquals("scheduled", session.status)
        assertEquals(20, session.platformCapacity)
        assertEquals(14, session.platformBooked)
        assertEquals(20, session.arcanaSpotsOffered)
        assertEquals(6, session.arcanaSpotsAvailable)
        assertEquals(311, session.templateId)
        assertEquals(41, session.locationId)
        assertEquals(listOf(77), session.instructorIds)

        // Lookup maps are keyed by STRINGIFIED ids (JSON object keys).
        val template = page.templates.getValue("311")
        assertEquals(311, template.id)
        assertEquals("Foundation 50", template.name)
        assertEquals("pilates", template.modality)
        assertEquals("none", template.spotSelectionMode)

        val location = page.locations.getValue("41")
        assertEquals(41, location.id)
        assertEquals("SolidCore Williamsburg", location.name)
        assertEquals("America/New_York", location.timezone)
        assertEquals(3, location.studioId)

        val studio = page.studios.getValue("3")
        assertEquals(3, studio.id)
        assertEquals("solidcore", studio.slug)
        assertEquals("#1A1A1A", studio.primaryColor)
        assertTrue(studio.publishesCapacity)
        assertEquals("2026-06-10T07:32:11-04:00", studio.lastSuccessfulSyncAt)

        val instructor = page.instructors.getValue("77")
        assertEquals(77, instructor.id)
        assertEquals("Maya R", instructor.name)
        assertEquals("", instructor.photoUrl)

        assertEquals("MjAyNi0wNi0xMFQwOTowMDowMC0wNDowMHw5OTEy", page.nextCursor)
    }

    @Test
    fun `last page has a null next_cursor`() {
        val raw = """
          {"sessions": [], "templates": {}, "locations": {}, "studios": {}, "instructors": {},
           "next_cursor": null}
        """.trimIndent()
        val page = json.decodeFromString(SchedulePageDto.serializer(), raw)
        assertTrue(page.sessions.isEmpty())
        assertNull(page.nextCursor)
    }

    @Test
    fun `absent next_cursor defaults to null`() {
        val page = json.decodeFromString(SchedulePageDto.serializer(), """{"sessions": []}""")
        assertNull(page.nextCursor)
    }

    @Test
    fun `unknown keys on both endpoint shapes are ignored`() {
        val overviewRaw = """
          {"studios":[{"id":3,"slug":"solidcore","name":"SolidCore","tier":"premium"}],
           "server_time":"2026-06-10T00:00:00Z"}
        """.trimIndent()
        val overview = json.decodeFromString(ScheduleOverviewDto.serializer(), overviewRaw)
        assertEquals("solidcore", overview.studios.single().slug)

        val pageRaw = """
          {"sessions":[{"id":1,"start_at":"2026-06-10T09:00:00-04:00","end_at":"2026-06-10T09:50:00-04:00",
            "duration_minutes":50,"status":"scheduled","platform_capacity":1,"platform_booked":0,
            "arcana_spots_offered":1,"arcana_spots_available":1,"template_id":2,"location_id":3,
            "instructor_ids":[],"waitlist_depth":4}],
           "page_generated_at":"2026-06-10T00:00:00Z"}
        """.trimIndent()
        val page = json.decodeFromString(SchedulePageDto.serializer(), pageRaw)
        assertEquals(1, page.sessions.single().id)
    }
}
