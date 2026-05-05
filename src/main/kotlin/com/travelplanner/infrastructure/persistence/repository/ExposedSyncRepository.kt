package com.travelplanner.infrastructure.persistence.repository

import com.travelplanner.domain.model.*
import com.travelplanner.domain.repository.SyncRepository
import com.travelplanner.infrastructure.persistence.DatabaseFactory.dbQuery
import com.travelplanner.infrastructure.persistence.tables.*
import org.jetbrains.exposed.sql.*
import java.time.Instant
import java.util.UUID

class ExposedSyncRepository : SyncRepository {

    override suspend fun getTripSnapshot(tripId: UUID, userId: UUID): TripSnapshot? = dbQuery {
        val trip = TripsTable.selectAll()
            .where { (TripsTable.id eq tripId) and TripsTable.deletedAt.isNull() }
            .singleOrNull()
            ?.toTrip()
            ?: return@dbQuery null

        val participants = TripParticipantsTable.selectAll()
            .where {
                (TripParticipantsTable.tripId eq tripId) and
                        TripParticipantsTable.deletedAt.isNull()
            }
            .map { it.toTripParticipant() }

        val itineraryRows = ItineraryPointsTable.selectAll()
            .where {
                (ItineraryPointsTable.tripId eq tripId) and
                        ItineraryPointsTable.deletedAt.isNull()
            }
            .orderBy(ItineraryPointsTable.sortOrder)
            .toList()
        val pointIds = itineraryRows.map { it[ItineraryPointsTable.id] }
        val itineraryParticipantIds = loadItineraryParticipants(pointIds)
        val itineraryPoints = itineraryRows.map {
            it.toItineraryPoint(itineraryParticipantIds[it[ItineraryPointsTable.id]].orEmpty())
        }

        val expenses = ExpensesTable.selectAll()
            .where {
                (ExpensesTable.tripId eq tripId) and ExpensesTable.deletedAt.isNull()
            }
            .map { it.toExpense() }

        val expenseIds = expenses.map { it.id }
        val expenseSplits = if (expenseIds.isNotEmpty()) {
            ExpenseSplitsTable.selectAll()
                .where { ExpenseSplitsTable.expenseId inList expenseIds }
                .map { it.toExpenseSplit() }
        } else {
            emptyList()
        }

        val attachments = AttachmentsTable.selectAll()
            .where {
                (AttachmentsTable.tripId eq tripId) and AttachmentsTable.deletedAt.isNull()
            }
            .map { it.toAttachment() }

        val checklistItems = loadChecklistVisibleTo(tripId, userId, after = null)
        val pendingJoinRequests = loadPendingJoinRequestsIfOwner(tripId, userId, after = null)
        val historyEntries = loadHistoryEntries(tripId, after = null, limit = 200)
        val pointLinks = loadPointLinks(tripId, after = null)
        val pointComments = loadPointComments(tripId, after = null)

        val now = Instant.now()
        TripSnapshot(
            trip = trip,
            participants = participants,
            itineraryPoints = itineraryPoints,
            expenses = expenses,
            expenseSplits = expenseSplits,
            attachments = attachments,
            checklistItems = checklistItems,
            pendingJoinRequests = pendingJoinRequests,
            historyEntries = historyEntries,
            pointLinks = pointLinks,
            pointComments = pointComments,
            cursor = SyncCursor(timestamp = now)
        )
    }

