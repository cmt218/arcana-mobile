package org.arcana.mobile.analytics

import kotlin.test.Test
import kotlin.test.assertEquals

class AppEnvironmentTest {
    @Test fun `prod hostname is prod`() {
        assertEquals("prod", classifyEnvironment("https://api.arcana.fit"))
        assertEquals("prod", classifyEnvironment("https://api.arcana.fit/api/v1/"))
    }

    @Test fun `dev loopbacks are local`() {
        assertEquals("local", classifyEnvironment("http://localhost:8000"))
        assertEquals("local", classifyEnvironment("http://127.0.0.1:8000"))
        assertEquals("local", classifyEnvironment("http://10.0.2.2:8000"))
    }

    @Test fun `cloudflare quick tunnel is tunnel`() {
        assertEquals("tunnel", classifyEnvironment("https://random-words-here.trycloudflare.com"))
    }

    @Test
    fun `staging host classifies as staging`() {
        assertEquals("staging", classifyEnvironment("https://api.staging.arcana.fit"))
    }

    @Test fun `unknown host is other`() {
        assertEquals("other", classifyEnvironment("https://staging.example.com"))
    }

    @Test fun `unparseable url is other`() {
        assertEquals("other", classifyEnvironment("not a url"))
    }
}
