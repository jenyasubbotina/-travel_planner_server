package com.example


import com.example.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

class TripRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    private fun ResultRow.toTripDto() = TripDto(
        id = this[TripsTable.id],
        title = this[TripsTable.title],
        destination = this[TripsTable.destination],
        startDate = this[TripsTable.startDate],
        endDate = this[TripsTable.endDate],
        totalBudget = this[TripsTable.totalBudget],
        description = this[TripsTable.description] ?: "",
        ownerUserId = this[TripsTable.ownerUserId],
        joinCode = this[TripsTable.joinCode],
        status = this[TripsTable.status],
        currency = this[TripsTable.currency],
        imageUrl = this[TripsTable.imageUrl],
        filesJson = this[TripsTable.filesJson],
    )

    private fun ResultRow.toExpenseDto() = ExpenseDto(
        id = this[ExpensesTable.id],
        tripId = this[ExpensesTable.tripId],
        title = this[ExpensesTable.title],
        amount = this[ExpensesTable.amount],
        category = this[ExpensesTable.category],
        payerUserId = this[ExpensesTable.payerUserId],
        date = this[ExpensesTable.date],
        splits = Json.decodeFromString(this[ExpensesTable.splitsJson]),
        creatorUserId = this[ExpensesTable.creatorUserId],
        pendingUpdate = this[ExpensesTable.pendingUpdateJson]?.let {
            Json.decodeFromString(it)
        },
        imageUrl = this[ExpensesTable.imageUrl]
    )

    private fun ResultRow.toChecklistItemDto() = ChecklistItemDto(
        id = this[ChecklistTable.id],
        tripId = this[ChecklistTable.tripId],
        title = this[ChecklistTable.title],
        isGroup = this[ChecklistTable.isGroup],
        ownerUserId = this[ChecklistTable.ownerUserId],
        completedBy = Json.decodeFromString(this[ChecklistTable.completedByJson])
    )

    suspend fun getAllTrips(): List<TripDto> = dbQuery {
        TripsTable.selectAll().map { it.toTripDto() }
    }

    suspend fun getTrip(id: Long): TripDto? = dbQuery {
        TripsTable.selectAll().where { TripsTable.id eq id }.singleOrNull()?.toTripDto()
    }

    suspend fun getUserTrips(userId: String): List<TripDto> = dbQuery {
        val joinedTripIds = TripParticipantsTable
            .selectAll()
            .where { (TripParticipantsTable.userId eq userId) and (TripParticipantsTable.isPending eq false) }
            .map { it[TripParticipantsTable.tripId] }

        TripsTable.selectAll().where {
            (TripsTable.ownerUserId eq userId) or (TripsTable.id inList joinedTripIds)
        }.map { it.toTripDto() }
    }

    suspend fun getParticipantUserIdsForTrip(tripId: Long): List<String> = dbQuery {
        val ownerId =
            TripsTable.selectAll().where { TripsTable.id eq tripId }.singleOrNull()?.get(TripsTable.ownerUserId)

        val participantIds = TripParticipantsTable.selectAll()
            .where { (TripParticipantsTable.tripId eq tripId) and (TripParticipantsTable.isPending eq false) }
            .map { it[TripParticipantsTable.userId] }

        listOfNotNull(ownerId).plus(participantIds).distinct()
    }

    suspend fun createTrip(req: CreateTripRequest): TripDto = dbQuery {
        val code = generateJoinCode()

        val insertId = TripsTable.insert {
            it[title] = req.title
            it[destination] = req.destination
            it[startDate] = req.startDate
            it[endDate] = req.endDate
            it[totalBudget] = req.totalBudget
            it[description] = req.description
            it[ownerUserId] = req.ownerUserId
            it[joinCode] = code
            it[currency] = req.currency
            it[imageUrl] = req.imageUrl
        }[TripsTable.id]

        TripParticipantsTable.insert {
            it[this.tripId] = insertId
            it[this.userId] = req.ownerUserId
            it[this.isPending] = false
            it[this.name] = req.ownerName
            it[this.email] = req.ownerEmail
        }

        TripDto(
            id = insertId,
            title = req.title,
            destination = req.destination,
            startDate = req.startDate,
            endDate = req.endDate,
            totalBudget = req.totalBudget,
            description = req.description ?: "",
            ownerUserId = req.ownerUserId,
            joinCode = code,
            status = "ACTIVE",
            currency = req.currency,
            imageUrl = req.imageUrl,
            filesJson = "[]",
        )
    }

    suspend fun updateTripStatus(tripId: Long, newStatus: String): TripDto? = dbQuery {
        TripsTable.update({ TripsTable.id eq tripId }) {
            it[status] = newStatus
        }

        TripsTable.selectAll().where { TripsTable.id eq tripId }
            .singleOrNull()
            ?.toTripDto()
    }

    suspend fun updateTripBudget(tripId: Long, budget: Double): TripDto? = dbQuery {
        val updatedRows = TripsTable.update({ TripsTable.id eq tripId }) {
            it[totalBudget] = budget
        }
        if (updatedRows > 0) {
            TripsTable.selectAll().where { TripsTable.id eq tripId }.singleOrNull()?.toTripDto()
        } else null
    }

    suspend fun updateTripFilesJson(tripId: Long, filesJson: String): TripDto? = dbQuery {
        val updatedRows = TripsTable.update({ TripsTable.id eq tripId }) {
            it[TripsTable.filesJson] = filesJson
        }
        if (updatedRows > 0) {
            TripsTable.selectAll().where { TripsTable.id eq tripId }.singleOrNull()?.toTripDto()
        } else null
    }

    suspend fun leaveOrDeleteTrip(tripId: Long, userId: String): String = dbQuery {
        val trip = TripsTable.selectAll().where { TripsTable.id eq tripId }.singleOrNull() ?: return@dbQuery "NOT_FOUND"

        if (trip[TripsTable.ownerUserId] == userId) {
            TripsTable.deleteWhere { TripsTable.id eq tripId }
            "DELETED"
        } else {
            TripParticipantsTable.deleteWhere {
                (TripParticipantsTable.tripId eq tripId) and (TripParticipantsTable.userId eq userId)
            }
            "LEFT"
        }
    }

    suspend fun isUserInTrip(tripId: Long, userId: String): Boolean = dbQuery {
        val trip = TripsTable.selectAll().where { TripsTable.id eq tripId }.singleOrNull() ?: return@dbQuery false
        if (trip[TripsTable.ownerUserId] == userId) return@dbQuery true

        TripParticipantsTable.selectAll().where {
            (TripParticipantsTable.tripId eq tripId) and
                    (TripParticipantsTable.userId eq userId) and
                    (TripParticipantsTable.isPending eq false)
        }.count() > 0
    }

    suspend fun isTripOwner(tripId: Long, userId: String): Boolean = dbQuery {
        TripsTable.selectAll().where { (TripsTable.id eq tripId) and (TripsTable.ownerUserId eq userId) }.count() > 0
    }

    suspend fun isTripArchived(tripId: Long): Boolean = dbQuery {
        val trip = TripsTable.selectAll().where { TripsTable.id eq tripId }.singleOrNull()
        trip?.get(TripsTable.status) == "ARCHIVED"
    }

    private fun generateJoinCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    suspend fun joinTrip(tripId: Long, user: UserDto): Boolean = dbQuery {
        val existing = TripParticipantsTable.selectAll().where {
            (TripParticipantsTable.tripId eq tripId) and (TripParticipantsTable.userId eq user.id)
        }.singleOrNull()

        if (existing != null && !existing[TripParticipantsTable.isPending]) {
            TripParticipantsTable.update({
                (TripParticipantsTable.tripId eq tripId) and (TripParticipantsTable.userId eq user.id)
            }) {
                it[name] = user.name
                it[email] = user.email
            }
            return@dbQuery false
        } else {
            TripParticipantsTable.deleteWhere {
                (TripParticipantsTable.tripId eq tripId) and (TripParticipantsTable.userId eq user.id)
            }
            TripParticipantsTable.insert {
                it[this.tripId] = tripId
                it[this.userId] = user.id
                it[this.isPending] = false
                it[this.name] = user.name
                it[this.email] = user.email
            }
            return@dbQuery true
        }
    }

    suspend fun requestJoinByCode(code: String, user: UserDto): TripDto? = dbQuery {
        val trip = TripsTable.selectAll().where { TripsTable.joinCode eq code }
            .singleOrNull()?.toTripDto() ?: return@dbQuery null

        TripParticipantsTable.deleteWhere {
            (TripParticipantsTable.tripId eq trip.id) and (TripParticipantsTable.userId eq user.id)
        }

        TripParticipantsTable.insert {
            it[this.tripId] = trip.id
            it[this.userId] = user.id
            it[this.isPending] = true
            it[this.name] = user.name
            it[this.email] = user.email
        }
        trip
    }

    suspend fun joinTripByCode(code: String, user: UserDto): TripDto? = dbQuery {
        val trip = TripsTable.selectAll().where { TripsTable.joinCode eq code }.singleOrNull()?.toTripDto()
            ?: return@dbQuery null
        joinTrip(trip.id, user)
        trip
    }

    suspend fun resolveJoinRequest(tripId: Long, userId: String, approve: Boolean) = dbQuery {
        if (approve) {
            TripParticipantsTable.update({
                (TripParticipantsTable.tripId eq tripId) and (TripParticipantsTable.userId eq userId)
            }) {
                it[isPending] = false
            }
        } else {
            TripParticipantsTable.deleteWhere {
                (TripParticipantsTable.tripId eq tripId) and (TripParticipantsTable.userId eq userId)
            }
        }
    }

    suspend fun regenerateJoinCode(tripId: Long): String? = dbQuery {
        val newCode = generateJoinCode()
        val updatedRows = TripsTable.update({ TripsTable.id eq tripId }) {
            it[joinCode] = newCode
        }
        if (updatedRows > 0) newCode else null
    }

    suspend fun getPendingRequests(tripId: Long): List<UserDto> = dbQuery {
        TripParticipantsTable
            .selectAll()
            .where { (TripParticipantsTable.tripId eq tripId) and (TripParticipantsTable.isPending eq true) }
            .map {
                UserDto(
                    id = it[TripParticipantsTable.userId],
                    name = it[TripParticipantsTable.name],
                    email = it[TripParticipantsTable.email]
                )
            }
    }

    suspend fun getParticipants(tripId: Long): List<UserDto> = dbQuery {
        TripParticipantsTable
            .selectAll()
            .where { (TripParticipantsTable.tripId eq tripId) and (TripParticipantsTable.isPending eq false) }
            .map {
                UserDto(
                    id = it[TripParticipantsTable.userId],
                    name = it[TripParticipantsTable.name],
                    email = it[TripParticipantsTable.email]
                )
            }
    }

    suspend fun getAllExpenses(tripId: Long): List<ExpenseDto> = dbQuery {
        ExpensesTable.selectAll().where { ExpensesTable.tripId eq tripId }.map { it.toExpenseDto() }
    }

    suspend fun getExpenseById(tripId: Long, expenseId: String): ExpenseDto? = dbQuery {
        ExpensesTable.selectAll()
            .where { (ExpensesTable.id eq expenseId) and (ExpensesTable.tripId eq tripId) }
            .singleOrNull()?.toExpenseDto()
    }

    suspend fun addExpense(tripId: Long, userId: String, request: CreateExpenseRequest): ExpenseDto = dbQuery {
        val newId = UUID.randomUUID().toString()

        ExpensesTable.insert {
            it[id] = newId
            it[this.tripId] = tripId
            it[title] = request.title
            it[amount] = request.amount
            it[category] = request.category
            it[payerUserId] = request.payerUserId
            it[date] = request.date
            it[splitsJson] = Json.encodeToString(request.splits)
            it[creatorUserId] = userId
            it[pendingUpdateJson] = null
            it[imageUrl] = request.imageUrl
        }

        ExpenseDto(
            id = newId,
            tripId = tripId,
            title = request.title,
            amount = request.amount,
            category = request.category,
            payerUserId = request.payerUserId,
            date = request.date,
            splits = request.splits,
            creatorUserId = userId,
            pendingUpdate = null
        )
    }

    suspend fun updateExpense(tripId: Long, expenseId: String, request: CreateExpenseRequest): ExpenseDto? = dbQuery {
        val updatedRows = ExpensesTable.update({
            (ExpensesTable.id eq expenseId) and (ExpensesTable.tripId eq tripId)
        }) {
            it[title] = request.title
            it[amount] = request.amount
            it[category] = request.category
            it[payerUserId] = request.payerUserId
            it[splitsJson] = Json.encodeToString(request.splits)
            it[imageUrl] = request.imageUrl
        }

        if (updatedRows > 0) {
            ExpensesTable.selectAll()
                .where { (ExpensesTable.id eq expenseId) and (ExpensesTable.tripId eq tripId) }
                .singleOrNull()?.toExpenseDto()
        } else null
    }

    suspend fun deleteExpense(tripId: Long, expenseId: String): Boolean = dbQuery {
        val deletedRows = ExpensesTable.deleteWhere {
            (ExpensesTable.id eq expenseId) and (ExpensesTable.tripId eq tripId)
        }
        deletedRows > 0
    }

    suspend fun savePendingUpdate(
        tripId: Long,
        expenseId: String,
        pendingUpdate: PendingExpenseUpdateDto
    ): ExpenseDto? = dbQuery {
        val updatedRows = ExpensesTable.update({
            (ExpensesTable.id eq expenseId) and (ExpensesTable.tripId eq tripId)
        }) {
            it[pendingUpdateJson] = Json.encodeToString(pendingUpdate)
        }

        if (updatedRows > 0) {
            ExpensesTable.selectAll()
                .where { (ExpensesTable.id eq expenseId) and (ExpensesTable.tripId eq tripId) }
                .singleOrNull()?.toExpenseDto()
        } else null
    }

    suspend fun applyPendingUpdate(tripId: Long, expenseId: String): ExpenseDto? = dbQuery {
        val current = ExpensesTable.selectAll()
            .where { (ExpensesTable.id eq expenseId) and (ExpensesTable.tripId eq tripId) }
            .singleOrNull()?.toExpenseDto() ?: return@dbQuery null

        val pending = current.pendingUpdate ?: return@dbQuery null
        val req = pending.proposedExpense

        val updatedRows = ExpensesTable.update({
            (ExpensesTable.id eq expenseId) and (ExpensesTable.tripId eq tripId)
        }) {
            it[title] = req.title
            it[amount] = req.amount
            it[category] = req.category
            it[payerUserId] = req.payerUserId
            it[splitsJson] = Json.encodeToString(req.splits)

            it[pendingUpdateJson] = null as String?
        }

        if (updatedRows > 0) {
            ExpensesTable.selectAll()
                .where { (ExpensesTable.id eq expenseId) and (ExpensesTable.tripId eq tripId) }
                .singleOrNull()?.toExpenseDto()
        } else null
    }

    suspend fun clearPendingUpdate(tripId: Long, expenseId: String): ExpenseDto? = dbQuery {
        val updatedRows = ExpensesTable.update({
            (ExpensesTable.id eq expenseId) and (ExpensesTable.tripId eq tripId)
        }) {
            it[pendingUpdateJson] = null as String?
        }

        if (updatedRows > 0) {
            ExpensesTable.selectAll()
                .where { (ExpensesTable.id eq expenseId) and (ExpensesTable.tripId eq tripId) }
                .singleOrNull()?.toExpenseDto()
        } else null
    }


    suspend fun getAllEvents(tripId: Long): List<EventDto> = dbQuery {
        EventsTable.selectAll().where { EventsTable.tripId eq tripId }
            .map {
                Json.decodeFromString<EventDto>(it[EventsTable.eventDataJson])
            }
    }

    suspend fun addEvent(tripId: Long, request: EventDto): EventDto = dbQuery {
        val newId = request.id.ifBlank { UUID.randomUUID().toString() }
        val newEvent = request.copy(id = newId, tripId = tripId)

        EventsTable.insert {
            it[id] = newId
            it[this.tripId] = tripId
            it[title] = newEvent.title
            it[eventDataJson] = Json.encodeToString(newEvent)
        }
        newEvent
    }

    suspend fun updateEvent(tripId: Long, updatedEvent: EventDto): EventDto? = dbQuery {
        val safeUpdate = updatedEvent.copy(tripId = tripId)
        val updatedRows = EventsTable.update({
            (EventsTable.id eq updatedEvent.id) and (EventsTable.tripId eq tripId)
        }) {
            it[title] = safeUpdate.title
            it[eventDataJson] = Json.encodeToString(safeUpdate)
        }
        if (updatedRows > 0) safeUpdate else null
    }

    suspend fun deleteEvent(tripId: Long, eventId: String): Boolean = dbQuery {
        val deletedRows = EventsTable.deleteWhere {
            (EventsTable.id eq eventId) and (EventsTable.tripId eq tripId)
        }
        deletedRows > 0
    }

    suspend fun logAction(
        tripId: Long, userId: String, actionType: String,
        entityType: String, entityId: String, details: String
    ): HistoryLogDto = dbQuery {
        val newId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        HistoryLogsTable.insert {
            it[id] = newId
            it[this.tripId] = tripId
            it[this.userId] = userId
            it[this.actionType] = actionType
            it[this.entityType] = entityType
            it[this.entityId] = entityId
            it[this.details] = details
            it[this.timestamp] = now
        }

        HistoryLogDto(newId, tripId, userId, actionType, entityType, entityId, details, now)
    }

    suspend fun getHistoryLogs(tripId: Long): List<HistoryLogDto> = dbQuery {
        HistoryLogsTable.selectAll().where { HistoryLogsTable.tripId eq tripId }
            .orderBy(HistoryLogsTable.timestamp to SortOrder.DESC)
            .map {
                HistoryLogDto(
                    id = it[HistoryLogsTable.id],
                    tripId = it[HistoryLogsTable.tripId],
                    userId = it[HistoryLogsTable.userId],
                    actionType = it[HistoryLogsTable.actionType],
                    entityType = it[HistoryLogsTable.entityType],
                    entityId = it[HistoryLogsTable.entityId],
                    details = it[HistoryLogsTable.details],
                    timestamp = it[HistoryLogsTable.timestamp]
                )
            }
    }

    private fun getChecklistItemByIdInternal(id: String): ChecklistItemDto? {
        return ChecklistTable.selectAll()
            .where { ChecklistTable.id eq id }
            .singleOrNull()?.toChecklistItemDto()
    }

    suspend fun getChecklistItemById(id: String): ChecklistItemDto? = dbQuery {
        getChecklistItemByIdInternal(id)
    }

    suspend fun getChecklist(tripId: Long, userId: String): List<ChecklistItemDto> = dbQuery {
        ChecklistTable.selectAll().where {
            (ChecklistTable.tripId eq tripId) and
                    ((ChecklistTable.isGroup eq true) or (ChecklistTable.ownerUserId eq userId))
        }.map { it.toChecklistItemDto() }
    }

    suspend fun addChecklistItem(tripId: Long, userId: String, req: CreateChecklistItemRequest): ChecklistItemDto =
        dbQuery {
            val newId = UUID.randomUUID().toString()

            ChecklistTable.insert {
                it[id] = newId
                it[this.tripId] = tripId
                it[title] = req.title
                it[isGroup] = req.isGroup
                it[ownerUserId] = userId
                it[completedByJson] = "[]"
            }

            ChecklistItemDto(newId, tripId, req.title, req.isGroup, userId, emptyList())
        }

    suspend fun toggleChecklistCompletion(tripId: Long, itemId: String, userId: String): ChecklistItemDto? = dbQuery {
        val item = getChecklistItemByIdInternal(itemId) ?: return@dbQuery null

        if (!item.isGroup && item.ownerUserId != userId) return@dbQuery null

        val currentCompleted = item.completedBy.toMutableSet()
        if (currentCompleted.contains(userId)) {
            currentCompleted.remove(userId)
        } else {
            currentCompleted.add(userId)
        }

        ChecklistTable.update({ ChecklistTable.id eq itemId }) {
            it[completedByJson] = Json.encodeToString(currentCompleted.toList())
        }

        getChecklistItemByIdInternal(itemId)
    }

    suspend fun deleteChecklistItem(tripId: Long, itemId: String, userId: String): Boolean = dbQuery {
        val item = getChecklistItemByIdInternal(itemId) ?: return@dbQuery false

        if (!item.isGroup && item.ownerUserId != userId) return@dbQuery false

        val rows = ChecklistTable.deleteWhere { (ChecklistTable.id eq itemId) and (ChecklistTable.tripId eq tripId) }
        rows > 0
    }
}