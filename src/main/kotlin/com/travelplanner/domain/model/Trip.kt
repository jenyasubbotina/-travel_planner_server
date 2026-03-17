package com.travelplanner.domain.model

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class Trip(
    val id: UUID,
    val title: String,
    val description: String? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val baseCurrency: String = "USD",
    val status: TripStatus = TripStatus.ACTIVE,
    val createdBy: UUID,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long = 1,
    val deletedAt: Instant? = null
)

enum class TripStatus {
    ACTIVE,
    ARCHIVED,
    COMPLETED
}
