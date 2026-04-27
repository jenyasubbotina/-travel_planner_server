package com.travelplanner.domain.model

import java.time.Instant
import java.util.UUID

data class TripJoinRequest(
    val id: UUID,
    val tripId: UUID,
    val requesterUserId: UUID,
    val status: JoinRequestStatus,
    val createdAt: Instant,
    val resolvedAt: Instant? = null,
    val resolvedBy: UUID? = null,
)

enum class JoinRequestStatus { PENDING, APPROVED, DENIED }
