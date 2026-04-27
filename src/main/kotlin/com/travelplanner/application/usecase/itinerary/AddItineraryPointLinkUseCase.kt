package com.travelplanner.application.usecase.itinerary

import com.travelplanner.domain.event.HistoryPayload
import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.DomainEvent
import com.travelplanner.domain.model.ItineraryPointLink
import com.travelplanner.domain.repository.DomainEventRepository
import com.travelplanner.domain.repository.ItineraryRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TransactionRunner
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

class AddItineraryPointLinkUseCase(
    private val participantRepository: ParticipantRepository,
    private val itineraryRepository: ItineraryRepository,
    private val domainEventRepository: DomainEventRepository,
    private val transactionRunner: TransactionRunner,
) {

    data class Input(
        val pointId: UUID,
        val userId: UUID,
        val title: String,
        val url: String,
    )

    suspend fun execute(input: Input): ItineraryPointLink = transactionRunner.runInTransaction {
        if (input.title.isBlank()) {
            throw DomainException.ValidationError("Link title cannot be blank")
        }
        if (input.url.isBlank()) {
            throw DomainException.ValidationError("Link url cannot be blank")
        }

        val point = itineraryRepository.findById(input.pointId)
            ?: throw DomainException.ItineraryPointNotFound(input.pointId)

        val participant = participantRepository.findByTripAndUser(point.tripId, input.userId)
            ?: throw DomainException.AccessDenied("User is not a participant of this trip")
        if (!participant.role.canEdit()) {
            throw DomainException.InsufficientRole("EDITOR")
        }

        val now = Instant.now()
        val link = ItineraryPointLink(
            id = UUID.randomUUID(),
            pointId = input.pointId,
            title = input.title.trim(),
            url = input.url.trim(),
            sortOrder = itineraryRepository.nextLinkSortOrder(input.pointId),
            createdAt = now,
        )

        val saved = itineraryRepository.addLink(link)

        domainEventRepository.save(
            DomainEvent(
                id = UUID.randomUUID(),
                eventType = "ITINERARY_LINK_ADDED",
                aggregateType = "TRIP",
                aggregateId = point.tripId,
                payload = HistoryPayload.build(
                    actorUserId = input.userId,
                    entityType = HistoryPayload.EntityType.LINK,
                    entityId = saved.id,
                    actionType = HistoryPayload.ActionType.CREATE,
                    context = buildJsonObject {
                        put("pointId", input.pointId.toString())
                        put("pointTitle", point.title)
                        put("title", saved.title)
                        put("url", saved.url)
                    },
                ),
                createdAt = now,
            )
        )

        saved
    }
}
