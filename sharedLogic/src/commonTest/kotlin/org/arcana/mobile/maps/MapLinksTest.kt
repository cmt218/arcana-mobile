package org.arcana.mobile.maps

import kotlin.test.Test
import kotlin.test.assertEquals

class MapLinksTest {
    private val pinned = MapTarget("Chelsea", "135 W 20th St, New York, NY 10011", 40.7411, -73.9964, business = "Barry's Chelsea")
    private val addressOnly = MapTarget("SLT Flatiron", "10 W 19th St, New York, NY 10011")
    private val q = "Barry%27s%20Chelsea%2C%20135%20W%2020th%20St%2C%20New%20York%2C%20NY%2010011"

    @Test fun `percent encoding keeps unreserved characters and encodes the rest`() {
        assertEquals("Barry%27s%20Chelsea%20%26%20Co.", percentEncode("Barry's Chelsea & Co."))
        assertEquals("caf%C3%A9", percentEncode("café"))
        assertEquals("a-b_c.d~e", percentEncode("a-b_c.d~e"))
    }

    @Test fun `the search query is the business plus the address`() {
        assertEquals("Barry's Chelsea, 135 W 20th St, New York, NY 10011", pinned.searchQuery)
        assertEquals("SLT Flatiron, 10 W 19th St, New York, NY 10011", addressOnly.searchQuery)
        assertEquals("SLT Flatiron", addressOnly.copy(address = "").searchQuery)
    }

    @Test fun `apple maps searches the business and biases to the coordinates`() {
        assertEquals("maps://?q=$q&sll=40.7411,-73.9964", appleMapsUrl(pinned))
        assertEquals("maps://?q=SLT%20Flatiron%2C%2010%20W%2019th%20St%2C%20New%20York%2C%20NY%2010011", appleMapsUrl(addressOnly))
    }

    @Test fun `google maps searches the business and centres on the coordinates`() {
        assertEquals("comgooglemaps://?q=$q&center=40.7411,-73.9964&zoom=16", googleMapsUrl(pinned))
        assertEquals("comgooglemaps://?q=SLT%20Flatiron%2C%2010%20W%2019th%20St%2C%20New%20York%2C%20NY%2010011", googleMapsUrl(addressOnly))
    }

    @Test fun `geo uri searches the business near the coordinates`() {
        assertEquals("geo:40.7411,-73.9964?q=$q", geoUri(pinned))
        assertEquals("geo:0,0?q=SLT%20Flatiron%2C%2010%20W%2019th%20St%2C%20New%20York%2C%20NY%2010011", geoUri(addressOnly))
    }

    @Test fun `a single missing coordinate counts as no coordinates`() {
        val half = pinned.copy(longitude = null)
        assertEquals(false, half.hasCoordinates)
        assertEquals("maps://?q=$q", appleMapsUrl(half))
    }
}
