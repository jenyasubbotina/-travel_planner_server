package com.travelplanner.application.usecase.itinerary

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.repository.ItineraryRepository
import com.travelplanner.domain.repository.ParticipantRepository
import java.util.UUID

class ReorderItineraryUseCase(
    private val participantRepository: ParticipantRepository,
    private val itineraryRepository: ItineraryRepository
) {

    data class Input(
        val tripId: UUID,
        val userId: UUID,
        val orders: List<PointOrder>
    )

    data class PointOrder(val pointId: UUID, val newSortOrder: Int)

    suspend fun execute(input: Input) {
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
    }
}
