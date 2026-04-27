package com.travelplanner.domain.model

import java.time.Instant
import java.util.UUID

data class ItineraryPointComment(
    val id: UUID,
    val pointId: UUID,
    val authorUserId: UUID,
    val text: String,
    val createdAt: Instant,
)
