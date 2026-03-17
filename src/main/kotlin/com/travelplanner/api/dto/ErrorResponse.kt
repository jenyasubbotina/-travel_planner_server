package com.travelplanner.api.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
    val details: JsonElement? = null,
    val traceId: String? = null
)
