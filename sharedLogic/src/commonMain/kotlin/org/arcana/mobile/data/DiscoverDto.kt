package org.arcana.mobile.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DiscoverCategoryDto(val slug: String, val name: String)

/** One brand in `GET /api/v1/discover/studios/`. */
@Serializable
data class DiscoverStudioDto(
    val slug: String,
    val name: String,
    val tagline: String = "",
    @SerialName("primary_color") val primaryColor: String = "",
    @SerialName("logo_url") val logoUrl: String = "",
    val categories: List<DiscoverCategoryDto> = emptyList(),
    val neighborhoods: List<String> = emptyList(),
    @SerialName("location_count") val locationCount: Int = 0,
)

@Serializable
data class DiscoverDirectoryDto(
    val studios: List<DiscoverStudioDto> = emptyList(),
    val feedback: DiscoverFeedbackDto = DiscoverFeedbackDto(),
)

@Serializable
data class AmenityDto(
    val slug: String = "",
    val label: String = "",
    @SerialName("icon_key") val iconKey: String = "",
)

@Serializable
data class StudioPageLocationDto(
    val id: Int,
    val name: String,
    val neighborhood: String = "",
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("review_count") val reviewCount: Int = 0,
)

@Serializable
data class StudioClassTypeDto(
    val key: String,
    val label: String,
    val description: String = "",
    val categories: List<String> = emptyList(),
    @SerialName("review_count") val reviewCount: Int = 0,
)

@Serializable
data class StudioInstructorDto(
    @SerialName("profile_id") val profileId: Int,
    val name: String,
    val bio: String = "",
    @SerialName("photo_url") val photoUrl: String = "",
    @SerialName("review_count") val reviewCount: Int = 0,
)

/** `GET /api/v1/discover/studios/<slug>/`. `bio` already falls back to the
 *  synced description server-side. */
@Serializable
data class StudioPageDto(
    val slug: String,
    val name: String,
    val tagline: String = "",
    @SerialName("primary_color") val primaryColor: String = "",
    @SerialName("logo_url") val logoUrl: String = "",
    val categories: List<DiscoverCategoryDto> = emptyList(),
    val neighborhoods: List<String> = emptyList(),
    @SerialName("location_count") val locationCount: Int = 0,
    val bio: String = "",
    @SerialName("good_to_know") val goodToKnow: String = "",
    val website: String = "",
    val instagram: String = "",
    val amenities: List<AmenityDto> = emptyList(),
    @SerialName("cancellation_cutoff_minutes") val cancellationCutoffMinutes: Int? = null,
    @SerialName("review_count") val reviewCount: Int = 0,
    val locations: List<StudioPageLocationDto> = emptyList(),
    @SerialName("class_types") val classTypes: List<StudioClassTypeDto> = emptyList(),
    val instructors: List<StudioInstructorDto> = emptyList(),
    @SerialName("favorite_location_ids") val favoriteLocationIds: List<Int> = emptyList(),
)
