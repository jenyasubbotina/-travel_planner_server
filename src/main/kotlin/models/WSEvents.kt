package com.example.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class TripEvent {
    abstract val tripId: Long

    @Serializable
    @SerialName("EXPENSE_ADDED")
    data class ExpenseAdded(override val tripId: Long, val expense: ExpenseDto) : TripEvent()

    @Serializable
    @SerialName("EXPENSE_DELETED")
    data class ExpenseDeleted(override val tripId: Long, val id: String) : TripEvent()

    @Serializable
    @SerialName("EXPENSE_UPDATED")
    data class ExpenseUpdated(override val tripId: Long, val expense: ExpenseDto) : TripEvent()

    @Serializable
    @SerialName("PARTICIPANT_JOINED")
    data class ParticipantJoined(override val tripId: Long, val user: UserDto) : TripEvent()

    @Serializable
    @SerialName("EVENT_ADDED")
    data class EventAdded(override val tripId: Long, val event: EventDto) : TripEvent()

    @Serializable
    @SerialName("EVENT_UPDATED")
    data class EventUpdated(override val tripId: Long, val event: EventDto) : TripEvent()

    @Serializable
    @SerialName("EVENT_DELETED")
    data class EventDeleted(override val tripId: Long, val id: String) : TripEvent()

    @Serializable
    @SerialName("JOIN_REQUEST_RECEIVED")
    data class JoinRequestReceived(override val tripId: Long, val user: UserDto) : TripEvent()

    @Serializable
    @SerialName("JOIN_REQUEST_RESOLVED")
    data class JoinRequestResolved(override val tripId: Long, val userId: String, val approved: Boolean) : TripEvent()

    @Serializable
    @SerialName("CODE_REGENERATED")
    data class CodeRegenerated(override val tripId: Long, val newCode: String) : TripEvent()

    @Serializable
    @SerialName("TRIP_DELETED")
    data class TripDeleted(override val tripId: Long) : TripEvent()

    @Serializable
    @SerialName("PARTICIPANT_LEFT")
    data class ParticipantLeft(override val tripId: Long, val userId: String) : TripEvent()

    @Serializable
    @SerialName("TRIP_UPDATED")
    data class TripUpdated(override val tripId: Long, val trip: TripDto) : TripEvent()

    @Serializable
    @SerialName("HISTORY_ADDED")
    data class HistoryAdded(
        override val tripId: Long,
        val log: HistoryLogDto
    ) : TripEvent()

    @Serializable
    @SerialName("CHECKLIST_UPDATED")
    data class ChecklistUpdated(
        override val tripId: Long,
        val item: ChecklistItemDto,
    ) : TripEvent()

    @Serializable
    @SerialName("CHECKLIST_DELETED")
    data class ChecklistDeleted(
        override val tripId: Long,
        val id: String,
    ) : TripEvent()

    @Serializable
    @SerialName("TRIP_FILES_UPDATED")
    data class TripFilesUpdated(
        override val tripId: Long,
        val filesJson: String,
    ) : TripEvent()
}