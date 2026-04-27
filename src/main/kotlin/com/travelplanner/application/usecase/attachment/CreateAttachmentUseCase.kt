package com.travelplanner.application.usecase.attachment

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.Attachment
import com.travelplanner.domain.model.DomainEvent
import com.travelplanner.domain.repository.AttachmentRepository
import com.travelplanner.domain.repository.DomainEventRepository
import com.travelplanner.domain.repository.ExpenseRepository
import com.travelplanner.domain.repository.ParticipantRepository
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

class CreateAttachmentUseCase(
    private val participantRepository: ParticipantRepository,
    private val attachmentRepository: AttachmentRepository,
    private val expenseRepository: ExpenseRepository,
    private val domainEventRepository: DomainEventRepository,
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

        if (input.expenseId != null) {
            val expense = expenseRepository.findById(input.expenseId)
                ?: throw DomainException.ExpenseNotFound(input.expenseId)

            if (expense.deletedAt != null || expense.tripId != input.tripId) {
                throw DomainException.ExpenseNotFound(input.expenseId)
            }
        }

        val now = Instant.now()
        val attachment = Attachment(
            id = UUID.randomUUID(),
            tripId = input.tripId,
            expenseId = input.expenseId,
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
                payload = buildJsonObject {
                    put("actorUserId", input.userId.toString())
                    put("attachmentId", created.id.toString())
                    put("fileName", created.fileName)
                    if (input.expenseId != null) put("expenseId", input.expenseId.toString())
                }.toString(),
                createdAt = now
            )
        )

        return created
    }
}
