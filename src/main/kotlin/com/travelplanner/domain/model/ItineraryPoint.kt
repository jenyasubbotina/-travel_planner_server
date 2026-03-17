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
    val type: String? = null,
    val date: LocalDate? = null,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val sortOrder: Int = 0,
    val createdBy: UUID,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long = 1,
    val deletedAt: Instant? = null
)
