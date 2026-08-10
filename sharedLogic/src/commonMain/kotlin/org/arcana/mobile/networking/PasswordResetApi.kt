package org.arcana.mobile.networking

interface PasswordResetApi {
    suspend fun requestPasswordReset(email: String)
}

class PasswordResetRequestError(val statusCode: Int) : Exception("password_reset_request_$statusCode")
