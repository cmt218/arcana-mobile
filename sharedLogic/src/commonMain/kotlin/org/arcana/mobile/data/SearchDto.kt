package org.arcana.mobile.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET /api/v1/classes/search-entities/` — the search screen's chip data.
 *  Contract: arcana-server docs/superpowers/specs/2026-08-29-class-search-design.md. */
@Serializable
data class SearchEntitiesDto(
    val studios: List<SearchStudioDto> = emptyList(),
    val locations: List<SearchLocationDto> = emptyList(),
    val instructors: List<SearchInstructorDto> = emptyList(),
)

@Serializable
data class SearchLocationDto(
    val id: Int,
    val name: String,
    @SerialName("studio_name") val studioName: String,
)

@Serializable
data class SearchStudioDto(
    val slug: String,
    val name: String,
    @SerialName("logo_url") val logoUrl: String = "",
    @SerialName("primary_color") val primaryColor: String = "",
)

@Serializable
data class SearchInstructorDto(val name: String)
