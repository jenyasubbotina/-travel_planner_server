package com.travelplanner.domain.repository

import com.travelplanner.domain.model.Trip
import java.time.Instant
import java.util.UUID

interface TripRepository {
    suspend fun findById(id: UUID): Trip?
    suspend fun findByIdIncludeDeleted(id: UUID): Trip?
    suspend fun findByUser(userId: UUID): List<Trip>
    suspend fun create(trip: Trip): Trip
    suspend fun update(trip: Trip): Trip
    suspend fun softDelete(id: UUID, deletedAt: Instant): Boolean
    suspend fun findModifiedAfter(tripId: UUID, after: Instant): List<Trip>
}
