package com.travelplanner.infrastructure.persistence.repository

import com.travelplanner.domain.model.ItineraryPoint
import com.travelplanner.domain.model.ItineraryPointStatus
import com.travelplanner.domain.repository.ItineraryRepository
import com.travelplanner.infrastructure.persistence.DatabaseFactory.dbQuery
import com.travelplanner.infrastructure.persistence.tables.ItineraryPointParticipantsTable
import com.travelplanner.infrastructure.persistence.tables.ItineraryPointsTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant
import java.util.UUID

class ExposedItineraryRepository : ItineraryRepository {

    override suspend fun findByTrip(tripId: UUID): List<ItineraryPoint> = dbQuery {
        val rows = ItineraryPointsTable.selectAll()
            .where {
                (ItineraryPointsTable.tripId eq tripId) and
                        ItineraryPointsTable.deletedAt.isNull()
            }
            .orderBy(ItineraryPointsTable.sortOrder)
            .toList()

        val ids = rows.map { it[ItineraryPointsTable.id] }
        val participantsByPoint = loadParticipants(ids)
        rows.map { it.toItineraryPoint(participantsByPoint[it[ItineraryPointsTable.id]].orEmpty()) }
    }

    override suspend fun findById(id: UUID): ItineraryPoint? = dbQuery {
        val row = ItineraryPointsTable.selectAll()
            .where {
                (ItineraryPointsTable.id eq id) and
                        ItineraryPointsTable.deletedAt.isNull()
            }
            .singleOrNull()
            ?: return@dbQuery null
        val participants = loadParticipants(listOf(id))[id].orEmpty()
        row.toItineraryPoint(participants)
    }

    override suspend fun create(point: ItineraryPoint): ItineraryPoint = dbQuery {
        ItineraryPointsTable.insert {
            it[id] = point.id
            it[tripId] = point.tripId
            it[title] = point.title
            it[description] = point.description
            it[subtitle] = point.subtitle
            it[type] = point.type
            it[date] = point.date
            it[dayIndex] = point.dayIndex
            it[startTime] = point.startTime
            it[endTime] = point.endTime
            it[duration] = point.duration
            it[latitude] = point.latitude
            it[longitude] = point.longitude
            it[address] = point.address
            it[cost] = point.cost
            it[actualCost] = point.actualCost
            it[status] = point.status.name
            it[sortOrder] = point.sortOrder
            it[createdBy] = point.createdBy
            it[createdAt] = point.createdAt
            it[updatedAt] = point.updatedAt
            it[version] = point.version
            it[deletedAt] = point.deletedAt
        }
        replaceParticipantsFor(point.id, point.participantIds)
        point
    }

    override suspend fun update(point: ItineraryPoint): ItineraryPoint = dbQuery {
        val now = Instant.now()
        val newVersion = point.version + 1
        ItineraryPointsTable.update({ ItineraryPointsTable.id eq point.id }) {
            it[title] = point.title
            it[description] = point.description
            it[subtitle] = point.subtitle
            it[type] = point.type
            it[date] = point.date
            it[dayIndex] = point.dayIndex
            it[startTime] = point.startTime
            it[endTime] = point.endTime
            it[duration] = point.duration
            it[latitude] = point.latitude
            it[longitude] = point.longitude
            it[address] = point.address
            it[cost] = point.cost
            it[actualCost] = point.actualCost
            it[status] = point.status.name
            it[sortOrder] = point.sortOrder
            it[updatedAt] = now
            it[version] = newVersion
        }
        replaceParticipantsFor(point.id, point.participantIds)
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
        val rows = ItineraryPointsTable.selectAll()
            .where {
                (ItineraryPointsTable.tripId eq tripId) and
                        (ItineraryPointsTable.updatedAt greater after)
            }
            .toList()
        val ids = rows.map { it[ItineraryPointsTable.id] }
        val participantsByPoint = loadParticipants(ids)
        rows.map { it.toItineraryPoint(participantsByPoint[it[ItineraryPointsTable.id]].orEmpty()) }
    }

    private fun loadParticipants(pointIds: List<UUID>): Map<UUID, List<UUID>> {
        if (pointIds.isEmpty()) return emptyMap()
        return ItineraryPointParticipantsTable
            .selectAll()
            .where { ItineraryPointParticipantsTable.pointId inList pointIds }
            .groupBy({ it[ItineraryPointParticipantsTable.pointId] }, { it[ItineraryPointParticipantsTable.userId] })
    }

    private fun replaceParticipantsFor(pointId: UUID, participantIds: List<UUID>) {
        ItineraryPointParticipantsTable.deleteWhere { ItineraryPointParticipantsTable.pointId eq pointId }
        if (participantIds.isNotEmpty()) {
            ItineraryPointParticipantsTable.batchInsert(participantIds.distinct()) { userId ->
                this[ItineraryPointParticipantsTable.pointId] = pointId
                this[ItineraryPointParticipantsTable.userId] = userId
            }
        }
    }

    private fun ResultRow.toItineraryPoint(participantIds: List<UUID>) = ItineraryPoint(
        id = this[ItineraryPointsTable.id],
        tripId = this[ItineraryPointsTable.tripId],
        title = this[ItineraryPointsTable.title],
        description = this[ItineraryPointsTable.description],
        subtitle = this[ItineraryPointsTable.subtitle],
        type = this[ItineraryPointsTable.type],
        date = this[ItineraryPointsTable.date],
        dayIndex = this[ItineraryPointsTable.dayIndex],
        startTime = this[ItineraryPointsTable.startTime],
        endTime = this[ItineraryPointsTable.endTime],
        duration = this[ItineraryPointsTable.duration],
        latitude = this[ItineraryPointsTable.latitude],
        longitude = this[ItineraryPointsTable.longitude],
        address = this[ItineraryPointsTable.address],
        cost = this[ItineraryPointsTable.cost],
        actualCost = this[ItineraryPointsTable.actualCost],
        status = runCatching { ItineraryPointStatus.valueOf(this[ItineraryPointsTable.status]) }
            .getOrDefault(ItineraryPointStatus.NONE),
        participantIds = participantIds,
        sortOrder = this[ItineraryPointsTable.sortOrder],
        createdBy = this[ItineraryPointsTable.createdBy],
        createdAt = this[ItineraryPointsTable.createdAt],
        updatedAt = this[ItineraryPointsTable.updatedAt],
        version = this[ItineraryPointsTable.version],
        deletedAt = this[ItineraryPointsTable.deletedAt]
    )
}
