package com.travelplanner.application.usecase.attachment

import com.travelplanner.domain.event.HistoryPayload
import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.DomainEvent
import com.travelplanner.domain.repository.AttachmentRepository
import com.travelplanner.domain.repository.DomainEventRepository
import com.travelplanner.domain.repository.ParticipantRepository
import java.time.Instant
import java.util.UUID

class DeleteAttachmentUseCase(
    private val attachmentRepository: AttachmentRepository,
    private val participantRepository: ParticipantRepository,
    private val domainEventRepository: DomainEventRepository,
) {

    data class Input(val attachmentId: UUID, val userId: UUID)

    suspend fun execute(input: Input) {
        val attachment = attachmentRepository.findById(input.attachmentId)
            ?: throw DomainException.AttachmentNotFound(input.attachmentId)

        if (attachment.deletedAt != null) {
            throw DomainException.AttachmentNotFound(input.attachmentId)
        }

        // Check if user is the uploader or OWNER of the trip
        if (attachment.uploadedBy != input.userId) {
            val participant = participantRepository.findByTripAndUser(attachment.tripId, input.userId)
                ?: throw DomainException.AccessDenied("User is not a participant of this trip")

            if (!participant.role.canManageParticipants()) {
                throw DomainException.AccessDenied("Only the uploader or trip owner can delete attachments")
            }
        }

        val now = Instant.now()
        attachmentRepository.softDelete(input.attachmentId, now)

        domainEventRepository.save(
            DomainEvent(
                id = UUID.randomUUID(),
                eventType = "ATTACHMENT_DELETED",
                aggregateType = "TRIP",
                aggregateId = attachment.tripId,
                payload = HistoryPayload.build(
                    actorUserId = input.userId,
                    entityType = HistoryPayload.EntityType.ATTACHMENT,
                    entityId = attachment.id,
                    actionType = HistoryPayload.ActionType.DELETE,
                    entity = HistoryPayload.attachmentSnapshot(attachment),
                ),
                createdAt = now
            )
        )
    }
}
