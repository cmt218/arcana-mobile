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
