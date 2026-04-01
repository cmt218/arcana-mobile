package org.cadence.mobile.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClassDto(
    val id: Int,
    val name: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    val instructor: String,
    val studio: String,
    @SerialName("available_spots") val availableSpots: Int,
    @SerialName("total_spots") val totalSpots: Int,
)
