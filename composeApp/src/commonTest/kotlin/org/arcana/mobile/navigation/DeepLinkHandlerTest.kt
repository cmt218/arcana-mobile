package org.arcana.mobile.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinkHandlerTest {
    // --- https Universal Link shape ---
    @Test fun `parses welcome token from valid https url`() {
        assertEquals("abc123", DeepLinkHandler.extractWelcomeToken("https://arcana.fit/welcome?token=abc123"))
    }
    @Test fun `parses welcome token with url-encoded value`() {
        assertEquals("abc/123", DeepLinkHandler.extractWelcomeToken("https://arcana.fit/welcome?token=abc%2F123"))
    }
    @Test fun `tolerates trailing slash and fragment`() {
        assertEquals("xy", DeepLinkHandler.extractWelcomeToken("https://arcana.fit/welcome/?token=xy#frag"))
    }
    @Test fun `returns null for unrelated https url`() {
        assertNull(DeepLinkHandler.extractWelcomeToken("https://arcana.fit/about"))
        assertNull(DeepLinkHandler.extractWelcomeToken("https://example.com/welcome?token=x"))
    }
    @Test fun `returns null when token query param missing`() {
        assertNull(DeepLinkHandler.extractWelcomeToken("https://arcana.fit/welcome"))
    }
    @Test fun `returns null for empty token value on https`() {
        assertNull(DeepLinkHandler.extractWelcomeToken("https://arcana.fit/welcome?token="))
    }
    @Test fun `host match is case-insensitive`() {
        assertEquals("x", DeepLinkHandler.extractWelcomeToken("https://ARCANA.FIT/welcome?token=x"))
    }

    // --- arcana:// custom scheme shape (local-dev) ---
    @Test fun `parses welcome token from custom scheme url`() {
        assertEquals("tok-9", DeepLinkHandler.extractWelcomeToken("arcana://welcome?token=tok-9"))
    }
    @Test fun `custom scheme with url-encoded value`() {
        assertEquals("a/b", DeepLinkHandler.extractWelcomeToken("arcana://welcome?token=a%2Fb"))
    }
    @Test fun `custom scheme returns null for wrong authority`() {
        assertNull(DeepLinkHandler.extractWelcomeToken("arcana://other?token=x"))
    }
    @Test fun `custom scheme returns null when token missing`() {
        assertNull(DeepLinkHandler.extractWelcomeToken("arcana://welcome"))
    }
    @Test fun `returns null for empty token value on custom scheme`() {
        assertNull(DeepLinkHandler.extractWelcomeToken("arcana://welcome?token="))
    }

    // --- extra edge cases ---
    @Test fun `tolerates http scheme`() {
        assertEquals("abc", DeepLinkHandler.extractWelcomeToken("http://arcana.fit/welcome?token=abc"))
    }
    @Test fun `decodes plus as space`() {
        assertEquals("a b", DeepLinkHandler.extractWelcomeToken("https://arcana.fit/welcome?token=a+b"))
    }
    @Test fun `custom scheme tolerates fragment`() {
        assertEquals("z", DeepLinkHandler.extractWelcomeToken("arcana://welcome?token=z#frag"))
    }
    @Test fun `picks token among multiple params`() {
        assertEquals("t", DeepLinkHandler.extractWelcomeToken("https://arcana.fit/welcome?ref=x&token=t&utm=y"))
    }
    @Test fun `returns null for non-url garbage`() {
        assertNull(DeepLinkHandler.extractWelcomeToken("not a url"))
    }
    @Test fun `https rejects welcome as wrong path with extra segment`() {
        assertNull(DeepLinkHandler.extractWelcomeToken("https://arcana.fit/welcome/extra?token=x"))
    }
}
