package com.travelplanner.application.usecase.attachment

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.Attachment
import com.travelplanner.domain.repository.AttachmentRepository
import com.travelplanner.domain.repository.ParticipantRepository
import java.time.Instant
import java.util.UUID

class CreateAttachmentUseCase(
    private val participantRepository: ParticipantRepository,
    private val attachmentRepository: AttachmentRepository
) {

    data class Input(
        val tripId: UUID,
        val userId: UUID,
        val expenseId: UUID? = null,
        val fileName: String,
        val fileSize: Long,
        val mimeType: String,
        val s3Key: String
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

        val attachment = Attachment(
            id = UUID.randomUUID(),
            tripId = input.tripId,
            expenseId = input.expenseId,
            uploadedBy = input.userId,
            fileName = input.fileName,
            fileSize = input.fileSize,
            mimeType = input.mimeType,
            s3Key = input.s3Key,
            createdAt = Instant.now()
        )

        return attachmentRepository.create(attachment)
    }
}
