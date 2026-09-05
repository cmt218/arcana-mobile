package org.arcana.mobile.auth

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The per-key record is what attaches "was it locked, or genuinely gone?" to a
 *  later forced logout, so it has to survive being rewritten and stay separate
 *  per key. */
class SecureStorageDiagnosticsTest {

    @AfterTest fun teardown() = SecureStorageDiagnostics.resetForTest()

    @Test fun `keeps the latest failure per key`() {
        SecureStorageDiagnostics.report(SecureStorageDiagnostics.Op.LOAD, "access", -25308)
        SecureStorageDiagnostics.report(SecureStorageDiagnostics.Op.LOAD, "access", -25300)

        assertEquals(-25300, SecureStorageDiagnostics.lastFailureFor("access")?.status)
    }

    @Test fun `records keys independently`() {
        SecureStorageDiagnostics.report(SecureStorageDiagnostics.Op.LOAD, "access", -25308)
        SecureStorageDiagnostics.report(SecureStorageDiagnostics.Op.SAVE, "refresh", -25299)

        assertEquals(-25308, SecureStorageDiagnostics.lastFailureFor("access")?.status)
        assertEquals(SecureStorageDiagnostics.Op.SAVE, SecureStorageDiagnostics.lastFailureFor("refresh")?.op)
        assertNull(SecureStorageDiagnostics.lastFailureFor("never_written"))
    }

    @Test fun `only notable failures reach the listener`() {
        val seen = mutableListOf<SecureStorageDiagnostics.Failure>()
        SecureStorageDiagnostics.listener = { seen += it }

        SecureStorageDiagnostics.report(SecureStorageDiagnostics.Op.LOAD, "base_url", -25300, notable = false)
        SecureStorageDiagnostics.report(SecureStorageDiagnostics.Op.LOAD, "access", -25308)

        assertEquals(listOf("access"), seen.map { it.key })
        assertEquals(-25300, SecureStorageDiagnostics.lastFailureFor("base_url")?.status)
    }
}