    override suspend fun getDelta(tripId: UUID, userId: UUID, after: Instant): SyncDelta = dbQuery {
        val trips = TripsTable.selectAll()
            .where {
                (TripsTable.id eq tripId) and (TripsTable.updatedAt greater after)
            }
            .map { it.toTrip() }

        val participants = TripParticipantsTable.selectAll()
            .where {
                (TripParticipantsTable.tripId eq tripId) and
                        (TripParticipantsTable.updatedAt greater after)
            }
            .map { it.toTripParticipant() }

        val itineraryRows = ItineraryPointsTable.selectAll()
            .where {
                (ItineraryPointsTable.tripId eq tripId) and
                        (ItineraryPointsTable.updatedAt greater after)
            }
            .toList()
        val itineraryParticipantIds = loadItineraryParticipants(
            itineraryRows.map { it[ItineraryPointsTable.id] }
        )
        val itineraryPoints = itineraryRows.map {
            it.toItineraryPoint(itineraryParticipantIds[it[ItineraryPointsTable.id]].orEmpty())
        }

        val expenses = ExpensesTable.selectAll()
            .where {
                (ExpensesTable.tripId eq tripId) and
                        (ExpensesTable.updatedAt greater after)
            }
            .map { it.toExpense() }

        val modifiedExpenseIds = expenses.map { it.id }
        val expenseSplits = if (modifiedExpenseIds.isNotEmpty()) {
            ExpenseSplitsTable.selectAll()
                .where { ExpenseSplitsTable.expenseId inList modifiedExpenseIds }
                .map { it.toExpenseSplit() }
        } else {
            emptyList()
        }

        val attachments = AttachmentsTable.selectAll()
            .where {
                (AttachmentsTable.tripId eq tripId) and
                        (
                                (AttachmentsTable.createdAt greater after) or
                                        (AttachmentsTable.deletedAt greater after)
                                )
            }
            .map { it.toAttachment() }

        val checklistItems = loadChecklistVisibleTo(tripId, userId, after = after)
        val pendingJoinRequests = loadPendingJoinRequestsIfOwner(tripId, userId, after = after)
        val historyEntries = loadHistoryEntries(tripId, after = after, limit = null)
        val pointLinks = loadPointLinks(tripId, after = after)
        val pointComments = loadPointComments(tripId, after = after)

        val now = Instant.now()
        SyncDelta(
            trips = trips,
            participants = participants,
            itineraryPoints = itineraryPoints,
            expenses = expenses,
            expenseSplits = expenseSplits,
            attachments = attachments,
            checklistItems = checklistItems,
            pendingJoinRequests = pendingJoinRequests,
            historyEntries = historyEntries,
            pointLinks = pointLinks,
            pointComments = pointComments,
            cursor = SyncCursor(timestamp = now)
        )
    }

    // --- New entity loaders ---

    private fun loadChecklistVisibleTo(tripId: UUID, userId: UUID, after: Instant?): List<ChecklistItem> {
        val rows = TripChecklistItemsTable.selectAll()
            .where {
                val base = (TripChecklistItemsTable.tripId eq tripId) and
                        (
                                (TripChecklistItemsTable.isGroup eq true) or
                                        (TripChecklistItemsTable.ownerUserId eq userId)
                                )
                if (after != null) {
                    base and (TripChecklistItemsTable.updatedAt greater after)
                } else {
                    base
                }
            }
            .toList()

        if (rows.isEmpty()) return emptyList()
        val itemIds = rows.map { it[TripChecklistItemsTable.id] }
        val completionsByItem = TripChecklistCompletionsTable
            .selectAll()
            .where { TripChecklistCompletionsTable.itemId inList itemIds }
            .groupBy(
                { it[TripChecklistCompletionsTable.itemId] },
                { it[TripChecklistCompletionsTable.userId] }
            )
        return rows.map { row ->
            ChecklistItem(
                id = row[TripChecklistItemsTable.id],
                tripId = row[TripChecklistItemsTable.tripId],
                title = row[TripChecklistItemsTable.title],
                isGroup = row[TripChecklistItemsTable.isGroup],
                ownerUserId = row[TripChecklistItemsTable.ownerUserId],
                completedBy = completionsByItem[row[TripChecklistItemsTable.id]].orEmpty(),
                createdAt = row[TripChecklistItemsTable.createdAt],
                updatedAt = row[TripChecklistItemsTable.updatedAt],
            )
        }
    }

