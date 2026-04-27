package com.travelplanner.infrastructure.persistence.repository

import com.travelplanner.domain.model.DomainEvent
import com.travelplanner.domain.repository.DomainEventRepository
import com.travelplanner.infrastructure.persistence.DatabaseFactory.dbQuery
import com.travelplanner.infrastructure.persistence.tables.DomainEventsTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant
import java.util.UUID

class ExposedDomainEventRepository : DomainEventRepository {

    override suspend fun save(event: DomainEvent): DomainEvent = dbQuery {
        DomainEventsTable.insert {
            it[id] = event.id
            it[eventType] = event.eventType
            it[aggregateType] = event.aggregateType
            it[aggregateId] = event.aggregateId
            it[payload] = event.payload
            it[createdAt] = event.createdAt
            it[processedAt] = event.processedAt
            it[retryCount] = event.retryCount
        }
        event
    }

    override suspend fun findUnprocessed(limit: Int): List<DomainEvent> = dbQuery {
        DomainEventsTable.selectAll()
            .where { DomainEventsTable.processedAt.isNull() }
            .orderBy(DomainEventsTable.createdAt)
            .limit(limit)
            .map { it.toDomainEvent() }
    }

    override suspend fun markProcessed(id: UUID): Unit = dbQuery {
        DomainEventsTable.update({ DomainEventsTable.id eq id }) {
            it[processedAt] = Instant.now()
        }
    }

    override suspend fun incrementRetry(id: UUID): Unit = dbQuery {
        val newRetryCount = with(SqlExpressionBuilder) { DomainEventsTable.retryCount + 1 }
        DomainEventsTable.update({ DomainEventsTable.id eq id }) {
            it[retryCount] = newRetryCount
        }
    }

    override suspend fun findByAggregateId(aggregateId: UUID, limit: Int, offset: Int): List<DomainEvent> = dbQuery {
        DomainEventsTable.selectAll()
            .where { DomainEventsTable.aggregateId eq aggregateId }
            .orderBy(DomainEventsTable.createdAt, SortOrder.DESC)
            .limit(limit)
            .offset(offset.toLong())
            .map { it.toDomainEvent() }
    }

    // --- Mapping helper ---

    private fun ResultRow.toDomainEvent() = DomainEvent(
        id = this[DomainEventsTable.id],
        eventType = this[DomainEventsTable.eventType],
        aggregateType = this[DomainEventsTable.aggregateType],
        aggregateId = this[DomainEventsTable.aggregateId],
        payload = this[DomainEventsTable.payload],
        createdAt = this[DomainEventsTable.createdAt],
        processedAt = this[DomainEventsTable.processedAt],
        retryCount = this[DomainEventsTable.retryCount]
    )
}
