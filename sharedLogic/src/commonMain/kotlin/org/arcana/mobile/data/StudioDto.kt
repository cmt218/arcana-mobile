package org.arcana.mobile.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Response row for `GET /api/v1/studios/` (active Partners with active
 *  locations nested). Used by the favorites manager + Schedule chip rail. */
@Serializable
data class StudioDto(
    val id: Int,
    val slug: String,
    val name: String,
    @SerialName("logo_url") val logoUrl: String = "",
    @SerialName("hero_image_url") val heroImageUrl: String = "",
    @SerialName("primary_color") val primaryColor: String = "",
    val locations: List<StudioLocationDto> = emptyList(),
)

@Serializable
data class StudioLocationDto(
    val id: Int,
    val name: String,
    val address: String = "",
    val timezone: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
)
