package org.arcana.mobile.data

import kotlinx.serialization.Serializable

@Serializable
data class CompleteSignupRequest(
    val token: String,
    val password: String,
    val display_name: String,
    val phone_number: String,
)

@Serializable
data class CompleteSignupResponse(
    val access: String,
    val refresh: String,
    val user: SignupUser,
    val membership: SignupMembership,
)

@Serializable
data class SignupUser(
    val id: Int,
    val email: String,
    val display_name: String,
)

@Serializable
data class SignupMembership(
    val id: Int,
    val status: String,
    val tier_slug: String,
    val member_number: String? = null,
)
