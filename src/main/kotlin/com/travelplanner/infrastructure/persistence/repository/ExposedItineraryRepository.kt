package com.travelplanner.infrastructure.persistence.repository

import com.travelplanner.domain.model.ItineraryPoint
import com.travelplanner.domain.model.ItineraryPointComment
import com.travelplanner.domain.model.ItineraryPointLink
import com.travelplanner.domain.model.ItineraryPointStatus
import com.travelplanner.domain.repository.ItineraryRepository
import com.travelplanner.infrastructure.persistence.DatabaseFactory.dbQuery
import com.travelplanner.infrastructure.persistence.tables.ItineraryPointCommentsTable
import com.travelplanner.infrastructure.persistence.tables.ItineraryPointLinksTable
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
            it[category] = point.category
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
            it[category] = point.category
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

    override suspend fun findLinks(pointId: UUID): List<ItineraryPointLink> = dbQuery {
        ItineraryPointLinksTable.selectAll()
            .where { ItineraryPointLinksTable.pointId eq pointId }
            .orderBy(ItineraryPointLinksTable.sortOrder to SortOrder.ASC, ItineraryPointLinksTable.createdAt to SortOrder.ASC)
            .map { it.toLink() }
    }

    override suspend fun findLink(linkId: UUID, pointId: UUID): ItineraryPointLink? = dbQuery {
        ItineraryPointLinksTable.selectAll()
            .where { (ItineraryPointLinksTable.id eq linkId) and (ItineraryPointLinksTable.pointId eq pointId) }
            .singleOrNull()
            ?.toLink()
    }

    override suspend fun addLink(link: ItineraryPointLink): ItineraryPointLink = dbQuery {
        ItineraryPointLinksTable.insert {
            it[id] = link.id
            it[pointId] = link.pointId
            it[title] = link.title
            it[url] = link.url
            it[sortOrder] = link.sortOrder
            it[createdAt] = link.createdAt
        }
        link
    }

    override suspend fun deleteLink(linkId: UUID, pointId: UUID): Boolean = dbQuery {
        ItineraryPointLinksTable.deleteWhere {
            (ItineraryPointLinksTable.id eq linkId) and (ItineraryPointLinksTable.pointId eq pointId)
        } > 0
    }

    override suspend fun nextLinkSortOrder(pointId: UUID): Int = dbQuery {
        val maxOrder = ItineraryPointLinksTable.sortOrder.max()
        ItineraryPointLinksTable
            .select(maxOrder)
            .where { ItineraryPointLinksTable.pointId eq pointId }
            .singleOrNull()
            ?.get(maxOrder)
            ?.let { it + 1 }
            ?: 0
    }

    override suspend fun findComments(pointId: UUID, limit: Int, offset: Int): List<ItineraryPointComment> = dbQuery {
        ItineraryPointCommentsTable.selectAll()
            .where { ItineraryPointCommentsTable.pointId eq pointId }
            .orderBy(ItineraryPointCommentsTable.createdAt, SortOrder.DESC)
            .limit(limit)
            .offset(offset.toLong())
            .map { it.toComment() }
    }

    override suspend fun addComment(comment: ItineraryPointComment): ItineraryPointComment = dbQuery {
        ItineraryPointCommentsTable.insert {
            it[id] = comment.id
            it[pointId] = comment.pointId
            it[authorUserId] = comment.authorUserId
            it[text] = comment.text
            it[createdAt] = comment.createdAt
        }
        comment
    }

    private fun ResultRow.toLink() = ItineraryPointLink(
        id = this[ItineraryPointLinksTable.id],
        pointId = this[ItineraryPointLinksTable.pointId],
        title = this[ItineraryPointLinksTable.title],
        url = this[ItineraryPointLinksTable.url],
        sortOrder = this[ItineraryPointLinksTable.sortOrder],
        createdAt = this[ItineraryPointLinksTable.createdAt],
    )

    private fun ResultRow.toComment() = ItineraryPointComment(
        id = this[ItineraryPointCommentsTable.id],
        pointId = this[ItineraryPointCommentsTable.pointId],
        authorUserId = this[ItineraryPointCommentsTable.authorUserId],
        text = this[ItineraryPointCommentsTable.text],
        createdAt = this[ItineraryPointCommentsTable.createdAt],
    )

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
        category = this[ItineraryPointsTable.category],
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
