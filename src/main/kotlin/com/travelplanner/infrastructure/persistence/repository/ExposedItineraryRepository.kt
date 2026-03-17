package com.travelplanner.infrastructure.persistence.repository

import com.travelplanner.domain.model.ItineraryPoint
import com.travelplanner.domain.repository.ItineraryRepository
import com.travelplanner.infrastructure.persistence.DatabaseFactory.dbQuery
import com.travelplanner.infrastructure.persistence.tables.ItineraryPointsTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

class ExposedItineraryRepository : ItineraryRepository {

    override suspend fun findByTrip(tripId: UUID): List<ItineraryPoint> = dbQuery {
        ItineraryPointsTable.selectAll()
            .where {
                (ItineraryPointsTable.tripId eq tripId) and
                    ItineraryPointsTable.deletedAt.isNull()
            }
            .orderBy(ItineraryPointsTable.sortOrder)
            .map { it.toItineraryPoint() }
    }

    override suspend fun findById(id: UUID): ItineraryPoint? = dbQuery {
        ItineraryPointsTable.selectAll()
            .where {
                (ItineraryPointsTable.id eq id) and
                    ItineraryPointsTable.deletedAt.isNull()
            }
            .singleOrNull()
            ?.toItineraryPoint()
    }

    override suspend fun create(point: ItineraryPoint): ItineraryPoint = dbQuery {
        ItineraryPointsTable.insert {
            it[id] = point.id
            it[tripId] = point.tripId
            it[title] = point.title
            it[description] = point.description
            it[type] = point.type
            it[date] = point.date
            it[startTime] = point.startTime?.toString()
            it[endTime] = point.endTime?.toString()
            it[latitude] = point.latitude
            it[longitude] = point.longitude
            it[address] = point.address
            it[sortOrder] = point.sortOrder
            it[createdBy] = point.createdBy
            it[createdAt] = point.createdAt
            it[updatedAt] = point.updatedAt
            it[version] = point.version
            it[deletedAt] = point.deletedAt
        }
        point
    }

    override suspend fun update(point: ItineraryPoint): ItineraryPoint = dbQuery {
        val now = Instant.now()
        val newVersion = point.version + 1
        ItineraryPointsTable.update({ ItineraryPointsTable.id eq point.id }) {
            it[title] = point.title
            it[description] = point.description
            it[type] = point.type
            it[date] = point.date
            it[startTime] = point.startTime?.toString()
            it[endTime] = point.endTime?.toString()
            it[latitude] = point.latitude
            it[longitude] = point.longitude
            it[address] = point.address
            it[sortOrder] = point.sortOrder
            it[updatedAt] = now
            it[version] = newVersion
        }
        point.copy(updatedAt = now, version = newVersion)
    }

    override suspend fun softDelete(id: UUID, deletedAt: Instant): Boolean = dbQuery {
        val updatedCount = ItineraryPointsTable.update({ ItineraryPointsTable.id eq id }) {
            it[ItineraryPointsTable.deletedAt] = deletedAt
            it[updatedAt] = deletedAt
            it[version] = with(SqlExpressionBuilder) { ItineraryPointsTable.version + 1L }
        }
        updatedCount > 0
    }

    override suspend fun updateSortOrders(updates: List<Pair<UUID, Int>>): Unit = dbQuery {
        for ((pointId, sortOrder) in updates) {
            ItineraryPointsTable.update({ ItineraryPointsTable.id eq pointId }) {
                it[ItineraryPointsTable.sortOrder] = sortOrder
                it[updatedAt] = Instant.now()
            }
        }
    }

    override suspend fun findModifiedAfter(tripId: UUID, after: Instant): List<ItineraryPoint> = dbQuery {
        ItineraryPointsTable.selectAll()
            .where {
                (ItineraryPointsTable.tripId eq tripId) and
                    (ItineraryPointsTable.updatedAt greater after)
            }
            .map { it.toItineraryPoint() }
    }

    // --- Mapping helper ---

    private fun ResultRow.toItineraryPoint() = ItineraryPoint(
        id = this[ItineraryPointsTable.id],
        tripId = this[ItineraryPointsTable.tripId],
        title = this[ItineraryPointsTable.title],
        description = this[ItineraryPointsTable.description],
        type = this[ItineraryPointsTable.type],
        date = this[ItineraryPointsTable.date],
        startTime = this[ItineraryPointsTable.startTime]?.let { LocalTime.parse(it) },
        endTime = this[ItineraryPointsTable.endTime]?.let { LocalTime.parse(it) },
        latitude = this[ItineraryPointsTable.latitude],
        longitude = this[ItineraryPointsTable.longitude],
        address = this[ItineraryPointsTable.address],
        sortOrder = this[ItineraryPointsTable.sortOrder],
        createdBy = this[ItineraryPointsTable.createdBy],
        createdAt = this[ItineraryPointsTable.createdAt],
        updatedAt = this[ItineraryPointsTable.updatedAt],
        version = this[ItineraryPointsTable.version],
        deletedAt = this[ItineraryPointsTable.deletedAt]
    )
}
