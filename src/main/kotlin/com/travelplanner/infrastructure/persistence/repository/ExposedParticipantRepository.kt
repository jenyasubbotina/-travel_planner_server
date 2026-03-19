package com.travelplanner.infrastructure.persistence.repository

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.InvitationStatus
import com.travelplanner.domain.model.TripInvitation
import com.travelplanner.domain.model.TripParticipant
import com.travelplanner.domain.model.TripRole
import com.travelplanner.domain.model.User
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.infrastructure.persistence.DatabaseFactory.dbQuery
import com.travelplanner.infrastructure.persistence.tables.TripInvitationsTable
import com.travelplanner.infrastructure.persistence.tables.TripParticipantsTable
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant
import java.util.UUID

class ExposedParticipantRepository : ParticipantRepository {

    override suspend fun findByTrip(tripId: UUID): List<TripParticipant> = dbQuery {
        TripParticipantsTable.selectAll()
            .where { TripParticipantsTable.tripId eq tripId }
            .map { it.toTripParticipant() }
    }

    override suspend fun findByTripAndUser(tripId: UUID, userId: UUID): TripParticipant? = dbQuery {
        TripParticipantsTable.selectAll()
            .where {
                (TripParticipantsTable.tripId eq tripId) and
                    (TripParticipantsTable.userId eq userId)
            }
            .singleOrNull()
            ?.toTripParticipant()
    }

    override suspend fun findTripsByUser(userId: UUID): List<UUID> = dbQuery {
        TripParticipantsTable
            .select(TripParticipantsTable.tripId)
            .where { TripParticipantsTable.userId eq userId }
            .map { it[TripParticipantsTable.tripId] }
    }

    override suspend fun add(participant: TripParticipant): Unit = dbQuery {
        TripParticipantsTable.insert {
            it[tripId] = participant.tripId
            it[userId] = participant.userId
            it[role] = participant.role.name
            it[joinedAt] = participant.joinedAt
        }
    }

    override suspend fun updateRole(tripId: UUID, userId: UUID, role: TripRole): Boolean = dbQuery {
        val updatedCount = TripParticipantsTable.update({
            (TripParticipantsTable.tripId eq tripId) and
                (TripParticipantsTable.userId eq userId)
        }) {
            it[TripParticipantsTable.role] = role.name
        }
        updatedCount > 0
    }

    override suspend fun remove(tripId: UUID, userId: UUID): Boolean = dbQuery {
        val deletedCount = TripParticipantsTable.deleteWhere {
            (TripParticipantsTable.tripId eq tripId) and
                (TripParticipantsTable.userId eq userId)
        }
        deletedCount > 0
    }

    override suspend fun isParticipant(tripId: UUID, userId: UUID): Boolean = dbQuery {
        TripParticipantsTable.selectAll()
            .where {
                (TripParticipantsTable.tripId eq tripId) and
                    (TripParticipantsTable.userId eq userId)
            }
            .count() > 0
    }

    override suspend fun getUserIdsForTrip(tripId: UUID): List<UUID> = dbQuery {
        TripParticipantsTable
            .select(TripParticipantsTable.userId)
            .where { TripParticipantsTable.tripId eq tripId }
            .map { it[TripParticipantsTable.userId] }
    }

    // --- Invitations ---

    override suspend fun createInvitation(invitation: TripInvitation): TripInvitation = dbQuery {
        TripInvitationsTable.insert {
            it[id] = invitation.id
            it[tripId] = invitation.tripId
            it[email] = invitation.email
            it[invitedBy] = invitation.invitedBy
            it[role] = invitation.role.name
            it[status] = invitation.status.name
            it[createdAt] = invitation.createdAt
            it[resolvedAt] = invitation.resolvedAt
        }
        invitation
    }

    override suspend fun findInvitationById(id: UUID): TripInvitation? = dbQuery {
        TripInvitationsTable.selectAll()
            .where { TripInvitationsTable.id eq id }
            .singleOrNull()
            ?.toTripInvitation()
    }

    override suspend fun findPendingInvitationsByTrip(tripId: UUID): List<TripInvitation> = dbQuery {
        TripInvitationsTable.selectAll()
            .where {
                (TripInvitationsTable.tripId eq tripId) and
                    (TripInvitationsTable.status eq InvitationStatus.PENDING.name)
            }
            .map { it.toTripInvitation() }
    }

    override suspend fun findPendingInvitationsByEmail(email: String): List<TripInvitation> = dbQuery {
        TripInvitationsTable.selectAll()
            .where {
                (TripInvitationsTable.email eq email) and
                    (TripInvitationsTable.status eq InvitationStatus.PENDING.name)
            }
            .map { it.toTripInvitation() }
    }

    override suspend fun updateInvitation(invitation: TripInvitation): TripInvitation = dbQuery {
        TripInvitationsTable.update({ TripInvitationsTable.id eq invitation.id }) {
            it[status] = invitation.status.name
            it[resolvedAt] = invitation.resolvedAt
        }
        invitation
    }

    override suspend fun acceptInvitation(invitationId: UUID, user: User): TripParticipant = dbQuery {
        val invitation = TripInvitationsTable.selectAll()
            .where { TripInvitationsTable.id eq invitationId }
            .singleOrNull()
            ?.toTripInvitation()
            ?: throw DomainException.InvitationNotFound(invitationId)

        if (invitation.status != InvitationStatus.PENDING) {
            throw DomainException.InvitationAlreadyResolved(invitationId)
        }

        if (!user.email.equals(invitation.email, ignoreCase = true)) {
            throw DomainException.AccessDenied("This invitation was sent to a different email address")
        }

        val existingParticipant = TripParticipantsTable.selectAll()
            .where {
                (TripParticipantsTable.tripId eq invitation.tripId) and
                    (TripParticipantsTable.userId eq user.id)
            }
            .singleOrNull()

        if (existingParticipant != null) {
            throw DomainException.AlreadyParticipant(user.id, invitation.tripId)
        }

        val now = Instant.now()
        val participant = TripParticipant(
            tripId = invitation.tripId,
            userId = user.id,
            role = invitation.role,
            joinedAt = now
        )

        TripInvitationsTable.update({ TripInvitationsTable.id eq invitation.id }) {
            it[status] = InvitationStatus.ACCEPTED.name
            it[resolvedAt] = now
        }

        try {
            TripParticipantsTable.insert {
                it[tripId] = participant.tripId
                it[userId] = participant.userId
                it[role] = participant.role.name
                it[joinedAt] = participant.joinedAt
            }
        } catch (cause: ExposedSQLException) {
            if (cause.sqlState == "23505") {
                throw DomainException.AlreadyParticipant(user.id, invitation.tripId)
            }
            throw cause
        }

        participant
    }

    // --- Mapping helpers ---

    private fun ResultRow.toTripParticipant() = TripParticipant(
        tripId = this[TripParticipantsTable.tripId],
        userId = this[TripParticipantsTable.userId],
        role = TripRole.valueOf(this[TripParticipantsTable.role]),
        joinedAt = this[TripParticipantsTable.joinedAt]
    )

    private fun ResultRow.toTripInvitation() = TripInvitation(
        id = this[TripInvitationsTable.id],
        tripId = this[TripInvitationsTable.tripId],
        email = this[TripInvitationsTable.email],
        invitedBy = this[TripInvitationsTable.invitedBy],
        role = TripRole.valueOf(this[TripInvitationsTable.role]),
        status = InvitationStatus.valueOf(this[TripInvitationsTable.status]),
        createdAt = this[TripInvitationsTable.createdAt],
        resolvedAt = this[TripInvitationsTable.resolvedAt]
    )
}
