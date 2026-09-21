package org.arcana.mobile.discover

import org.arcana.mobile.data.DiscoverCategoryDto
import org.arcana.mobile.data.DiscoverStudioDto
import org.arcana.mobile.data.StudioPageLocationDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiscoverMapTest {
    private val reformer = DiscoverCategoryDto("reformer", "Reformer")
    private val nofar = DiscoverStudioDto(
        slug = "nofar-method", name = "Nofar Method", primaryColor = "#283b15", categories = listOf(reformer),
        neighborhoods = listOf("Flatiron / Chelsea", "SoHo / Tribeca"), locationCount = 3,
        locations = listOf(
            StudioPageLocationDto(1, "Flatiron", "Flatiron / Chelsea", "12 W 21st St", 40.7410, -73.9920),
            StudioPageLocationDto(2, "Tribeca", "SoHo / Tribeca", "1 White St", 40.7190, -74.0060),
            // The server withholds coordinates it does not trust: no pin, never a misplaced one.
            StudioPageLocationDto(3, "Chelsea", "Flatiron / Chelsea", "9 W 19th St", null, null),
        ),
    )

    @Test fun `every located studio location becomes a pin carrying what its card says`() {
        val pins = discoverPins(listOf(nofar), neighborhoods = emptySet())
        assertEquals(listOf(1, 2), pins.map { it.locationId })
        val first = pins.first()
        assertEquals("nofar-method", first.brandSlug)
        assertEquals("NM", first.monogram)
        assertEquals(listOf(reformer), first.categories)
        assertEquals("12 W 21st St", first.address)
    }

    @Test fun `a neighborhood filter keeps only that neighborhood's pins`() {
        // The server keeps the whole brand when any location matches; the map must not.
        val pins = discoverPins(listOf(nofar), neighborhoods = setOf("SoHo / Tribeca"))
        assertEquals(listOf(2), pins.map { it.locationId })
    }

    @Test fun `monograms take two initials and skip punctuation`() {
        assertEquals("S", monogramFor("[solidcore]"))
        assertEquals("3F", monogramFor("305 Fitness"))
        assertEquals("IH", monogramFor("ID Hot Yoga"))
        assertEquals("?", monogramFor("   "))
    }

    @Test fun `the place line says a name once`() {
        val pins = discoverPins(listOf(nofar), emptySet())
        assertEquals("Flatiron · Flatiron / Chelsea", pinPlaceLine(pins[0]))
        assertEquals("Tribeca", pinPlaceLine(pins[1].copy(neighborhood = "Tribeca")))
        assertEquals("Tribeca", pinPlaceLine(pins[1].copy(neighborhood = "")))
    }

    @Test fun `a frame takes in every pin with room to spare and never zooms past a neighborhood`() {
        assertEquals(null, framePins(emptyList()))
        val pins = discoverPins(listOf(nofar), emptySet())
        val frame = framePins(pins)!!
        pins.forEach { pin ->
            assertTrue(pin.latitude > frame.south && pin.latitude < frame.north)
            assertTrue(pin.longitude > frame.west && pin.longitude < frame.east)
        }
        // One pin alone: the frame is a couple of kilometres, not one building.
        val lone = framePins(pins.take(1))!!
        assertEquals(pins[0].latitude, lone.latitude)
        assertEquals(0.018, lone.latitudeDelta)
        assertEquals(0.018, lone.longitudeDelta)
    }

    // Clustering is what the Android map draws (MapKit clusters natively on iOS).

    private fun pin(id: Int, latitude: Double, longitude: Double) = DiscoverPin(
        locationId = id, brandSlug = "b", brandName = "B", primaryColor = "", locationName = "L$id", neighborhood = "",
        address = "", latitude = latitude, longitude = longitude, monogram = "B", categories = emptyList(),
    )

    @Test fun `neighbours merge when zoomed out and separate when zoomed in`() {
        // Two studios a block apart in Flatiron, one in Williamsburg.
        val pins = listOf(pin(1, 40.7410, -73.9920), pin(2, 40.7414, -73.9915), pin(3, 40.7140, -73.9610))
        val far = clusterPins(pins, zoom = 11.0)
        assertTrue(far.any { it.pinIds.toSet() == setOf(1, 2) }, "a block apart is one mark from across the city")
        val near = clusterPins(pins, zoom = 18.0)
        assertEquals(3, near.size)
        assertTrue(near.all { it.single })
    }

    @Test fun `every pin lands in exactly one cluster and a cluster sits among its members`() {
        val pins = (0 until 40).map { pin(it, 40.70 + it * 0.002, -74.01 + it * 0.0015) }
        for (zoom in listOf(9.0, 12.0, 14.5, 17.0)) {
            val clusters = clusterPins(pins, zoom)
            assertEquals(pins.map { it.locationId }.sorted(), clusters.flatMap { it.pinIds }.sorted(), "zoom $zoom")
            clusters.forEach { cluster ->
                val members = pins.filter { it.locationId in cluster.pinIds }
                assertTrue(cluster.latitude in members.minOf { it.latitude }..members.maxOf { it.latitude })
                assertTrue(cluster.longitude in members.minOf { it.longitude }..members.maxOf { it.longitude })
            }
        }
    }

    @Test fun `pins at one address never separate`() {
        val stacked = listOf(pin(1, 40.7410, -73.9920), pin(2, 40.7410, -73.9920))
        assertEquals(listOf(listOf(1, 2)), clusterPins(stacked, zoom = 20.0).map { it.pinIds })
    }

    @Test fun `the selected pin stands alone even at an address it shares`() {
        // Reached from a class page, the place must be visible, not a count.
        val stacked = listOf(pin(1, 40.7410, -73.9920), pin(2, 40.7410, -73.9920), pin(3, 40.7411, -73.9921))
        val clusters = clusterPins(stacked, zoom = 11.0, alone = 2)
        assertEquals(listOf(listOf(2), listOf(1, 3)), clusters.map { it.pinIds })
        assertEquals(40.7410, clusters.first().latitude)
    }

    @Test fun `clustering is stable for the same input`() {
        val pins = (0 until 25).map { pin(it, 40.72 + it * 0.001, -74.0 + it * 0.001) }
        assertEquals(clusterPins(pins, 13.0), clusterPins(pins, 13.0))
    }
}
