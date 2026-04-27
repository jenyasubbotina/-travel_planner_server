package com.travelplanner.infrastructure.persistence.repository

import com.travelplanner.domain.model.ChecklistItem
import com.travelplanner.domain.repository.ChecklistRepository
import com.travelplanner.infrastructure.persistence.DatabaseFactory.dbQuery
import com.travelplanner.infrastructure.persistence.tables.TripChecklistCompletionsTable
import com.travelplanner.infrastructure.persistence.tables.TripChecklistItemsTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant
import java.util.UUID

class ExposedChecklistRepository : ChecklistRepository {

    override suspend fun findByTripVisibleTo(tripId: UUID, userId: UUID): List<ChecklistItem> = dbQuery {
        val rows = TripChecklistItemsTable.selectAll()
            .where {
                (TripChecklistItemsTable.tripId eq tripId) and
                    (
                        (TripChecklistItemsTable.isGroup eq true) or
                            (TripChecklistItemsTable.ownerUserId eq userId)
                    )
            }
            .orderBy(TripChecklistItemsTable.createdAt, SortOrder.ASC)
            .toList()

        val ids = rows.map { it[TripChecklistItemsTable.id] }
        val completionsByItem = loadCompletions(ids)
        rows.map { it.toChecklistItem(completionsByItem[it[TripChecklistItemsTable.id]].orEmpty()) }
    }

    override suspend fun findById(itemId: UUID): ChecklistItem? = dbQuery {
        val row = TripChecklistItemsTable.selectAll()
            .where { TripChecklistItemsTable.id eq itemId }
            .singleOrNull()
            ?: return@dbQuery null
        val completions = loadCompletions(listOf(itemId))[itemId].orEmpty()
        row.toChecklistItem(completions)
    }

    override suspend fun create(item: ChecklistItem): ChecklistItem = dbQuery {
        TripChecklistItemsTable.insert {
            it[id] = item.id
            it[tripId] = item.tripId
            it[title] = item.title
            it[isGroup] = item.isGroup
            it[ownerUserId] = item.ownerUserId
            it[createdAt] = item.createdAt
            it[updatedAt] = item.updatedAt
        }
        item
    }

    override suspend fun delete(itemId: UUID, tripId: UUID): Boolean = dbQuery {
        TripChecklistItemsTable.deleteWhere {
            (TripChecklistItemsTable.id eq itemId) and (TripChecklistItemsTable.tripId eq tripId)
        } > 0
    }

    override suspend fun toggleCompletion(itemId: UUID, userId: UUID): Boolean = dbQuery {
        val deleted = TripChecklistCompletionsTable.deleteWhere {
            (TripChecklistCompletionsTable.itemId eq itemId) and
                (TripChecklistCompletionsTable.userId eq userId)
        }
        if (deleted > 0) {
            false
        } else {
            TripChecklistCompletionsTable.insert {
                it[TripChecklistCompletionsTable.itemId] = itemId
                it[TripChecklistCompletionsTable.userId] = userId
                it[completedAt] = Instant.now()
            }
            true
        }
    }

    private fun loadCompletions(itemIds: List<UUID>): Map<UUID, List<UUID>> {
        if (itemIds.isEmpty()) return emptyMap()
        return TripChecklistCompletionsTable
            .selectAll()
            .where { TripChecklistCompletionsTable.itemId inList itemIds }
            .groupBy(
                { it[TripChecklistCompletionsTable.itemId] },
                { it[TripChecklistCompletionsTable.userId] }
            )
    }

    private fun ResultRow.toChecklistItem(completedBy: List<UUID>) = ChecklistItem(
        id = this[TripChecklistItemsTable.id],
        tripId = this[TripChecklistItemsTable.tripId],
        title = this[TripChecklistItemsTable.title],
        isGroup = this[TripChecklistItemsTable.isGroup],
        ownerUserId = this[TripChecklistItemsTable.ownerUserId],
        completedBy = completedBy,
        createdAt = this[TripChecklistItemsTable.createdAt],
        updatedAt = this[TripChecklistItemsTable.updatedAt],
    )
}