    private fun loadPendingJoinRequestsIfOwner(
        tripId: UUID,
        userId: UUID,
        after: Instant?,
    ): List<JoinRequestWithUser> {
        val isOwner = TripParticipantsTable
            .selectAll()
            .where {
                (TripParticipantsTable.tripId eq tripId) and
                        (TripParticipantsTable.userId eq userId) and
                        (TripParticipantsTable.role eq TripRole.OWNER.name)
            }
            .empty()
            .not()
        if (!isOwner) return emptyList()

        val joined = TripJoinRequestsTable
            .innerJoin(UsersTable, { TripJoinRequestsTable.requesterUserId }, { UsersTable.id })
            .selectAll()
            .where {
                val base = (TripJoinRequestsTable.tripId eq tripId) and
                        (TripJoinRequestsTable.status eq JoinRequestStatus.PENDING.name)
                if (after != null) {
                    base and (TripJoinRequestsTable.createdAt greater after)
                } else {
                    base
                }
            }
            .orderBy(TripJoinRequestsTable.createdAt, SortOrder.DESC)

        return joined.map { row ->
            val request = TripJoinRequest(
                id = row[TripJoinRequestsTable.id],
                tripId = row[TripJoinRequestsTable.tripId],
                requesterUserId = row[TripJoinRequestsTable.requesterUserId],
                status = JoinRequestStatus.valueOf(row[TripJoinRequestsTable.status]),
                createdAt = row[TripJoinRequestsTable.createdAt],
                resolvedAt = row[TripJoinRequestsTable.resolvedAt],
                resolvedBy = row[TripJoinRequestsTable.resolvedBy],
            )
            JoinRequestWithUser(
                request = request,
                displayName = row[UsersTable.displayName],
                email = row[UsersTable.email],
            )
        }
    }

    private fun loadHistoryEntries(tripId: UUID, after: Instant?, limit: Int?): List<DomainEvent> {
        val baseQuery = DomainEventsTable.selectAll()
            .where {
                val base = DomainEventsTable.aggregateId eq tripId
                if (after != null) {
                    base and (DomainEventsTable.createdAt greater after)
                } else {
                    base
                }
            }
            .orderBy(DomainEventsTable.createdAt, SortOrder.DESC)
        val query = if (limit != null) baseQuery.limit(limit) else baseQuery
        return query.map { row ->
            DomainEvent(
                id = row[DomainEventsTable.id],
                eventType = row[DomainEventsTable.eventType],
                aggregateType = row[DomainEventsTable.aggregateType],
                aggregateId = row[DomainEventsTable.aggregateId],
                payload = row[DomainEventsTable.payload],
                createdAt = row[DomainEventsTable.createdAt],
                processedAt = row[DomainEventsTable.processedAt],
                retryCount = row[DomainEventsTable.retryCount],
            )
        }
    }

    private fun loadPointLinks(tripId: UUID, after: Instant?): List<ItineraryPointLink> {
        val query = ItineraryPointLinksTable
            .innerJoin(ItineraryPointsTable, { ItineraryPointLinksTable.pointId }, { ItineraryPointsTable.id })
            .selectAll()
            .where {
                val base = (ItineraryPointsTable.tripId eq tripId) and
                        ItineraryPointsTable.deletedAt.isNull()
                if (after != null) {
                    base and (ItineraryPointLinksTable.createdAt greater after)
                } else {
                    base
                }
            }
            .orderBy(ItineraryPointLinksTable.sortOrder to SortOrder.ASC, ItineraryPointLinksTable.createdAt to SortOrder.ASC)
        return query.map { row ->
            ItineraryPointLink(
                id = row[ItineraryPointLinksTable.id],
                pointId = row[ItineraryPointLinksTable.pointId],
                title = row[ItineraryPointLinksTable.title],
                url = row[ItineraryPointLinksTable.url],
                sortOrder = row[ItineraryPointLinksTable.sortOrder],
                createdAt = row[ItineraryPointLinksTable.createdAt],
            )
        }
    }

    private fun loadPointComments(tripId: UUID, after: Instant?): List<ItineraryPointCommentWithAuthor> {
        val query = ItineraryPointCommentsTable
            .innerJoin(ItineraryPointsTable, { ItineraryPointCommentsTable.pointId }, { ItineraryPointsTable.id })
            .innerJoin(UsersTable, { ItineraryPointCommentsTable.authorUserId }, { UsersTable.id })
            .selectAll()
            .where {
                val base = (ItineraryPointsTable.tripId eq tripId) and
                        ItineraryPointsTable.deletedAt.isNull()
                if (after != null) {
                    base and (ItineraryPointCommentsTable.createdAt greater after)
                } else {
                    base
                }
            }
            .orderBy(ItineraryPointCommentsTable.createdAt, SortOrder.ASC)
        return query.map { row ->
            val comment = ItineraryPointComment(
                id = row[ItineraryPointCommentsTable.id],
                pointId = row[ItineraryPointCommentsTable.pointId],
                authorUserId = row[ItineraryPointCommentsTable.authorUserId],
                text = row[ItineraryPointCommentsTable.text],
                createdAt = row[ItineraryPointCommentsTable.createdAt],
            )
            ItineraryPointCommentWithAuthor(
                comment = comment,
                authorDisplayName = row[UsersTable.displayName],
            )
        }
    }

