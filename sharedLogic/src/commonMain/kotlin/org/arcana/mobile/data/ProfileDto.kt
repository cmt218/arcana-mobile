package org.arcana.mobile.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET /api/v1/users/me/` — the member's editable profile, as served by the
 * server's `UserProfileSerializer`. Every field is defaulted so a sparse/older
 * payload still deserializes (the JSON client sets `ignoreUnknownKeys = true`).
 *
 * `birthday` is an ISO `yyyy-MM-dd` string (or null if never set); the edit
 * screen converts it to/from the MM/DD/YYYY digit mask. `gender` is the server
 * choice code ("male"/"female"/"other"). `id`/`email` are read-only.
 */
@Serializable
data class MeProfileDto(
    val id: Int = 0,
    val email: String = "",
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name") val lastName: String = "",
    @SerialName("phone_number") val phoneNumber: String = "",
    val gender: String = "",
    val birthday: String? = null,
    @SerialName("address_line1") val addressLine1: String = "",
    @SerialName("address_line2") val addressLine2: String = "",
    val city: String = "",
    val state: String = "",
    @SerialName("postal_code") val postalCode: String = "",
)

/**
 * Body for `PATCH /api/v1/users/me/`. Carries only the fields the edit-profile
 * screen manages — password, email, and avatar are intentionally absent, so a
 * PATCH never touches them. Validation (non-blank, 18+ birthday, 10+ phone
 * digits) is enforced client-side before this is built; the server stores what
 * it's given.
 */
@Serializable
data class UpdateProfileRequest(
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    @SerialName("phone_number") val phoneNumber: String,
    val gender: String,
    val birthday: String,        // ISO yyyy-MM-dd
    @SerialName("address_line1") val addressLine1: String,
    @SerialName("address_line2") val addressLine2: String,
    val city: String,
    val state: String,
    @SerialName("postal_code") val postalCode: String,
)
