package com.travelplanner.api.plugins

import com.travelplanner.api.dto.ErrorResponse
import com.travelplanner.domain.exception.DomainException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<DomainException> { call, cause ->
            val (status, response) = when (cause) {
                // Auth errors
                is DomainException.EmailAlreadyExists ->
                    HttpStatusCode.Conflict to ErrorResponse(cause.code, cause.message)

                is DomainException.InvalidCredentials ->
                    HttpStatusCode.Unauthorized to ErrorResponse(cause.code, cause.message)

                is DomainException.InvalidRefreshToken ->
                    HttpStatusCode.Unauthorized to ErrorResponse(cause.code, cause.message)

                is DomainException.TokenExpired ->
                    HttpStatusCode.Unauthorized to ErrorResponse(cause.code, cause.message)

                // Not found errors
                is DomainException.UserNotFound ->
                    HttpStatusCode.NotFound to ErrorResponse(cause.code, cause.message)

                is DomainException.TripNotFound ->
                    HttpStatusCode.NotFound to ErrorResponse(cause.code, cause.message)

                is DomainException.InvitationNotFound ->
                    HttpStatusCode.NotFound to ErrorResponse(cause.code, cause.message)

                is DomainException.ItineraryPointNotFound ->
                    HttpStatusCode.NotFound to ErrorResponse(cause.code, cause.message)

                is DomainException.ExpenseNotFound ->
                    HttpStatusCode.NotFound to ErrorResponse(cause.code, cause.message)

                is DomainException.AttachmentNotFound ->
                    HttpStatusCode.NotFound to ErrorResponse(cause.code, cause.message)

                is DomainException.DeviceNotFound ->
                    HttpStatusCode.NotFound to ErrorResponse(cause.code, cause.message)

                // Access errors
                is DomainException.AccessDenied ->
                    HttpStatusCode.Forbidden to ErrorResponse(cause.code, cause.message)

                is DomainException.InsufficientRole ->
                    HttpStatusCode.Forbidden to ErrorResponse(cause.code, cause.message)

                // Validation errors
                is DomainException.ValidationError ->
                    HttpStatusCode.UnprocessableEntity to ErrorResponse(cause.code, cause.message)

                is DomainException.InvalidSplitSum ->
                    HttpStatusCode.UnprocessableEntity to ErrorResponse(cause.code, cause.message)

                is DomainException.ParticipantNotInTrip ->
                    HttpStatusCode.UnprocessableEntity to ErrorResponse(cause.code, cause.message)

                // Conflict errors
                is DomainException.VersionConflict ->
                    HttpStatusCode.Conflict to ErrorResponse(cause.code, cause.message)

                is DomainException.IdempotencyConflict ->
                    HttpStatusCode.Conflict to ErrorResponse(cause.code, cause.message)

                is DomainException.InvitationAlreadyResolved ->
                    HttpStatusCode.Conflict to ErrorResponse(cause.code, cause.message)

                is DomainException.AlreadyParticipant ->
                    HttpStatusCode.Conflict to ErrorResponse(cause.code, cause.message)

                // Trip state errors
                is DomainException.TripNotActive ->
                    HttpStatusCode.UnprocessableEntity to ErrorResponse(cause.code, cause.message)
            }
            call.respond(status, response)
        }

        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    code = "INTERNAL_ERROR",
                    message = "An internal error occurred"
                )
            )
        }
    }
}
