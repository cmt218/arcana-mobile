package org.arcana.mobile.auth

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Foundation.NSData
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual class SecureStorage actual constructor() {

    private val service = "org.arcana.mobile"

    /**
     * Update in place, adding only when the item does not exist yet.
     *
     * This used to delete then add, which is not atomic: a failed add left the
     * slot permanently empty because the old value was already gone. Items are
     * [kSecAttrAccessibleWhenUnlockedThisDeviceOnly], so a write from a locked
     * device is rejected outright, and rotating refresh tokens rewrites this
     * every few minutes including from the background. Losing the token that
     * way signed members out for good. A failed update now leaves the previous
     * value intact, so the worst case is a stale token the server will refuse,
     * which is recoverable.
     */
    actual fun save(key: String, value: String) {
        val bytes = value.encodeToByteArray()
        // usePinned cannot address into a zero-length array (addressOf(0)
        // is out of bounds) — an empty value gets an empty CFData directly.
        if (bytes.isEmpty()) {
            saveCfData(key, CFDataCreate(null, null, 0)!!)
            return
        }
        bytes.usePinned { pinned ->
            saveCfData(key, CFDataCreate(null, pinned.addressOf(0).reinterpret(), bytes.size.toLong())!!)
        }
    }

    private fun saveCfData(key: String, cfData: CFDataRef) {
        val query = buildQuery(key, capacity = 3)
        val attributes = CFDictionaryCreateMutable(null, 1, null, null)!!
        CFDictionaryAddValue(attributes, kSecValueData, cfData)
        var status = SecItemUpdate(query, attributes)
        if (status == errSecItemNotFound) {
            // First write for this key. Accessibility is fixed at add time,
            // which is why it is set here and not on the update above.
            val addQuery = buildQuery(key, capacity = 5)
            CFDictionaryAddValue(addQuery, kSecValueData, cfData)
            CFDictionaryAddValue(addQuery, kSecAttrAccessible, kSecAttrAccessibleWhenUnlockedThisDeviceOnly)
            status = SecItemAdd(addQuery, null)
            CFRelease(addQuery)
        }
        if (status != errSecSuccess) {
            SecureStorageDiagnostics.report(SecureStorageDiagnostics.Op.SAVE, key, status)
        }
        CFRelease(attributes)
        CFRelease(query)
        CFRelease(cfData)
    }

    @OptIn(BetaInteropApi::class)
    actual fun load(key: String): String? {
        val query = buildQuery(key, capacity = 5)
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
        return memScoped {
            // Allocate an ObjC-typed slot: CFDataRef is toll-free bridged to NSData,
            // so the Keychain will write an NSData into this slot.
            val resultRef = alloc<ObjCObjectVar<NSData?>>()
            @Suppress("UNCHECKED_CAST")
            val status = SecItemCopyMatching(query, resultRef.ptr as kotlinx.cinterop.CValuesRef<CFTypeRefVar>)
            CFRelease(query)
            if (status != errSecSuccess) {
                // Every failure still collapses to null for the caller (behavior
                // unchanged), but record WHICH failure it was. This is the field
                // that separates "device locked, token is fine"
                // (errSecInteractionNotAllowed) from "genuinely signed out"
                // (errSecItemNotFound) — indistinguishable until now.
                SecureStorageDiagnostics.report(
                    op = SecureStorageDiagnostics.Op.LOAD,
                    key = key,
                    status = status,
                    // errSecItemNotFound just means nothing was ever written here
                    // (signed-out member, or no dev base-URL override) — normal, so
                    // not its own event. Still recorded, so a forced logout can say
                    // "genuinely absent" rather than "unreadable".
                    notable = status != errSecItemNotFound,
                )
                return@memScoped null
            }
            val nsData = resultRef.value ?: return@memScoped null
            val length = nsData.length.toInt()
            if (length == 0) return@memScoped null
            val result = ByteArray(length)
            result.usePinned { pinned ->
                memcpy(pinned.addressOf(0), nsData.bytes, length.toULong())
            }
            result.decodeToString()
        }
    }

    actual fun delete(key: String) {
        val query = buildQuery(key, capacity = 3)
        val status = SecItemDelete(query)
        // errSecItemNotFound is the normal "nothing to remove" case (clearing a
        // session that was never written), so reporting it would be noise.
        if (status != errSecSuccess && status != errSecItemNotFound) {
            SecureStorageDiagnostics.report(SecureStorageDiagnostics.Op.DELETE, key, status)
        }
        CFRelease(query)
    }

    private fun buildQuery(key: String, capacity: Int): CFMutableDictionaryRef {
        return CFDictionaryCreateMutable(null, capacity.toLong(), null, null)!!.also { query ->
            CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(query, kSecAttrService, CFStringCreateWithCString(null, service, kCFStringEncodingUTF8))
            CFDictionaryAddValue(query, kSecAttrAccount, CFStringCreateWithCString(null, key, kCFStringEncodingUTF8))
        }
    }
}
