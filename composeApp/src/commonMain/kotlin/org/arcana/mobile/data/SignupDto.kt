package org.arcana.mobile.data

import kotlinx.serialization.Serializable

@Serializable
data class CompleteSignupRequest(
    val token: String,
    val password: String,
    val display_name: String,
    val phone_number: String,
    // Demographic + structured-address fields collected on the same screen.
    val gender: String,
    val birthday: String,        // ISO yyyy-MM-dd
    val address_line1: String,
    val address_line2: String,
    val city: String,
    val state: String,
    val postal_code: String,
)

/**
 * Camel-cased domain carrier for the profile fields the claim-your-name screen
 * collects alongside name + password. Kept separate from the wire DTO so the
 * ViewModel and API interface don't pass an 11-argument positional list around.
 */
data class SignupProfile(
    val gender: String,
    val birthday: String,        // ISO yyyy-MM-dd
    val addressLine1: String,
    val addressLine2: String,
    val city: String,
    val state: String,
    val postalCode: String,
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
