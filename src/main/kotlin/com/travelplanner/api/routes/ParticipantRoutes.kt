package com.travelplanner.api.routes

import com.travelplanner.api.dto.request.ChangeRoleRequest
import com.travelplanner.api.dto.request.InviteParticipantRequest
import com.travelplanner.api.dto.response.InvitationResponse
import com.travelplanner.api.dto.response.ParticipantDetailResponse
import com.travelplanner.api.dto.response.ParticipantResponse
import com.travelplanner.api.middleware.currentUserId
import com.travelplanner.api.middleware.requireTripParticipant
import com.travelplanner.api.middleware.tripIdParam
import com.travelplanner.api.middleware.uuidParam
import com.travelplanner.application.usecase.participant.AcceptInvitationUseCase
import com.travelplanner.application.usecase.participant.ChangeRoleUseCase
import com.travelplanner.application.usecase.participant.InviteParticipantUseCase
import com.travelplanner.application.usecase.participant.RemoveParticipantUseCase
import com.travelplanner.domain.model.TripInvitation
import com.travelplanner.domain.model.TripParticipant
import com.travelplanner.domain.model.TripRole
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.UserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Route.participantRoutes() {
    val inviteParticipantUseCase by inject<InviteParticipantUseCase>()
    val acceptInvitationUseCase by inject<AcceptInvitationUseCase>()
    val removeParticipantUseCase by inject<RemoveParticipantUseCase>()
    val changeRoleUseCase by inject<ChangeRoleUseCase>()
    val participantRepository by inject<ParticipantRepository>()
    val userRepository by inject<UserRepository>()

    authenticate("auth-jwt") {
        route("/api/v1/trips/{tripId}/participants") {
            get {
                val tripId = tripIdParam()
                val userId = currentUserId()
                requireTripParticipant(participantRepository, tripId, userId)
                val participants = participantRepository.findByTrip(tripId)
                val details = participants.map { p ->
                    val user = userRepository.findById(p.userId)
                    ParticipantDetailResponse(
                        userId = p.userId.toString(),
                        email = user?.email ?: "",
                        displayName = user?.displayName ?: "",
                        avatarUrl = user?.avatarUrl,
                        role = p.role.name,
                        joinedAt = p.joinedAt.toString()
                    )
                }
                call.respond(HttpStatusCode.OK, details)
            }

            post("/invite") {
                val tripId = tripIdParam()
                val userId = currentUserId()
                val req = call.receive<InviteParticipantRequest>()
                val invitation = inviteParticipantUseCase.execute(
                    InviteParticipantUseCase.Input(
                        tripId = tripId,
                        inviterUserId = userId,
                        email = req.email,
                        role = TripRole.valueOf(req.role)
                    )
                )
                call.respond(HttpStatusCode.Created, invitation.toResponse())
            }

            delete("/{userId}") {
                val tripId = tripIdParam()
                val requesterId = currentUserId()
                val targetUserId = uuidParam("userId")
                removeParticipantUseCase.execute(
                    RemoveParticipantUseCase.Input(
                        tripId = tripId,
                        requesterUserId = requesterId,
                        targetUserId = targetUserId
                    )
                )
                call.respond(HttpStatusCode.NoContent)
            }

            patch("/{userId}") {
                val tripId = tripIdParam()
                val requesterId = currentUserId()
                val targetUserId = uuidParam("userId")
                val req = call.receive<ChangeRoleRequest>()
                changeRoleUseCase.execute(
                    ChangeRoleUseCase.Input(
                        tripId = tripId,
                        requesterUserId = requesterId,
                        targetUserId = targetUserId,
                        newRole = TripRole.valueOf(req.role)
                    )
                )
                call.respond(HttpStatusCode.OK, mapOf("message" to "Role updated"))
            }
        }

        route("/api/v1/trip-invitations") {
            get {
                val userId = currentUserId()
                val user = userRepository.findById(userId)
                    ?: return@get call.respond(HttpStatusCode.OK, emptyList<InvitationResponse>())

                val statusFilter = call.request.queryParameters["status"]?.uppercase()
                val pending = participantRepository.findPendingInvitationsByEmail(user.email)
                val filtered = if (statusFilter == null || statusFilter == "PENDING") {
                    pending
                } else {
                    pending.filter { it.status.name == statusFilter }
                }
                call.respond(HttpStatusCode.OK, filtered.map { it.toResponse() })
            }

            post("/{invitationId}/accept") {
                val userId = currentUserId()
                val invitationId = uuidParam("invitationId")
                val participant = acceptInvitationUseCase.execute(invitationId, userId)
                call.respond(HttpStatusCode.OK, participant.toResponse())
            }
        }
    }
}

private fun TripParticipant.toResponse() = ParticipantResponse(
    tripId = tripId.toString(),
    userId = userId.toString(),
    role = role.name,
    joinedAt = joinedAt.toString()
)

private fun TripInvitation.toResponse() = InvitationResponse(
    id = id.toString(),
    tripId = tripId.toString(),
    email = email,
    role = role.name,
    status = status.name,
    createdAt = createdAt.toString()
)
