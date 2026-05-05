package com.travelplanner.domain.repository

import com.travelplanner.domain.model.TripInvitation
import com.travelplanner.domain.model.TripParticipant
import com.travelplanner.domain.model.TripRole
import com.travelplanner.domain.model.User
import java.util.UUID

interface ParticipantRepository {
    suspend fun findByTrip(tripId: UUID): List<TripParticipant>
    suspend fun findByTripAndUser(tripId: UUID, userId: UUID): TripParticipant?
    suspend fun findTripsByUser(userId: UUID): List<UUID>
    suspend fun add(participant: TripParticipant)
    suspend fun updateRole(tripId: UUID, userId: UUID, role: TripRole, expectedVersion: Long): TripParticipant
    suspend fun softDelete(tripId: UUID, userId: UUID, expectedVersion: Long): TripParticipant
    suspend fun isParticipant(tripId: UUID, userId: UUID): Boolean
    suspend fun getUserIdsForTrip(tripId: UUID): List<UUID>

    // Invitations
    suspend fun createInvitation(invitation: TripInvitation): TripInvitation
    suspend fun findInvitationById(id: UUID): TripInvitation?
    suspend fun findPendingInvitationsByTrip(tripId: UUID): List<TripInvitation>
    suspend fun findPendingInvitationsByEmail(email: String): List<TripInvitation>
    suspend fun updateInvitation(invitation: TripInvitation): TripInvitation
    suspend fun acceptInvitation(invitationId: UUID, user: User): TripParticipant
}
