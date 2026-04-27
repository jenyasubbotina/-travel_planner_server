package com.travelplanner.domain.model

import java.time.Instant
import java.util.UUID

data class Attachment(
    val id: UUID,
    val tripId: UUID,
    val expenseId: UUID? = null,
    val pointId: UUID? = null,
    val uploadedBy: UUID,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val s3Key: String,
    val createdAt: Instant,
    val deletedAt: Instant? = null
)
