package org.cadence.mobile.data

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
)

@Serializable
data class TokenResponse(
    val access: String,
    val refresh: String,
)

@Serializable
data class RefreshTokenResponse(
    val access: String,
    val refresh: String? = null,
)

@Serializable
data class RefreshRequest(
    val refresh: String,
)
