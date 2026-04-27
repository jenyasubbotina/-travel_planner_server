package com.travelplanner.infrastructure.persistence.repository

import com.travelplanner.domain.model.JoinRequestStatus
import com.travelplanner.domain.model.TripJoinRequest
import com.travelplanner.domain.repository.JoinRequestRepository
import com.travelplanner.infrastructure.persistence.DatabaseFactory.dbQuery
import com.travelplanner.infrastructure.persistence.tables.TripJoinRequestsTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant
import java.util.UUID

class ExposedJoinRequestRepository : JoinRequestRepository {

    override suspend fun findPendingByTrip(tripId: UUID): List<TripJoinRequest> = dbQuery {
        TripJoinRequestsTable.selectAll()
            .where {
                (TripJoinRequestsTable.tripId eq tripId) and
                    (TripJoinRequestsTable.status eq JoinRequestStatus.PENDING.name)
            }
            .orderBy(TripJoinRequestsTable.createdAt, SortOrder.DESC)
            .map { it.toRequest() }
    }

    override suspend fun findById(id: UUID): TripJoinRequest? = dbQuery {
        TripJoinRequestsTable.selectAll()
            .where { TripJoinRequestsTable.id eq id }
            .singleOrNull()
            ?.toRequest()
    }

    override suspend fun findPendingByTripAndUser(tripId: UUID, userId: UUID): TripJoinRequest? = dbQuery {
        TripJoinRequestsTable.selectAll()
            .where {
                (TripJoinRequestsTable.tripId eq tripId) and
                    (TripJoinRequestsTable.requesterUserId eq userId) and
                    (TripJoinRequestsTable.status eq JoinRequestStatus.PENDING.name)
            }
            .singleOrNull()
            ?.toRequest()
    }

    override suspend fun create(request: TripJoinRequest): TripJoinRequest = dbQuery {
        TripJoinRequestsTable.insert {
            it[id] = request.id
            it[tripId] = request.tripId
            it[requesterUserId] = request.requesterUserId
            it[status] = request.status.name
            it[createdAt] = request.createdAt
            it[resolvedAt] = request.resolvedAt
            it[resolvedBy] = request.resolvedBy
        }
        request
    }

    override suspend fun updateStatus(
        id: UUID,
        status: JoinRequestStatus,
        resolverUserId: UUID,
        resolvedAt: Instant
    ): Boolean = dbQuery {
        TripJoinRequestsTable.update({ TripJoinRequestsTable.id eq id }) {
            it[TripJoinRequestsTable.status] = status.name
            it[TripJoinRequestsTable.resolvedAt] = resolvedAt
            it[TripJoinRequestsTable.resolvedBy] = resolverUserId
        } > 0
    }

    private fun ResultRow.toRequest() = TripJoinRequest(
        id = this[TripJoinRequestsTable.id],
        tripId = this[TripJoinRequestsTable.tripId],
        requesterUserId = this[TripJoinRequestsTable.requesterUserId],
        status = JoinRequestStatus.valueOf(this[TripJoinRequestsTable.status]),
        createdAt = this[TripJoinRequestsTable.createdAt],
        resolvedAt = this[TripJoinRequestsTable.resolvedAt],
        resolvedBy = this[TripJoinRequestsTable.resolvedBy],
    )
}
