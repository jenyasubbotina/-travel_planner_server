package com.travelplanner.domain.repository

import com.travelplanner.domain.model.JoinRequestStatus
import com.travelplanner.domain.model.TripJoinRequest
import java.time.Instant
import java.util.UUID

interface JoinRequestRepository {
    suspend fun findPendingByTrip(tripId: UUID): List<TripJoinRequest>
    suspend fun findById(id: UUID): TripJoinRequest?
    suspend fun findPendingByTripAndUser(tripId: UUID, userId: UUID): TripJoinRequest?
    suspend fun create(request: TripJoinRequest): TripJoinRequest
    suspend fun updateStatus(
        id: UUID,
        status: JoinRequestStatus,
        resolverUserId: UUID,
        resolvedAt: Instant
    ): Boolean
}
