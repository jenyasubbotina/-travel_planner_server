package com.travelplanner.domain.exception

import java.util.UUID

sealed class DomainException(override val message: String, val code: String) : RuntimeException(message) {

    // Auth
    class EmailAlreadyExists(email: String) : DomainException("User with email '$email' already exists", "EMAIL_ALREADY_EXISTS")
    class InvalidCredentials : DomainException("Invalid email or password", "INVALID_CREDENTIALS")
    class InvalidRefreshToken : DomainException("Invalid or expired refresh token", "INVALID_REFRESH_TOKEN")
    class TokenExpired : DomainException("Token has expired", "TOKEN_EXPIRED")

    // Not found
    class UserNotFound(id: UUID) : DomainException("User not found: $id", "USER_NOT_FOUND")
    class TripNotFound(id: UUID) : DomainException("Trip not found: $id", "TRIP_NOT_FOUND")
    class InvitationNotFound(id: UUID) : DomainException("Invitation not found: $id", "INVITATION_NOT_FOUND")
    class ItineraryPointNotFound(id: UUID) : DomainException("Itinerary point not found: $id", "ITINERARY_POINT_NOT_FOUND")
    class ExpenseNotFound(id: UUID) : DomainException("Expense not found: $id", "EXPENSE_NOT_FOUND")
    class AttachmentNotFound(id: UUID) : DomainException("Attachment not found: $id", "ATTACHMENT_NOT_FOUND")
    class DeviceNotFound(id: UUID) : DomainException("Device not found: $id", "DEVICE_NOT_FOUND")

    // Access
    class AccessDenied(reason: String = "Access denied") : DomainException(reason, "ACCESS_DENIED")
    class InsufficientRole(required: String) : DomainException("Insufficient role. Required: $required", "INSUFFICIENT_ROLE")

    // Validation
    class ValidationError(details: String) : DomainException(details, "VALIDATION_ERROR")
    class InvalidSplitSum(expected: String, actual: String) : DomainException("Split amounts ($actual) do not sum to expense total ($expected)", "INVALID_SPLIT_SUM")
    class ParticipantNotInTrip(userId: UUID, tripId: UUID) : DomainException("User $userId is not a participant of trip $tripId", "PARTICIPANT_NOT_IN_TRIP")

    // Conflict
    class VersionConflict(entity: String, id: UUID) : DomainException("Version conflict for $entity $id", "VERSION_CONFLICT")
    class IdempotencyConflict(key: String) : DomainException("Duplicate mutation: $key", "IDEMPOTENCY_CONFLICT")
    class InvitationAlreadyResolved(id: UUID) : DomainException("Invitation $id already resolved", "INVITATION_ALREADY_RESOLVED")
    class PendingUpdateStored(val expenseId: UUID) : DomainException("Conflicting edit stored as pending", "PENDING_UPDATE_STORED")
    class NoPendingUpdate(val expenseId: UUID) : DomainException("No pending update for expense $expenseId", "NO_PENDING_UPDATE")
    class AnotherPendingUpdate(val expenseId: UUID, val proposerUserId: UUID) :
        DomainException(
            "Another participant has already proposed an edit to expense $expenseId; wait for it to be resolved",
            "ANOTHER_PENDING_UPDATE",
        )

    // Trip state
    class TripNotActive(id: UUID) : DomainException("Trip $id is not active", "TRIP_NOT_ACTIVE")
    class AlreadyParticipant(userId: UUID, tripId: UUID) : DomainException("User $userId is already a participant of trip $tripId", "ALREADY_PARTICIPANT")
}
