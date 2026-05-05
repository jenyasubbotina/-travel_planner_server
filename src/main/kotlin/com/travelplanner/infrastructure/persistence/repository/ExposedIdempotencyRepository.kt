package com.travelplanner.infrastructure.persistence.repository

import com.travelplanner.domain.repository.IdempotencyRecord
import com.travelplanner.domain.repository.IdempotencyRepository
import com.travelplanner.infrastructure.persistence.DatabaseFactory.dbQuery
import com.travelplanner.infrastructure.persistence.tables.IdempotencyKeysTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class ExposedIdempotencyRepository : IdempotencyRepository {

    override suspend fun find(key: String, userId: UUID): IdempotencyRecord? = dbQuery {
        val now = Instant.now()
        IdempotencyKeysTable.selectAll()
            .where {
                (IdempotencyKeysTable.key eq key) and
                    (IdempotencyKeysTable.userId eq userId) and
                    (IdempotencyKeysTable.expiresAt greater now)
            }
            .singleOrNull()
            ?.toIdempotencyRecord()
    }

    override suspend fun save(record: IdempotencyRecord): Unit = dbQuery {
        val now = Instant.now()
        val expiresAt = now.plus(7, ChronoUnit.DAYS)
        IdempotencyKeysTable.insert {
            it[key] = record.key
            it[userId] = record.userId
            it[responseStatus] = record.responseStatus
            it[responseBody] = record.responseBody
            it[createdAt] = now
            it[IdempotencyKeysTable.expiresAt] = expiresAt
        }
    }

    override suspend fun cleanExpired(): Unit = dbQuery {
        val now = Instant.now()
        IdempotencyKeysTable.deleteWhere { expiresAt less now }
    }

    // --- Mapping helper ---

    private fun ResultRow.toIdempotencyRecord() = IdempotencyRecord(
        key = this[IdempotencyKeysTable.key],
        userId = this[IdempotencyKeysTable.userId],
        responseStatus = this[IdempotencyKeysTable.responseStatus],
        responseBody = this[IdempotencyKeysTable.responseBody]
    )
}
