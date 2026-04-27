package com.travelplanner.domain.repository

import com.travelplanner.domain.model.*
import java.time.Instant
import java.util.UUID

interface SyncRepository {
    suspend fun getTripSnapshot(tripId: UUID, userId: UUID): TripSnapshot?
    suspend fun getDelta(tripId: UUID, userId: UUID, after: Instant): SyncDelta
}
