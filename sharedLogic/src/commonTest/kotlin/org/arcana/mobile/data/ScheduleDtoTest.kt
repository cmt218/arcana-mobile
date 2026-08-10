package org.arcana.mobile.data

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

class ScheduleDtoTest {

    @Test
    fun `parses a session whose template carries spot preference options`() {
        val raw = """
          {
            "id": 482, "start_at": "2026-07-07T10:00:00Z", "end_at": "2026-07-07T10:50:00Z",
            "duration_minutes": 50, "status": "scheduled",
            "platform_capacity": 20, "platform_booked": 14,
            "arcana_spots_offered": 20, "arcana_spots_available": 6,
            "template": {"id": 311, "name": "RUN x LIFT", "modality": "bootcamp",
                         "hero_image_url": "", "spot_selection_mode": "none",
                         "spot_preference_options": ["Bag", "Bench"],
                         "spot_preference_label": "Pick your station"},
            "instructors": [], "location": {"id": 41, "name": "Barry's Chelsea",
              "timezone": "America/New_York",
              "studio": {"id": 3, "slug": "barrys", "name": "Barry's", "logo_url": "", "primary_color": "#000000"}}
          }
        """.trimIndent()
        val session = json.decodeFromString(ScheduleSessionDto.serializer(), raw)
        val template = session.template
        assertEquals(listOf("Bag", "Bench"), template.spotPreferenceOptions)
        assertEquals("Pick your station", template.spotPreferenceLabel)
    }

    @Test
    fun `studio brief carries the resolved late-cancel window`() {
        val raw = """
          {"id": 3, "slug": "nofar-method", "name": "nofar method",
           "logo_url": "", "primary_color": "#000000",
           "late_cancel_cutoff_minutes": 1440}
        """.trimIndent()
        val studio = json.decodeFromString(StudioBriefDto.serializer(), raw)
        assertEquals(1440, studio.lateCancelCutoffMinutes)
    }

    @Test
    fun `studio brief from an older server without the window is null`() {
        val raw = """
          {"id": 3, "slug": "barrys", "name": "Barry's",
           "logo_url": "", "primary_color": "#000000"}
        """.trimIndent()
        val studio = json.decodeFromString(StudioBriefDto.serializer(), raw)
        assertNull(studio.lateCancelCutoffMinutes)
    }

    @Test
    fun `template without spot preference fields defaults cleanly`() {
        val raw = """
          {
            "id": 482, "start_at": "2026-07-07T10:00:00Z", "end_at": "2026-07-07T10:50:00Z",
            "duration_minutes": 50, "status": "scheduled",
            "platform_capacity": 20, "platform_booked": 14,
            "arcana_spots_offered": 20, "arcana_spots_available": 6,
            "template": {"id": 311, "name": "RUN x LIFT", "modality": "bootcamp",
                         "hero_image_url": "", "spot_selection_mode": "none"},
            "instructors": [], "location": {"id": 41, "name": "Barry's Chelsea",
              "timezone": "America/New_York",
              "studio": {"id": 3, "slug": "barrys", "name": "Barry's", "logo_url": "", "primary_color": "#000000"}}
          }
        """.trimIndent()
        val session = json.decodeFromString(ScheduleSessionDto.serializer(), raw)
        val template = session.template
        assertTrue(template.spotPreferenceOptions.isEmpty())
        assertNull(template.spotPreferenceLabel)
    }
}
