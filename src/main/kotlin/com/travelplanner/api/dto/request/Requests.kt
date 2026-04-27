package com.travelplanner.api.dto.request

import kotlinx.serialization.Serializable

// ──────────────────────────────────────────────
// Auth
// ──────────────────────────────────────────────

@Serializable
data class RegisterRequest(
    val email: String,
    val displayName: String,
    val password: String
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

// ──────────────────────────────────────────────
// Device
// ──────────────────────────────────────────────

@Serializable
data class RegisterDeviceRequest(
    val fcmToken: String,
    val deviceName: String? = null
)

// ──────────────────────────────────────────────
// Trip
// ──────────────────────────────────────────────

@Serializable
data class CreateTripRequest(
    val title: String,
    val description: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val baseCurrency: String = "USD",
    val totalBudget: String? = null,
    val destination: String? = null,
    val imageUrl: String? = null,
    val clientMutationId: String? = null
)

@Serializable
data class UpdateTripRequest(
    val title: String? = null,
    val description: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val baseCurrency: String? = null,
    val totalBudget: String? = null,
    val destination: String? = null,
    val imageUrl: String? = null,
    val status: String? = null,
    val expectedVersion: Long? = null,
    val clientMutationId: String? = null
)

// ──────────────────────────────────────────────
// Participant
// ──────────────────────────────────────────────

@Serializable
data class InviteParticipantRequest(
    val email: String,
    val role: String = "EDITOR",
    val clientMutationId: String? = null
)

@Serializable
data class ChangeRoleRequest(
    val role: String,
    val clientMutationId: String? = null
)

// ──────────────────────────────────────────────
// Itinerary
// ──────────────────────────────────────────────

@Serializable
data class CreateItineraryPointRequest(
    val title: String,
    val description: String? = null,
    val subtitle: String? = null,
    val type: String? = null,
    val category: String? = null,
    val date: String? = null,
    val dayIndex: Int? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val duration: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val cost: Double? = null,
    val actualCost: Double? = null,
    val status: String? = null,
    val participantIds: List<String>? = null,
    val clientMutationId: String? = null
)

@Serializable
data class UpdateItineraryPointRequest(
    val title: String? = null,
    val description: String? = null,
    val subtitle: String? = null,
    val type: String? = null,
    val category: String? = null,
    val date: String? = null,
    val dayIndex: Int? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val duration: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val cost: Double? = null,
    val actualCost: Double? = null,
    val status: String? = null,
    val participantIds: List<String>? = null,
    val expectedVersion: Long? = null,
    val clientMutationId: String? = null
)

@Serializable
data class AddPointLinkRequest(
    val title: String,
    val url: String,
)

@Serializable
data class AddPointCommentRequest(
    val text: String,
)

// ──────────────────────────────────────────────
// Checklist
// ──────────────────────────────────────────────

@Serializable
data class CreateChecklistItemRequest(
    val title: String,
    val isGroup: Boolean = false,
)

// ──────────────────────────────────────────────
// Join by code
// ──────────────────────────────────────────────

@Serializable
data class JoinByCodeRequest(
    val code: String,
)

@Serializable
data class ReorderRequest(
    val items: List<ReorderItem>,
    val clientMutationId: String? = null
)

@Serializable
data class ReorderItem(
    val id: String,
    val sortOrder: Int
)

// ──────────────────────────────────────────────
// Expense
// ──────────────────────────────────────────────

@Serializable
data class CreateExpenseRequest(
    val title: String,
    val description: String? = null,
    val amount: String,
    val currency: String,
    val category: String,
    val payerUserId: String,
    val expenseDate: String,
    val splitType: String = "EQUAL",
    val splits: List<ExpenseSplitRequest>,
    val clientMutationId: String? = null
)

@Serializable
data class UpdateExpenseRequest(
    val title: String? = null,
    val description: String? = null,
    val amount: String? = null,
    val currency: String? = null,
    val category: String? = null,
    val payerUserId: String? = null,
    val expenseDate: String? = null,
    val splitType: String? = null,
    val splits: List<ExpenseSplitRequest>? = null,
    val expectedVersion: Long? = null,
    val clientMutationId: String? = null
)

@Serializable
data class ExpenseSplitRequest(
    val participantUserId: String,
    val value: String
)

@Serializable
data class MergeExpenseRequest(
    val title: String? = null,
    val description: String? = null,
    val amount: String? = null,
    val currency: String? = null,
    val category: String? = null,
    val payerUserId: String? = null,
    val expenseDate: String? = null,
    val splitType: String? = null,
    val splits: List<ExpenseSplitRequest>? = null,
    val clientMutationId: String? = null,
)

// ──────────────────────────────────────────────
// Attachment
// ──────────────────────────────────────────────

@Serializable
data class PresignUploadRequest(
    val fileName: String,
    val contentType: String,
    val fileSize: Long,
    val tripId: String
)

@Serializable
data class PresignDownloadRequest(
    val s3Key: String
)

@Serializable
data class CreateAttachmentRequest(
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val s3Key: String,
    val clientMutationId: String? = null
)
