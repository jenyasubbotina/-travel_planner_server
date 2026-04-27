package com.travelplanner.application.usecase.checklist

import com.travelplanner.domain.event.HistoryPayload
import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.DomainEvent
import com.travelplanner.domain.model.TripRole
import com.travelplanner.domain.repository.ChecklistRepository
import com.travelplanner.domain.repository.DomainEventRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TransactionRunner
import java.time.Instant
import java.util.UUID

class DeleteChecklistItemUseCase(
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

    suspend fun execute(input: Input): Unit = transactionRunner.runInTransaction {
        val item = checklistRepository.findById(input.itemId)
            ?: throw DomainException.ValidationError("Checklist item not found")
        if (item.tripId != input.tripId) {
            throw DomainException.ValidationError("Checklist item does not belong to this trip")
        }

        val participant = participantRepository.findByTripAndUser(input.tripId, input.userId)
            ?: throw DomainException.AccessDenied("User is not a participant of this trip")
        if (!participant.role.canEdit()) {
            throw DomainException.InsufficientRole("EDITOR")
        }
        val isOwner = item.ownerUserId == input.userId
        val isTripOwner = participant.role == TripRole.OWNER
        if (!isOwner && !isTripOwner) {
            throw DomainException.AccessDenied("Only item owner or trip owner can delete this item")
        }

        val deleted = checklistRepository.delete(input.itemId, input.tripId)
        if (!deleted) {
            throw DomainException.ValidationError("Checklist item not found")
        }

        val now = Instant.now()
        domainEventRepository.save(
            DomainEvent(
                id = UUID.randomUUID(),
                eventType = "CHECKLIST_ITEM_DELETED",
                aggregateType = "TRIP",
                aggregateId = input.tripId,
                payload = HistoryPayload.build(
                    actorUserId = input.userId,
                    entityType = HistoryPayload.EntityType.CHECKLIST_ITEM,
                    entityId = input.itemId,
                    actionType = HistoryPayload.ActionType.DELETE,
                    entity = HistoryPayload.checklistItemSnapshot(item),
                ),
                createdAt = now,
            )
        )
    }
}
