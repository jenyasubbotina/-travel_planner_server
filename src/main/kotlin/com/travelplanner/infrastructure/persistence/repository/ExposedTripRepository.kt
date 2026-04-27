package com.travelplanner.infrastructure.persistence.repository

import com.travelplanner.domain.model.Trip
import com.travelplanner.domain.model.TripStatus
import com.travelplanner.domain.repository.TripRepository
import com.travelplanner.infrastructure.persistence.DatabaseFactory.dbQuery
import com.travelplanner.infrastructure.persistence.tables.TripParticipantsTable
import com.travelplanner.infrastructure.persistence.tables.TripsTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant
import java.util.UUID

class ExposedTripRepository : TripRepository {

    override suspend fun findById(id: UUID): Trip? = dbQuery {
        TripsTable.selectAll()
            .where { (TripsTable.id eq id) and (TripsTable.deletedAt.isNull()) }
            .singleOrNull()
            ?.toTrip()
    }

    override suspend fun findByIdIncludeDeleted(id: UUID): Trip? = dbQuery {
        TripsTable.selectAll()
            .where { TripsTable.id eq id }
            .singleOrNull()
            ?.toTrip()
    }

    override suspend fun findByUser(userId: UUID): List<Trip> = dbQuery {
        val participantTripIds = TripParticipantsTable
            .select(TripParticipantsTable.tripId)
            .where { TripParticipantsTable.userId eq userId }
            .map { it[TripParticipantsTable.tripId] }

        TripsTable.selectAll()
            .where {
                ((TripsTable.id inList participantTripIds) or (TripsTable.createdBy eq userId)) and
                    TripsTable.deletedAt.isNull()
            }
            .map { it.toTrip() }
    }

    override suspend fun create(trip: Trip): Trip = dbQuery {
        TripsTable.insert {
            it[id] = trip.id
            it[title] = trip.title
            it[description] = trip.description
            it[startDate] = trip.startDate
            it[endDate] = trip.endDate
            it[baseCurrency] = trip.baseCurrency
            it[totalBudget] = trip.totalBudget
            it[destination] = trip.destination
            it[imageUrl] = trip.imageUrl
            it[status] = trip.status.name
            it[createdBy] = trip.createdBy
            it[createdAt] = trip.createdAt
            it[updatedAt] = trip.updatedAt
            it[version] = trip.version
            it[deletedAt] = trip.deletedAt
        }
        trip
    }

    override suspend fun update(trip: Trip): Trip = dbQuery {
        val now = Instant.now()
        val newVersion = trip.version + 1
        TripsTable.update({ TripsTable.id eq trip.id }) {
            it[title] = trip.title
            it[description] = trip.description
            it[startDate] = trip.startDate
            it[endDate] = trip.endDate
            it[baseCurrency] = trip.baseCurrency
            it[totalBudget] = trip.totalBudget
            it[destination] = trip.destination
            it[imageUrl] = trip.imageUrl
            it[status] = trip.status.name
            it[updatedAt] = now
            it[version] = newVersion
        }
        trip.copy(updatedAt = now, version = newVersion)
    }

    override suspend fun softDelete(id: UUID, deletedAt: Instant): Boolean = dbQuery {
        val updatedCount = TripsTable.update({ TripsTable.id eq id }) {
            it[TripsTable.deletedAt] = deletedAt
            it[updatedAt] = deletedAt
            it[version] = with(SqlExpressionBuilder) { TripsTable.version + 1L }
        }
        updatedCount > 0
    }

    override suspend fun findModifiedAfter(tripId: UUID, after: Instant): List<Trip> = dbQuery {
        TripsTable.selectAll()
            .where { (TripsTable.id eq tripId) and (TripsTable.updatedAt greater after) }
            .map { it.toTrip() }
    }

    // --- Mapping helper ---

    private fun ResultRow.toTrip() = Trip(
        id = this[TripsTable.id],
        title = this[TripsTable.title],
        description = this[TripsTable.description],
        startDate = this[TripsTable.startDate],
        endDate = this[TripsTable.endDate],
        baseCurrency = this[TripsTable.baseCurrency],
        totalBudget = this[TripsTable.totalBudget],
        destination = this[TripsTable.destination],
        imageUrl = this[TripsTable.imageUrl],
        status = TripStatus.valueOf(this[TripsTable.status]),
        createdBy = this[TripsTable.createdBy],
        createdAt = this[TripsTable.createdAt],
        updatedAt = this[TripsTable.updatedAt],
        version = this[TripsTable.version],
        deletedAt = this[TripsTable.deletedAt]
    )
}
