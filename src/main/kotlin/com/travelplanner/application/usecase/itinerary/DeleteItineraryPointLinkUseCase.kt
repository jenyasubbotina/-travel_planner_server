package com.travelplanner.application.usecase.itinerary

import com.travelplanner.domain.event.HistoryPayload
import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.DomainEvent
import com.travelplanner.domain.repository.DomainEventRepository
import com.travelplanner.domain.repository.ItineraryRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TransactionRunner
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

class DeleteItineraryPointLinkUseCase(
    private val participantRepository: ParticipantRepository,
    private val itineraryRepository: ItineraryRepository,
    private val domainEventRepository: DomainEventRepository,
    private val transactionRunner: TransactionRunner,
) {

    data class Input(
        val pointId: UUID,
        val linkId: UUID,
        val userId: UUID,
    )

    suspend fun execute(input: Input): Unit = transactionRunner.runInTransaction {
        val point = itineraryRepository.findById(input.pointId)
            ?: throw DomainException.ItineraryPointNotFound(input.pointId)

        val participant = participantRepository.findByTripAndUser(point.tripId, input.userId)
            ?: throw DomainException.AccessDenied("User is not a participant of this trip")
        if (!participant.role.canEdit()) {
            throw DomainException.InsufficientRole("EDITOR")
        }

        val link = itineraryRepository.findLink(input.linkId, input.pointId)
            ?: throw DomainException.ValidationError("Link not found")

        val deleted = itineraryRepository.deleteLink(input.linkId, input.pointId)
        if (!deleted) {
            throw DomainException.ValidationError("Link not found")
        }

        val now = Instant.now()
        domainEventRepository.save(
            DomainEvent(
                id = UUID.randomUUID(),
                eventType = "ITINERARY_LINK_REMOVED",
                aggregateType = "TRIP",
                aggregateId = point.tripId,
                payload = HistoryPayload.build(
                    actorUserId = input.userId,
                    entityType = HistoryPayload.EntityType.LINK,
                    entityId = input.linkId,
                    actionType = HistoryPayload.ActionType.DELETE,
                    context = buildJsonObject {
                        put("pointId", input.pointId.toString())
                        put("pointTitle", point.title)
                        put("title", link.title)
                        put("url", link.url)
                    },
                ),
                createdAt = now,
            )
        )
    }
}
