package com.travelplanner.domain.model

import java.time.Instant
import java.util.UUID

data class TripParticipant(
    val tripId: UUID,
    val userId: UUID,
    val role: TripRole,
    val joinedAt: Instant,
    val updatedAt: Instant = joinedAt,
    val version: Long = 1L,
    val deletedAt: Instant? = null,
)

enum class TripRole {
    OWNER,
    EDITOR,
    VIEWER;

    fun canEdit(): Boolean = this == OWNER || this == EDITOR
    fun canManageParticipants(): Boolean = this == OWNER
}
