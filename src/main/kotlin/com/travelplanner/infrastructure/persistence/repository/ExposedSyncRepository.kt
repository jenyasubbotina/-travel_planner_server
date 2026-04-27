package com.travelplanner.infrastructure.persistence.repository

import com.travelplanner.domain.model.*
import com.travelplanner.domain.repository.SyncRepository
import com.travelplanner.infrastructure.persistence.DatabaseFactory.dbQuery
import com.travelplanner.infrastructure.persistence.tables.*
import org.jetbrains.exposed.sql.*
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

class ExposedSyncRepository : SyncRepository {

    override suspend fun getTripSnapshot(tripId: UUID): TripSnapshot? = dbQuery {
        val trip = TripsTable.selectAll()
            .where { (TripsTable.id eq tripId) and TripsTable.deletedAt.isNull() }
            .singleOrNull()
            ?.toTrip()
            ?: return@dbQuery null

        val participants = TripParticipantsTable.selectAll()
            .where { TripParticipantsTable.tripId eq tripId }
            .map { it.toTripParticipant() }

        val itineraryRows = ItineraryPointsTable.selectAll()
            .where {
                (ItineraryPointsTable.tripId eq tripId) and
                        ItineraryPointsTable.deletedAt.isNull()
            }
            .orderBy(ItineraryPointsTable.sortOrder)
            .toList()
        val itineraryParticipantIds = loadItineraryParticipants(
            itineraryRows.map { it[ItineraryPointsTable.id] }
        )
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

        val now = Instant.now()
        TripSnapshot(
            trip = trip,
            participants = participants,
            itineraryPoints = itineraryPoints,
            expenses = expenses,
            expenseSplits = expenseSplits,
            attachments = attachments,
            cursor = SyncCursor(timestamp = now)
        )
    }

    override suspend fun getDelta(tripId: UUID, after: Instant): SyncDelta = dbQuery {
        val trips = TripsTable.selectAll()
            .where {
                (TripsTable.id eq tripId) and (TripsTable.updatedAt greater after)
            }
            .map { it.toTrip() }

        val participants = TripParticipantsTable.selectAll()
            .where {
                (TripParticipantsTable.tripId eq tripId) and
                        (TripParticipantsTable.joinedAt greater after)
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

        val now = Instant.now()
        SyncDelta(
            trips = trips,
            participants = participants,
            itineraryPoints = itineraryPoints,
            expenses = expenses,
            expenseSplits = expenseSplits,
            attachments = attachments,
            cursor = SyncCursor(timestamp = now)
        )
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
        joinedAt = this[TripParticipantsTable.joinedAt]
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
        uploadedBy = this[AttachmentsTable.uploadedBy],
        fileName = this[AttachmentsTable.fileName],
        fileSize = this[AttachmentsTable.fileSize],
        mimeType = this[AttachmentsTable.mimeType],
        s3Key = this[AttachmentsTable.s3Key],
        createdAt = this[AttachmentsTable.createdAt],
        deletedAt = this[AttachmentsTable.deletedAt]
    )
}
