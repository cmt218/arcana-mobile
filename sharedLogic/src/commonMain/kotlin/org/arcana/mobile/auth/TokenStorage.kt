package org.arcana.mobile.auth

class TokenStorage(private val storage: SecureStorage = SecureStorage()) {

    companion object {
        /** Store keys. Named so diagnostics can attribute a failure to the token
         *  it belongs to rather than to unrelated keys in the same store. */
        const val ACCESS_TOKEN_KEY = "access_token"
        const val REFRESH_TOKEN_KEY = "refresh_token"
    }

    var accessToken: String?
        get() = storage.load(ACCESS_TOKEN_KEY)
        set(value) {
            if (value != null) storage.save(ACCESS_TOKEN_KEY, value)
            else storage.delete(ACCESS_TOKEN_KEY)
        }

    var refreshToken: String?
        get() = storage.load(REFRESH_TOKEN_KEY)
        set(value) {
            if (value != null) storage.save(REFRESH_TOKEN_KEY, value)
            else storage.delete(REFRESH_TOKEN_KEY)
        }

    val isLoggedIn: Boolean
        get() = accessToken != null && refreshToken != null

    fun clear() {
        storage.delete(ACCESS_TOKEN_KEY)
        storage.delete(REFRESH_TOKEN_KEY)
    }
}
