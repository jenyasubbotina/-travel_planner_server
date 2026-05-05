package com.travelplanner.domain.repository

import java.util.UUID

data class IdempotencyRecord(
    val key: String,
    val userId: UUID,
    val responseStatus: Int,
    val responseBody: String?
)

interface IdempotencyRepository {
    suspend fun find(key: String, userId: UUID): IdempotencyRecord?
    suspend fun save(record: IdempotencyRecord)
    suspend fun cleanExpired()
}
