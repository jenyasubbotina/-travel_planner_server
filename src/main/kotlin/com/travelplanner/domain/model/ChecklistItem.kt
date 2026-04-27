package com.travelplanner.domain.model

import java.time.Instant
import java.util.UUID

data class ChecklistItem(
    val id: UUID,
    val tripId: UUID,
    val title: String,
    val isGroup: Boolean,
    val ownerUserId: UUID,
    val completedBy: List<UUID> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
)
