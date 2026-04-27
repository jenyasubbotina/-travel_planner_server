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

class ReorderItineraryUseCase(
    private val participantRepository: ParticipantRepository,
    private val itineraryRepository: ItineraryRepository,
    private val domainEventRepository: DomainEventRepository,
    private val transactionRunner: TransactionRunner
) {

    data class Input(
        val tripId: UUID,
        val userId: UUID,
        val orders: List<PointOrder>
    )

    data class PointOrder(val pointId: UUID, val newSortOrder: Int)

    suspend fun execute(input: Input) = transactionRunner.runInTransaction {
        if (input.orders.isEmpty()) {
            throw DomainException.ValidationError("Order list cannot be empty")
        }

        val participant = participantRepository.findByTripAndUser(input.tripId, input.userId)
            ?: throw DomainException.AccessDenied("User is not a participant of this trip")

        if (!participant.role.canEdit()) {
            throw DomainException.InsufficientRole("EDITOR")
        }

        val updates = input.orders.map { order ->
            order.pointId to order.newSortOrder
        }

        itineraryRepository.updateSortOrders(updates)

        domainEventRepository.save(
            DomainEvent(
                id = UUID.randomUUID(),
                eventType = "ITINERARY_REORDERED",
                aggregateType = "TRIP",
                aggregateId = input.tripId,
                payload = HistoryPayload.build(
                    actorUserId = input.userId,
                    entityType = HistoryPayload.EntityType.TRIP,
                    entityId = input.tripId,
                    actionType = HistoryPayload.ActionType.REORDER_ITINERARY,
                    context = buildJsonObject { put("count", input.orders.size) },
                ),
                createdAt = Instant.now()
            )
        )
        Unit
    }
}
