package org.arcana.mobile.home

import kotlin.test.Test
import kotlin.test.assertEquals

class FirstNameTest {
    @Test fun `full name takes first token`() = assertEquals("Cole", firstName("Cole Tomlinson"))
    @Test fun `single name unchanged`() = assertEquals("Felicia", firstName("Felicia"))
    @Test fun `trims surrounding whitespace`() = assertEquals("Cole", firstName("  Cole Tomlinson  "))
    @Test fun `blank stays blank`() = assertEquals("", firstName("   "))
}
