package org.arcana.mobile.networking

import org.arcana.mobile.data.MeProfileDto
import org.arcana.mobile.data.UpdateProfileRequest

/**
 * Narrow seam over the member's own profile (`/api/v1/users/me/`) so the
 * EditProfile ViewModel can be faked in commonTest without an HttpClient.
 * `ArcanaApiClient` is the production implementation.
 */
interface ProfileApi {
    /** GET the current profile to pre-fill the edit form. */
    suspend fun fetchProfile(): MeProfileDto

    /** PATCH the editable profile fields; returns the updated profile. */
    suspend fun updateProfile(body: UpdateProfileRequest): MeProfileDto
}
