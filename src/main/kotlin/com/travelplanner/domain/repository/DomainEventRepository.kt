package com.travelplanner.domain.repository

import com.travelplanner.domain.model.DomainEvent
import java.util.UUID

interface DomainEventRepository {
    suspend fun save(event: DomainEvent): DomainEvent
    suspend fun findUnprocessed(limit: Int = 50): List<DomainEvent>
    suspend fun markProcessed(id: UUID)
    suspend fun incrementRetry(id: UUID)
}
