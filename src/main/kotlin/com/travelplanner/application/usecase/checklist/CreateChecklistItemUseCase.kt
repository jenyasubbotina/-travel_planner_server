package com.travelplanner.application.usecase.checklist

import com.travelplanner.domain.event.HistoryPayload
import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.ChecklistItem
import com.travelplanner.domain.model.DomainEvent
import com.travelplanner.domain.repository.ChecklistRepository
import com.travelplanner.domain.repository.DomainEventRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TransactionRunner
import java.time.Instant
import java.util.UUID

class CreateChecklistItemUseCase(
    private val participantRepository: ParticipantRepository,
    private val checklistRepository: ChecklistRepository,
    private val domainEventRepository: DomainEventRepository,
    private val transactionRunner: TransactionRunner,
) {

    data class Input(
        val tripId: UUID,
        val userId: UUID,
        val title: String,
        val isGroup: Boolean,
    )

    suspend fun execute(input: Input): ChecklistItem = transactionRunner.runInTransaction {
        val title = input.title.trim()
        if (title.isBlank()) {
            throw DomainException.ValidationError("Checklist item title cannot be blank")
        }

        val participant = participantRepository.findByTripAndUser(input.tripId, input.userId)
            ?: throw DomainException.AccessDenied("User is not a participant of this trip")
        if (!participant.role.canEdit()) {
            throw DomainException.InsufficientRole("EDITOR")
        }

        val now = Instant.now()
        val item = ChecklistItem(
            id = UUID.randomUUID(),
            tripId = input.tripId,
            title = title,
            isGroup = input.isGroup,
            ownerUserId = input.userId,
            createdAt = now,
            updatedAt = now,
        )
        val saved = checklistRepository.create(item)

        domainEventRepository.save(
            DomainEvent(
                id = UUID.randomUUID(),
                eventType = "CHECKLIST_ITEM_CREATED",
                aggregateType = "TRIP",
                aggregateId = input.tripId,
                payload = HistoryPayload.build(
                    actorUserId = input.userId,
                    entityType = HistoryPayload.EntityType.CHECKLIST_ITEM,
                    entityId = saved.id,
                    actionType = HistoryPayload.ActionType.CREATE,
                    entity = HistoryPayload.checklistItemSnapshot(saved),
                ),
                createdAt = now,
            )
        )

        saved
    }
}
