package com.example.history

import com.example.ConnectionManager
import com.example.TripRepository
import com.example.models.*
import kotlinx.serialization.json.Json

class ActionLogger(
    private val tripRepository: TripRepository,
    private val connectionManager: ConnectionManager
) {
    private suspend fun logAndBroadcast(
        tripId: Long, userId: String, actionType: String,
        entityType: String, entityId: String, details: String
    ) {
        val logDto = tripRepository.logAction(tripId, userId, actionType, entityType, entityId, details)
        connectionManager.broadcastToTrip(
            tripId, TripEvent.HistoryAdded(tripId, logDto), tripRepository
        )
    }

    suspend fun logTripCreated(tripId: Long, userId: String, trip: TripDto) =
        logAndBroadcast(tripId, userId, "CREATE", "TRIP", trip.id.toString(), Json.encodeToString(trip))

    suspend fun logExpenseAdded(tripId: Long, userId: String, expense: ExpenseDto) =
        logAndBroadcast(tripId, userId, "CREATE", "EXPENSE", expense.id, Json.encodeToString(expense))

    suspend fun logEventAdded(tripId: Long, userId: String, event: EventDto) =
        logAndBroadcast(tripId, userId, "CREATE", "EVENT", event.id, Json.encodeToString(event))

    suspend fun logExpenseDeleted(tripId: Long, userId: String, expense: ExpenseDto) {
        val type = if (expense.category == "PAYMENT") "PAYMENT" else "EXPENSE"
        logAndBroadcast(tripId, userId, "DELETE", type, expense.id, Json.encodeToString(expense))
    }

    suspend fun logEventDeleted(tripId: Long, userId: String, event: EventDto) =
        logAndBroadcast(tripId, userId, "DELETE", "EVENT", event.id, Json.encodeToString(event))

    suspend fun logParticipantJoined(tripId: Long, userId: String, user: UserDto) =
        logAndBroadcast(tripId, userId, "CREATE", "PARTICIPANT", user.id, Json.encodeToString(user))

    suspend fun logParticipantLeft(tripId: Long, userId: String, leftUser: UserDto) =
        logAndBroadcast(
            tripId,
            userId,
            "DELETE",
            "PARTICIPANT",
            leftUser.id,
            Json.encodeToString(leftUser)
        )

    suspend fun logTripUpdated(tripId: Long, userId: String, oldTrip: TripDto, newTrip: TripDto, reason: String) {
        val details = Json.encodeToString(mapOf("reason" to reason, "old" to oldTrip, "new" to newTrip))
        logAndBroadcast(tripId, userId, "UPDATE", "TRIP", newTrip.id.toString(), details)
    }

    suspend fun logExpenseUpdated(tripId: Long, userId: String, oldExpense: ExpenseDto, newExpense: ExpenseDto) {
        val type = if (newExpense.category == "PAYMENT") "PAYMENT" else "EXPENSE"
        val details = Json.encodeToString(mapOf("old" to oldExpense, "new" to newExpense))
        logAndBroadcast(tripId, userId, "UPDATE", type, newExpense.id, details)
    }

    suspend fun logEventUpdated(tripId: Long, userId: String, oldEvent: EventDto, newEvent: EventDto) {
        val details = Json.encodeToString(mapOf("old" to oldEvent, "new" to newEvent))
        logAndBroadcast(tripId, userId, "UPDATE", "EVENT", newEvent.id, details)
    }

    suspend fun logChecklistAdded(tripId: Long, userId: String, item: ChecklistItemDto) =
        logAndBroadcast(tripId, userId, "CREATE", "CHECKLIST", item.id, Json.encodeToString(item))

    suspend fun logChecklistUpdated(
        tripId: Long,
        userId: String,
        oldItem: ChecklistItemDto,
        newItem: ChecklistItemDto
    ) {
        val details = Json.encodeToString(mapOf("old" to oldItem, "new" to newItem))
        logAndBroadcast(tripId, userId, "UPDATE", "CHECKLIST", newItem.id, details)
    }

    suspend fun logChecklistDeleted(tripId: Long, userId: String, item: ChecklistItemDto) =
        logAndBroadcast(
            tripId,
            userId,
            "DELETE",
            "CHECKLIST",
            item.id,
            Json.encodeToString(item)
        )

    suspend fun logTripFilesUpdated(tripId: Long, userId: String, oldFilesJson: String, newFilesJson: String) {
        val details = Json.encodeToString(mapOf("old" to oldFilesJson, "new" to newFilesJson))
        logAndBroadcast(tripId, userId, "UPDATE", "TRIP_FILES", tripId.toString(), details)
    }

    suspend fun logCodeRegenerated(tripId: Long, userId: String, newCode: String) {
        val details = Json.encodeToString(mapOf("newCode" to newCode))
        logAndBroadcast(tripId, userId, "UPDATE", "JOIN_CODE", tripId.toString(), details)
    }

    suspend fun logTripDeleted(tripId: Long, userId: String, trip: TripDto) =
        logAndBroadcast(tripId, userId, "DELETE", "TRIP", trip.id.toString(), Json.encodeToString(trip))
}