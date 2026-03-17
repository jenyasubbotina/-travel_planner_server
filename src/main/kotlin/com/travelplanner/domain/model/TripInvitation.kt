package com.travelplanner.domain.model

import java.time.Instant
import java.util.UUID

data class TripInvitation(
    val id: UUID,
    val tripId: UUID,
    val email: String,
    val invitedBy: UUID,
    val role: TripRole = TripRole.EDITOR,
    val status: InvitationStatus = InvitationStatus.PENDING,
    val createdAt: Instant,
    val resolvedAt: Instant? = null
)

enum class InvitationStatus {
    PENDING,
    ACCEPTED,
    DECLINED
}
