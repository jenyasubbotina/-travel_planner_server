package com.travelplanner.application.usecase.attachment

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.repository.AttachmentRepository
import com.travelplanner.domain.repository.ParticipantRepository
import java.time.Instant
import java.util.UUID

class DeleteAttachmentUseCase(
    private val attachmentRepository: AttachmentRepository,
    private val participantRepository: ParticipantRepository
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

        // Soft delete the attachment record; S3 object is not deleted immediately
        attachmentRepository.softDelete(input.attachmentId, Instant.now())
    }
}
