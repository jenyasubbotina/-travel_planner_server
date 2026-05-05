package com.travelplanner.application.usecase.itinerary

import com.travelplanner.domain.event.HistoryPayload
import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.DomainEvent
import com.travelplanner.domain.model.ItineraryPointComment
import com.travelplanner.domain.repository.DomainEventRepository
import com.travelplanner.domain.repository.ItineraryRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TransactionRunner
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

class AddItineraryPointCommentUseCase(
    private val participantRepository: ParticipantRepository,
    private val itineraryRepository: ItineraryRepository,
    private val domainEventRepository: DomainEventRepository,
    private val transactionRunner: TransactionRunner,
) {

    data class Input(
        val pointId: UUID,
        val userId: UUID,
        val text: String,
        val id: UUID? = null,
    )

    suspend fun execute(input: Input): ItineraryPointComment = transactionRunner.runInTransaction {
        val text = input.text.trim()
        if (text.isBlank()) {
            throw DomainException.ValidationError("Comment text cannot be blank")
        }
        if (text.length > 2000) {
            throw DomainException.ValidationError("Comment text exceeds 2000 chars")
        }

        val point = itineraryRepository.findById(input.pointId)
            ?: throw DomainException.ItineraryPointNotFound(input.pointId)

        participantRepository.findByTripAndUser(point.tripId, input.userId)
            ?: throw DomainException.AccessDenied("User is not a participant of this trip")

        val commentId = input.id ?: UUID.randomUUID()

        val now = Instant.now()
        val comment = ItineraryPointComment(
            id = commentId,
            pointId = input.pointId,
            authorUserId = input.userId,
            text = text,
            createdAt = now,
        )

        val saved = itineraryRepository.addComment(comment)

        domainEventRepository.save(
            DomainEvent(
                id = UUID.randomUUID(),
                eventType = "ITINERARY_COMMENT_ADDED",
                aggregateType = "TRIP",
                aggregateId = point.tripId,
                payload = HistoryPayload.build(
                    actorUserId = input.userId,
                    entityType = HistoryPayload.EntityType.COMMENT,
                    entityId = saved.id,
                    actionType = HistoryPayload.ActionType.CREATE,
                    context = buildJsonObject {
                        put("pointId", input.pointId.toString())
                        put("pointTitle", point.title)
                        put("textPreview", text.take(80))
                    },
                ),
                createdAt = now,
            )
        )

        saved
    }
}
