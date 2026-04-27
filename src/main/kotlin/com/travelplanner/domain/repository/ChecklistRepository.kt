package com.travelplanner.domain.repository

import com.travelplanner.domain.model.ChecklistItem
import java.util.UUID

interface ChecklistRepository {
    suspend fun findByTripVisibleTo(tripId: UUID, userId: UUID): List<ChecklistItem>
    suspend fun findById(itemId: UUID): ChecklistItem?
    suspend fun create(item: ChecklistItem): ChecklistItem
    suspend fun delete(itemId: UUID, tripId: UUID): Boolean
    suspend fun toggleCompletion(itemId: UUID, userId: UUID): Boolean
}
