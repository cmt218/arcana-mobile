package org.arcana.mobile.auth

class TokenStorage(private val storage: SecureStorage = SecureStorage()) {

    var accessToken: String?
        get() = storage.load("access_token")
        set(value) {
            if (value != null) storage.save("access_token", value)
            else storage.delete("access_token")
        }

    var refreshToken: String?
        get() = storage.load("refresh_token")
        set(value) {
            if (value != null) storage.save("refresh_token", value)
            else storage.delete("refresh_token")
        }

    val isLoggedIn: Boolean
        get() = accessToken != null && refreshToken != null

    fun clear() {
        storage.delete("access_token")
        storage.delete("refresh_token")
    }
}
