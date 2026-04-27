package com.travelplanner.application.usecase.trip

import com.travelplanner.domain.model.DomainEvent
import com.travelplanner.domain.model.Trip
import com.travelplanner.domain.model.TripParticipant
import com.travelplanner.domain.model.TripRole
import com.travelplanner.domain.model.TripStatus
import com.travelplanner.domain.repository.DomainEventRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TransactionRunner
import com.travelplanner.domain.repository.TripRepository
import com.travelplanner.domain.validation.TripValidator
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class CreateTripUseCase(
    private val tripRepository: TripRepository,
    private val participantRepository: ParticipantRepository,
    private val domainEventRepository: DomainEventRepository,
    private val transactionRunner: TransactionRunner
) {

    data class Input(
        val title: String,
        val description: String? = null,
        val startDate: LocalDate? = null,
        val endDate: LocalDate? = null,
        val baseCurrency: String = "USD",
        val totalBudget: BigDecimal = BigDecimal.ZERO,
        val destination: String = "",
        val imageUrl: String? = null,
        val userId: UUID
    )

    suspend fun execute(input: Input): Trip = transactionRunner.runInTransaction {
        TripValidator.validateTitle(input.title)
        TripValidator.validateDates(input.startDate, input.endDate)
        TripValidator.validateCurrency(input.baseCurrency)

        val now = Instant.now()
        val trip = Trip(
            id = UUID.randomUUID(),
            title = input.title.trim(),
            description = input.description?.trim(),
            startDate = input.startDate,
            endDate = input.endDate,
            baseCurrency = input.baseCurrency.uppercase().trim(),
            totalBudget = input.totalBudget,
            destination = input.destination.trim(),
            imageUrl = input.imageUrl?.trim(),
            status = TripStatus.ACTIVE,
            createdBy = input.userId,
            createdAt = now,
            updatedAt = now,
            version = 1
        )

        val created = tripRepository.create(trip)

        val participant = TripParticipant(
            tripId = created.id,
            userId = input.userId,
            role = TripRole.OWNER,
            joinedAt = now
        )
        participantRepository.add(participant)

        domainEventRepository.save(
            DomainEvent(
                id = UUID.randomUUID(),
                eventType = "TRIP_CREATED",
                aggregateType = "TRIP",
                aggregateId = created.id,
                payload = buildJsonObject {
                    put("actorUserId", input.userId.toString())
                    put("tripTitle", created.title)
                }.toString(),
                createdAt = now
            )
        )

        created
    }
}
