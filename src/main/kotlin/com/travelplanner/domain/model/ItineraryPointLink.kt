package com.travelplanner.domain.model

import java.time.Instant
import java.util.UUID

data class ItineraryPointLink(
    val id: UUID,
    val pointId: UUID,
    val title: String,
    val url: String,
    val sortOrder: Int,
    val createdAt: Instant,
)
