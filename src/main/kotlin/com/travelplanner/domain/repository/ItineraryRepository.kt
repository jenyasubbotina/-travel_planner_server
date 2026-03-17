package com.travelplanner.domain.repository

import com.travelplanner.domain.model.ItineraryPoint
import java.time.Instant
import java.util.UUID

interface ItineraryRepository {
    suspend fun findByTrip(tripId: UUID): List<ItineraryPoint>
    suspend fun findById(id: UUID): ItineraryPoint?
    suspend fun create(point: ItineraryPoint): ItineraryPoint
    suspend fun update(point: ItineraryPoint): ItineraryPoint
    suspend fun softDelete(id: UUID, deletedAt: Instant): Boolean
    suspend fun updateSortOrders(updates: List<Pair<UUID, Int>>)
    suspend fun findModifiedAfter(tripId: UUID, after: Instant): List<ItineraryPoint>
}
