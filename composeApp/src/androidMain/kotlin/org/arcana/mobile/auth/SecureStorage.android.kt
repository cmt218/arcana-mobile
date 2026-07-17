package org.arcana.mobile.auth

import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.arcana.mobile.ArcanaApplication

actual class SecureStorage actual constructor() {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(ArcanaApplication.instance)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            ArcanaApplication.instance,
            "arcana_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // EncryptedSharedPreferences signals failure by throwing (keystore/decryption
    // problems) rather than by a status code. Each op reports the throw and then
    // rethrows, so behavior is unchanged — this is reporting only, and keeps
    // Android at parity with iOS in `token_storage_failure`.

    actual fun save(key: String, value: String) = report(SecureStorageDiagnostics.Op.SAVE, key) {
        prefs.edit().putString(key, value).apply()
    }

    actual fun load(key: String): String? = report(SecureStorageDiagnostics.Op.LOAD, key) {
        prefs.getString(key, null)
    }

    actual fun delete(key: String) = report(SecureStorageDiagnostics.Op.DELETE, key) {
        prefs.edit().remove(key).apply()
    }

    private inline fun <T> report(op: String, key: String, block: () -> T): T = try {
        block()
    } catch (e: Throwable) {
        SecureStorageDiagnostics.report(op, key, SecureStorageDiagnostics.STATUS_EXCEPTION)
        throw e
    }
}
