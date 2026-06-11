package org.arcana.mobile.data

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private val json = Json { ignoreUnknownKeys = true }

class FavoritesDtoTest {
    @Test
    fun `parses the GET favorites shape with both grains`() {
        val raw = """
          {"studios":[
             {"id":3,"slug":"solidcore","name":"SolidCore","logo_url":"https://cdn/sc.png",
              "primary_color":"#1A1A1A","location_ids":[41,42,55]}],
           "locations":[
             {"id":7,"name":"YO BK Williamsburg","studio_slug":"yo-bk","studio_name":"YO BK"}]}
        """.trimIndent()
        val f = json.decodeFromString(FavoritesDto.serializer(), raw)
        val studio = f.studios.single()
        assertEquals(3, studio.id)
        assertEquals("solidcore", studio.slug)
        assertEquals("SolidCore", studio.name)
        assertEquals("https://cdn/sc.png", studio.logoUrl)
        assertEquals("#1A1A1A", studio.primaryColor)
        assertEquals(listOf(41, 42, 55), studio.locationIds)
        val location = f.locations.single()
        assertEquals(7, location.id)
        assertEquals("YO BK Williamsburg", location.name)
        assertEquals("yo-bk", location.studioSlug)
        assertEquals("YO BK", location.studioName)
    }

    @Test
    fun `studio favorite missing location_ids fails decoding`() {
        // Guard: a server-side rename of location_ids must NOT silently
        // deserialize into "this studio favorite filters nothing".
        val raw = """{"studios":[{"id":3,"slug":"solidcore","name":"SolidCore"}],"locations":[]}"""
        assertFailsWith<SerializationException> {
            json.decodeFromString(FavoritesDto.serializer(), raw)
        }
    }

    @Test
    fun `parses a studios row ignoring unknown keys`() {
        val raw = """
          {"id":3,"slug":"solidcore","name":"SolidCore","logo_url":"","primary_color":"#1A1A1A",
           "tier":"premium",
           "locations":[{"id":41,"name":"SolidCore Williamsburg","timezone":"America/New_York"}]}
        """.trimIndent()
        val s = json.decodeFromString(StudioDto.serializer(), raw)
        assertEquals(3, s.id)
        assertEquals("solidcore", s.slug)
        assertEquals("SolidCore", s.name)
        val loc = s.locations.single()
        assertEquals(41, loc.id)
        assertEquals("SolidCore Williamsburg", loc.name)
        assertEquals("America/New_York", loc.timezone)
    }

    @Test
    fun `encodes update request with snake_case keys`() {
        val encoded = json.encodeToString(
            UpdateFavoritesRequest.serializer(),
            UpdateFavoritesRequest(studioSlugs = listOf("solidcore"), locationIds = listOf(7)),
        )
        assertEquals("""{"studio_slugs":["solidcore"],"location_ids":[7]}""", encoded)
    }
}
