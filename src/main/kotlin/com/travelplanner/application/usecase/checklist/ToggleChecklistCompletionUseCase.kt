package com.travelplanner.application.usecase.checklist

import com.travelplanner.domain.event.HistoryPayload
import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.ChecklistItem
import com.travelplanner.domain.model.DomainEvent
import com.travelplanner.domain.repository.ChecklistRepository
import com.travelplanner.domain.repository.DomainEventRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TransactionRunner
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

class ToggleChecklistCompletionUseCase(
    private val participantRepository: ParticipantRepository,
    private val checklistRepository: ChecklistRepository,
    private val domainEventRepository: DomainEventRepository,
    private val transactionRunner: TransactionRunner,
) {

    data class Input(
        val itemId: UUID,
        val tripId: UUID,
        val userId: UUID,
    )

    suspend fun execute(input: Input): ChecklistItem = transactionRunner.runInTransaction {
        val item = checklistRepository.findById(input.itemId)
            ?: throw DomainException.ValidationError("Checklist item not found")
        if (item.tripId != input.tripId) {
            throw DomainException.ValidationError("Checklist item does not belong to this trip")
        }

        participantRepository.findByTripAndUser(input.tripId, input.userId)
            ?: throw DomainException.AccessDenied("User is not a participant of this trip")

        if (!item.isGroup && item.ownerUserId != input.userId) {
            throw DomainException.AccessDenied("Cannot toggle a private checklist item belonging to another user")
        }

        val nowCompleted = checklistRepository.toggleCompletion(input.itemId, input.userId)

        val now = Instant.now()
        domainEventRepository.save(
            DomainEvent(
                id = UUID.randomUUID(),
                eventType = if (nowCompleted) "CHECKLIST_ITEM_COMPLETED" else "CHECKLIST_ITEM_UNCOMPLETED",
                aggregateType = "TRIP",
                aggregateId = input.tripId,
                payload = HistoryPayload.build(
                    actorUserId = input.userId,
                    entityType = HistoryPayload.EntityType.CHECKLIST_ITEM,
                    entityId = input.itemId,
                    actionType = if (nowCompleted) HistoryPayload.ActionType.COMPLETE else HistoryPayload.ActionType.UNCOMPLETE,
                    context = buildJsonObject { put("title", item.title) },
                ),
                createdAt = now,
            )
        )

        checklistRepository.findById(input.itemId)!!
    }
}
