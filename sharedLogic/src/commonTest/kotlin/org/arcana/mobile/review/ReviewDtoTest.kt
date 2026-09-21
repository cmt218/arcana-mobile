package org.arcana.mobile.review

import kotlinx.serialization.json.Json
import org.arcana.mobile.data.BookingDto
import org.arcana.mobile.data.DiscoverDirectoryDto
import org.arcana.mobile.data.FeedbackFeedDto
import org.arcana.mobile.data.MyUpcomingDto
import org.arcana.mobile.data.ScheduleSessionDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

/** Every new field defaults, so the shipped payloads still parse without them. */
class ReviewDtoTest {
    private val bookingJson = """
      {"id": 5, "status": "completed", "session": {"id": 9, "start_at": "2026-09-14T18:00:00Z",
       "end_at": "2026-09-14T18:50:00Z", "name": "Sculpt 50", "studio": "Soto Method"},
       "cancel_policy": {"will_forfeit_credit": false}%s}
    """.trimIndent()

    @Test fun `booking review_state defaults to ineligible and parses when present`() {
        val without = json.decodeFromString(BookingDto.serializer(), bookingJson.replace("%s", ""))
        assertEquals("ineligible", without.reviewState)
        assertFalse(without.canReview)
        val none = json.decodeFromString(BookingDto.serializer(), bookingJson.replace("%s", ", \"review_state\": \"none\""))
        assertTrue(none.canReview && !none.reviewed)
        val elsewhere = json.decodeFromString(BookingDto.serializer(), bookingJson.replace("%s", ", \"review_state\": \"reviewed_elsewhere\""))
        assertTrue(elsewhere.reviewed && !elsewhere.canReview)
    }

    @Test fun `scoped upcoming carries the prompt or null`() {
        val raw = """
          {"upcoming": [], "review_prompt": {"booking_id": 5, "class_session_id": 9,
            "start_at": "2026-09-14T18:00:00Z", "end_at": "2026-09-14T18:50:00Z",
            "brand": {"slug": "soto-method", "name": "Soto Method"}, "location": {"id": 4, "name": "Flatiron"},
            "class_type": {"key": "sculpt-50", "label": "Sculpt 50"}, "instructor": null}}
        """.trimIndent()
        val dto = json.decodeFromString(MyUpcomingDto.serializer(), raw)
        assertEquals(5, dto.reviewPrompt?.bookingId)
        assertNull(dto.reviewPrompt?.instructor)
        assertEquals("Sculpt 50", dto.reviewPrompt?.classType?.label)
        assertNull(json.decodeFromString(MyUpcomingDto.serializer(), """{"upcoming": [], "review_prompt": null}""").reviewPrompt)
        assertNull(json.decodeFromString(MyUpcomingDto.serializer(), """{"upcoming": []}""").reviewPrompt)
    }

    @Test fun `class detail review fields default and parse`() {
        val base = """
          {"id": 482, "start_at": "2026-07-07T10:00:00Z", "end_at": "2026-07-07T10:50:00Z",
           "duration_minutes": 50, "status": "scheduled", "platform_capacity": 20, "platform_booked": 14,
           "arcana_spots_offered": 20, "arcana_spots_available": 6,
           "template": {"id": 311, "name": "Sculpt 50", "modality": "sculpt", "hero_image_url": "",
                        "spot_selection_mode": "none"%s},
           "instructors": [{"id": 1, "name": "Dana Levy", "photo_url": ""%s}],
           "location": {"id": 41, "name": "Flatiron", "timezone": "America/New_York",
             "studio": {"id": 3, "slug": "soto", "name": "Soto Method", "logo_url": "", "primary_color": "#000000"}%s}%s}
        """.trimIndent()
        val old = json.decodeFromString(ScheduleSessionDto.serializer(), base.replace("%s", ""))
        assertEquals(0, old.classTypeReviewCount)
        assertEquals(0 to 0, old.locationReviewCount to old.brandReviewCount)
        assertNull(old.myReview)
        assertFalse(old.reviewPromptEligible)
        assertNull(old.reviewBookingId)
        assertNull(old.instructors[0].profileId)
        assertNull(old.template.classTypeKey)
        assertNull(old.location.brand)

        val filled = base
            .replaceFirst("%s", ", \"class_type_key\": \"sculpt-50\"")
            .replaceFirst("%s", ", \"profile_id\": 7, \"bio\": \"Lineage.\", \"review_count\": 12")
            .replaceFirst("%s", ", \"brand\": {\"slug\": \"soto-method\", \"name\": \"Soto Method\"}")
            .replaceFirst("%s", """, "class_type_review_count": 4, "location_review_count": 2, "brand_review_count": 9,
               "review_prompt_eligible": true, "review_booking_id": 55,
               "my_review": {"id": 9, "booking_id": 55, "again": "yes", "intensity": 3, "comment": ""}""")
        val new = json.decodeFromString(ScheduleSessionDto.serializer(), filled)
        assertEquals(4, new.classTypeReviewCount)
        assertEquals(2 to 9, new.locationReviewCount to new.brandReviewCount)
        assertEquals(55, new.reviewBookingId)
        assertTrue(new.reviewPromptEligible)
        assertEquals(3, new.myReview?.intensity)
        assertEquals(7, new.instructors[0].profileId)
        assertEquals(12, new.instructors[0].reviewCount)
        assertEquals("sculpt-50", new.template.classTypeKey)
        assertEquals("soto-method", new.location.brand?.slug)
    }

    @Test fun `feed page parses stats items and cursor`() {
        val raw = """
          {"scope": {"type": "instructor", "value": "7", "label": "Dana Levy", "count": 2,
                     "again": {"yes": 2, "maybe": 0, "no": 0}, "intensity_avg": 3.0, "instructor_avg": 4.5,
                     "class_avg": null, "studio_avg": null},
           "items": [{"id": 12, "created_at": "2026-09-12T14:00:00Z", "brand": {"slug": "soto-method", "name": "Soto Method"},
                      "location": {"id": 4, "name": "Flatiron"}, "class_type": {"key": "sculpt-50", "label": "Sculpt 50"},
                      "instructor": {"profile_id": 7, "name": "Dana Levy"}, "again": "yes", "intensity": 3,
                      "instructor_score": 5, "class_score": null, "studio_score": null, "comment": "Precise."}],
           "next_cursor": "abc"}
        """.trimIndent()
        val dto = json.decodeFromString(FeedbackFeedDto.serializer(), raw)
        assertEquals(2, dto.scope.count)
        assertEquals(4.5, dto.scope.instructorAvg)
        assertNull(dto.scope.classAvg)
        assertEquals("Precise.", dto.items.single().comment)
        assertNull(dto.items.single().bookingId)
        assertEquals("abc", dto.nextCursor)
        // Only a location feed carries the brand it can widen to.
        assertNull(dto.scope.brand)
        val location = json.decodeFromString(
            FeedbackFeedDto.serializer(),
            """{"scope": {"type": "location", "value": "4", "label": "Soto Method · Flatiron", "count": 1,
                "brand": {"slug": "soto-method", "name": "Soto Method", "count": 6}}, "items": []}""",
        )
        assertEquals("soto-method", location.scope.brand?.slug)
        assertEquals(6, location.scope.brand?.count)
    }

    @Test fun `an older server's directory feedback block is ignored`() {
        // Servers still send it for the 1.3.0 app; this build has no row to feed.
        val directory = json.decodeFromString(
            DiscoverDirectoryDto.serializer(),
            """{"studios": [], "feedback": {"review_count": 3, "latest": null}}""",
        )
        assertEquals(emptyList(), directory.studios)
    }
}
