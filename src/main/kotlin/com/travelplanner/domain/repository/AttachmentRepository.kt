package com.travelplanner.domain.repository

import com.travelplanner.domain.model.Attachment
import java.time.Instant
import java.util.UUID

interface AttachmentRepository {
    suspend fun findByTrip(tripId: UUID): List<Attachment>
    suspend fun findByExpense(expenseId: UUID): List<Attachment>
    suspend fun findById(id: UUID): Attachment?
    suspend fun create(attachment: Attachment): Attachment
    suspend fun softDelete(id: UUID, deletedAt: Instant): Boolean
    suspend fun findModifiedAfter(tripId: UUID, after: Instant): List<Attachment>
}
