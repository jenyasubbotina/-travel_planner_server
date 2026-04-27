package com.travelplanner.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class ItineraryPoint(
    val id: UUID,
    val tripId: UUID,
    val title: String,
    val description: String? = null,
    val subtitle: String? = null,
    val type: String? = null,
    val category: String? = null,
    val date: LocalDate? = null,
    val dayIndex: Int = 0,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val duration: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val cost: Double? = null,
    val actualCost: Double? = null,
    val status: ItineraryPointStatus = ItineraryPointStatus.NONE,
    val participantIds: List<UUID> = emptyList(),
    val sortOrder: Int = 0,
    val createdBy: UUID,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long = 1,
    val deletedAt: Instant? = null
)

enum class ItineraryPointStatus {
    NONE,
    PLANNED,
    CONFIRMED,
    BOOKED,
    PAID,
    CANCELLED
}
