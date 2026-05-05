package com.travelplanner.application.usecase.attachment

import com.travelplanner.domain.event.HistoryPayload
import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.Attachment
import com.travelplanner.domain.model.DomainEvent
import com.travelplanner.domain.repository.AttachmentRepository
import com.travelplanner.domain.repository.DomainEventRepository
import com.travelplanner.domain.repository.ExpenseRepository
import com.travelplanner.domain.repository.ItineraryRepository
import com.travelplanner.domain.repository.ParticipantRepository
import java.time.Instant
import java.util.UUID

class CreateAttachmentUseCase(
    private val participantRepository: ParticipantRepository,
    private val attachmentRepository: AttachmentRepository,
    private val expenseRepository: ExpenseRepository,
    private val itineraryRepository: ItineraryRepository,
    private val domainEventRepository: DomainEventRepository,
) {

    data class Input(
        val tripId: UUID,
        val userId: UUID,
        val expenseId: UUID? = null,
        val pointId: UUID? = null,
        val fileName: String,
        val fileSize: Long,
        val mimeType: String,
        val s3Key: String,
        val id: UUID? = null,
    )

    suspend fun execute(input: Input): Attachment {
        if (!participantRepository.isParticipant(input.tripId, input.userId)) {
            throw DomainException.AccessDenied("User is not a participant of this trip")
        }

        if (input.fileName.isBlank()) {
            throw DomainException.ValidationError("File name is required")
        }

        if (input.s3Key.isBlank()) {
            throw DomainException.ValidationError("S3 key is required")
        }

        if (input.expenseId != null && input.pointId != null) {
            throw DomainException.ValidationError("Attachment cannot reference both expense and itinerary point")
        }

        if (input.expenseId != null) {
            val expense = expenseRepository.findById(input.expenseId)
                ?: throw DomainException.ExpenseNotFound(input.expenseId)

            if (expense.deletedAt != null || expense.tripId != input.tripId) {
                throw DomainException.ExpenseNotFound(input.expenseId)
            }
        }

        if (input.pointId != null) {
            val point = itineraryRepository.findById(input.pointId)
                ?: throw DomainException.ItineraryPointNotFound(input.pointId)
            if (point.tripId != input.tripId) {
                throw DomainException.ItineraryPointNotFound(input.pointId)
            }
        }

        val attachmentId = input.id ?: UUID.randomUUID()
        if (input.id != null && attachmentRepository.findById(attachmentId) != null) {
            throw DomainException.DuplicateId("Attachment", attachmentId)
        }

        val now = Instant.now()
        val attachment = Attachment(
            id = attachmentId,
            tripId = input.tripId,
            expenseId = input.expenseId,
            pointId = input.pointId,
            uploadedBy = input.userId,
            fileName = input.fileName,
            fileSize = input.fileSize,
            mimeType = input.mimeType,
            s3Key = input.s3Key,
            createdAt = now
        )

        val created = attachmentRepository.create(attachment)

        domainEventRepository.save(
            DomainEvent(
                id = UUID.randomUUID(),
                eventType = "ATTACHMENT_CREATED",
                aggregateType = "TRIP",
                aggregateId = input.tripId,
                payload = HistoryPayload.build(
                    actorUserId = input.userId,
                    entityType = HistoryPayload.EntityType.ATTACHMENT,
                    entityId = created.id,
                    actionType = HistoryPayload.ActionType.CREATE,
                    entity = HistoryPayload.attachmentSnapshot(created),
                ),
                createdAt = now
            )
        )

        return created
    }
}