    // --- Mapping helpers ---

    private fun ResultRow.toTrip() = Trip(
        id = this[TripsTable.id],
        title = this[TripsTable.title],
        description = this[TripsTable.description],
        startDate = this[TripsTable.startDate],
        endDate = this[TripsTable.endDate],
        baseCurrency = this[TripsTable.baseCurrency],
        totalBudget = this[TripsTable.totalBudget],
        destination = this[TripsTable.destination],
        imageUrl = this[TripsTable.imageUrl],
        joinCode = this[TripsTable.joinCode],
        status = TripStatus.valueOf(this[TripsTable.status]),
        createdBy = this[TripsTable.createdBy],
        createdAt = this[TripsTable.createdAt],
        updatedAt = this[TripsTable.updatedAt],
        version = this[TripsTable.version],
        deletedAt = this[TripsTable.deletedAt]
    )

    private fun ResultRow.toTripParticipant() = TripParticipant(
        tripId = this[TripParticipantsTable.tripId],
        userId = this[TripParticipantsTable.userId],
        role = TripRole.valueOf(this[TripParticipantsTable.role]),
        joinedAt = this[TripParticipantsTable.joinedAt],
        updatedAt = this[TripParticipantsTable.updatedAt],
        version = this[TripParticipantsTable.version],
        deletedAt = this[TripParticipantsTable.deletedAt],
    )

    private fun loadItineraryParticipants(pointIds: List<UUID>): Map<UUID, List<UUID>> {
        if (pointIds.isEmpty()) return emptyMap()
        return ItineraryPointParticipantsTable
            .selectAll()
            .where { ItineraryPointParticipantsTable.pointId inList pointIds }
            .groupBy(
                { it[ItineraryPointParticipantsTable.pointId] },
                { it[ItineraryPointParticipantsTable.userId] }
            )
    }

    private fun ResultRow.toItineraryPoint(participantIds: List<UUID> = emptyList()) = ItineraryPoint(
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

    private fun ResultRow.toExpense() = Expense(
        id = this[ExpensesTable.id],
        tripId = this[ExpensesTable.tripId],
        payerUserId = this[ExpensesTable.payerUserId],
        title = this[ExpensesTable.title],
        description = this[ExpensesTable.description],
        amount = this[ExpensesTable.amount],
        currency = this[ExpensesTable.currency],
        category = this[ExpensesTable.category],
        expenseDate = this[ExpensesTable.expenseDate],
        splitType = SplitType.valueOf(this[ExpensesTable.splitType]),
        createdBy = this[ExpensesTable.createdBy],
        createdAt = this[ExpensesTable.createdAt],
        updatedAt = this[ExpensesTable.updatedAt],
        version = this[ExpensesTable.version],
        deletedAt = this[ExpensesTable.deletedAt]
    )

    private fun ResultRow.toExpenseSplit() = ExpenseSplit(
        id = this[ExpenseSplitsTable.id],
        expenseId = this[ExpenseSplitsTable.expenseId],
        participantUserId = this[ExpenseSplitsTable.participantUserId],
        shareType = SplitType.valueOf(this[ExpenseSplitsTable.shareType]),
        value = this[ExpenseSplitsTable.value],
        amountInExpenseCurrency = this[ExpenseSplitsTable.amountInExpenseCurrency]
    )

    private fun ResultRow.toAttachment() = Attachment(
        id = this[AttachmentsTable.id],
        tripId = this[AttachmentsTable.tripId],
        expenseId = this[AttachmentsTable.expenseId],
        pointId = this[AttachmentsTable.pointId],
        uploadedBy = this[AttachmentsTable.uploadedBy],
        fileName = this[AttachmentsTable.fileName],
        fileSize = this[AttachmentsTable.fileSize],
        mimeType = this[AttachmentsTable.mimeType],
        s3Key = this[AttachmentsTable.s3Key],
        createdAt = this[AttachmentsTable.createdAt],
        deletedAt = this[AttachmentsTable.deletedAt]
    )
}
