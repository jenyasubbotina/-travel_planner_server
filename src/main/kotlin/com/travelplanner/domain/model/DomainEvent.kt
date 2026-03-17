package com.travelplanner.domain.model

import java.time.Instant
import java.util.UUID

data class DomainEvent(
    val id: UUID,
    val eventType: String,
    val aggregateType: String,
    val aggregateId: UUID,
    val payload: String,
    val createdAt: Instant,
    val processedAt: Instant? = null,
    val retryCount: Int = 0
)
