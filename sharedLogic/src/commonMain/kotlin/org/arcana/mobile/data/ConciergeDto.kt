package org.arcana.mobile.data

import kotlinx.serialization.Serializable

@Serializable
data class CreateConciergeRequest(
    val message: String,
)

@Serializable
data class CreateConciergeResponse(
    val id: Int,
    val status: String,
)
