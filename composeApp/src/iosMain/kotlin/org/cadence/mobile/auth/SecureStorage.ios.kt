package org.cadence.mobile.auth

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

    private val service = "org.cadence.mobile"

    actual fun save(key: String, value: String) {
        delete(key)
        val bytes = value.encodeToByteArray()
        bytes.usePinned { pinned ->
            val cfData = CFDataCreate(null, pinned.addressOf(0).reinterpret(), bytes.size.toLong())!!
            val query = buildQuery(key, capacity = 5)
            CFDictionaryAddValue(query, kSecValueData, cfData)
            CFDictionaryAddValue(query, kSecAttrAccessible, kSecAttrAccessibleWhenUnlockedThisDeviceOnly)
            SecItemAdd(query, null)
            CFRelease(cfData)
            CFRelease(query)
        }
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
            if (status != errSecSuccess) return@memScoped null
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
        SecItemDelete(query)
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
