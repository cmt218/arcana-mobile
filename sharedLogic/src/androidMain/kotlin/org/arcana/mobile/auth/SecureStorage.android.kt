package org.arcana.mobile.auth

import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.arcana.mobile.SharedAndroidContext
import java.security.KeyStore

actual class SecureStorage actual constructor() {

    private val prefs by lazy { open() }

    /**
     * Opening the store can fail in a way the member cannot escape: an Android
     * backup or device transfer restores the prefs file to a new phone, but the
     * hardware master key encrypting it never leaves the old one, so Tink's
     * keyset fails to authenticate and every read throws. This ran during Koin
     * DI before any UI existed, so the app died on launch, every launch, with no
     * way out but clearing app data (ARCANA-ANDROID-9).
     *
     * Rebuilding empty signs the member out — the stored tokens are genuinely
     * unrecoverable once the key is gone — but that beats an app that cannot
     * start. A second failure is left to throw: [discard] puts the store back to
     * its fresh-install state, so a retry that still fails means the device's
     * keystore is broken and we want to hear about it rather than mask it.
     */
    private fun open(): SharedPreferences = try {
        create()
    } catch (e: Exception) {
        SecureStorageDiagnostics.report(
            op = SecureStorageDiagnostics.Op.DISCARD,
            key = SecureStorageDiagnostics.KEY_WHOLE_STORE,
            status = SecureStorageDiagnostics.STATUS_UNREADABLE,
        )
        discard()
        create()
    }

    private fun create(): SharedPreferences {
        val context = SharedAndroidContext.require()
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** Both halves must go. The Tink keysets live inside the prefs file, and a
     *  master key that outlived them would fail the retry exactly as it failed
     *  the first attempt. Deleting the alias is best-effort: regenerating the
     *  keyset against a fresh key is what actually unblocks startup. */
    private fun discard() {
        SharedAndroidContext.require().deleteSharedPreferences(PREFS_FILE)
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE)
                .apply { load(null) }
                .deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        }
    }

    actual fun save(key: String, value: String) {
        report(SecureStorageDiagnostics.Op.SAVE, key) { it.edit().putString(key, value).apply() }
    }

    actual fun load(key: String): String? =
        report(SecureStorageDiagnostics.Op.LOAD, key) { it.getString(key, null) }

    actual fun delete(key: String) {
        report(SecureStorageDiagnostics.Op.DELETE, key) { it.edit().remove(key).apply() }
    }

    /**
     * A single unreadable value must not take the app down: `load` runs in Koin
     * field initializers during startup, so a throw here is an unrecoverable
     * launch crash. Every failure collapses to null and is reported, matching
     * iOS. Resolving [prefs] outside the catch keeps [open]'s rule that a store
     * which cannot be rebuilt still throws.
     */
    private inline fun <T> report(
        op: String,
        key: String,
        block: (SharedPreferences) -> T,
    ): T? {
        val prefs = prefs
        return try {
            block(prefs)
        } catch (e: Throwable) {
            SecureStorageDiagnostics.report(op, key, SecureStorageDiagnostics.STATUS_EXCEPTION)
            null
        }
    }

    private companion object {
        const val PREFS_FILE = "arcana_secure_prefs"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}
