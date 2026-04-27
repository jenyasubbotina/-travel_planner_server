package com.travelplanner.application.usecase.trip

import com.travelplanner.domain.event.HistoryPayload
import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.DomainEvent
import com.travelplanner.domain.model.Trip
import com.travelplanner.domain.model.TripStatus
import com.travelplanner.domain.repository.DomainEventRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TransactionRunner
import com.travelplanner.domain.repository.TripRepository
import com.travelplanner.domain.validation.TripValidator
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class UpdateTripUseCase(
    private val tripRepository: TripRepository,
    private val participantRepository: ParticipantRepository,
    private val domainEventRepository: DomainEventRepository,
    private val transactionRunner: TransactionRunner
) {

    data class Input(
        val tripId: UUID,
        val userId: UUID,
        val title: String? = null,
        val description: String? = null,
        val startDate: LocalDate? = null,
        val endDate: LocalDate? = null,
        val baseCurrency: String? = null,
        val totalBudget: BigDecimal? = null,
        val destination: String? = null,
        val imageUrl: String? = null,
        val status: TripStatus? = null,
        val expectedVersion: Long
    )

    suspend fun execute(input: Input): Trip = transactionRunner.runInTransaction {
        val trip = tripRepository.findById(input.tripId)
            ?: throw DomainException.TripNotFound(input.tripId)

        if (trip.deletedAt != null) {
            throw DomainException.TripNotFound(input.tripId)
        }

        val participant = participantRepository.findByTripAndUser(input.tripId, input.userId)
            ?: throw DomainException.AccessDenied("User is not a participant of this trip")

        if (!participant.role.canEdit()) {
            throw DomainException.InsufficientRole("EDITOR")
        }

        if (trip.version != input.expectedVersion) {
            throw DomainException.VersionConflict("Trip", input.tripId)
        }

        val newTitle = input.title ?: trip.title
        val newStartDate = input.startDate ?: trip.startDate
        val newEndDate = input.endDate ?: trip.endDate
        val newCurrency = input.baseCurrency ?: trip.baseCurrency

        if (input.title != null) TripValidator.validateTitle(newTitle)
        TripValidator.validateDates(newStartDate, newEndDate)
        if (input.baseCurrency != null) TripValidator.validateCurrency(newCurrency)

        val updated = trip.copy(
            title = newTitle.trim(),
            description = if (input.description != null) input.description.trim() else trip.description,
            startDate = newStartDate,
            endDate = newEndDate,
            baseCurrency = newCurrency.uppercase().trim(),
            totalBudget = input.totalBudget ?: trip.totalBudget,
            destination = if (input.destination != null) input.destination.trim() else trip.destination,
            imageUrl = if (input.imageUrl != null) input.imageUrl.trim() else trip.imageUrl,
            status = input.status ?: trip.status
        )

        val saved = tripRepository.update(updated)

        val diff = HistoryPayload.diff(
            HistoryPayload.tripSnapshot(trip),
            HistoryPayload.tripSnapshot(saved),
        )
        if (diff != null) {
            domainEventRepository.save(
                DomainEvent(
                    id = UUID.randomUUID(),
                    eventType = "TRIP_UPDATED",
                    aggregateType = "TRIP",
                    aggregateId = saved.id,
                    payload = HistoryPayload.build(
                        actorUserId = input.userId,
                        entityType = HistoryPayload.EntityType.TRIP,
                        entityId = saved.id,
                        actionType = HistoryPayload.ActionType.UPDATE,
                        old = diff.first,
                        new = diff.second,
                    ),
                    createdAt = Instant.now()
                )
            )
        }

        saved
    }
}
