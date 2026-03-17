package com.travelplanner.domain.model

import java.time.Instant
import java.util.UUID

data class TripParticipant(
    val tripId: UUID,
    val userId: UUID,
    val role: TripRole,
    val joinedAt: Instant
)

enum class TripRole {
    OWNER,
    EDITOR,
    VIEWER;

    fun canEdit(): Boolean = this == OWNER || this == EDITOR
    fun canManageParticipants(): Boolean = this == OWNER
}
