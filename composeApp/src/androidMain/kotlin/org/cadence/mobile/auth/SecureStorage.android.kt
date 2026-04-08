package org.cadence.mobile.auth

import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.cadence.mobile.CadenceApplication

actual class SecureStorage actual constructor() {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(CadenceApplication.instance)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            CadenceApplication.instance,
            "cadence_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    actual fun save(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    actual fun load(key: String): String? {
        return prefs.getString(key, null)
    }

    actual fun delete(key: String) {
        prefs.edit().remove(key).apply()
    }
}
