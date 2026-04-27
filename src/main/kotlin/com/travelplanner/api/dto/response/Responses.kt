package com.travelplanner.api.dto.response

import kotlinx.serialization.Serializable

// ──────────────────────────────────────────────
// Auth
// ──────────────────────────────────────────────

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserResponse
)

// ──────────────────────────────────────────────
// User
// ──────────────────────────────────────────────

@Serializable
data class UserResponse(
    val id: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class DeviceResponse(
    val id: String,
    val fcmToken: String,
    val deviceName: String? = null,
    val createdAt: String
)

// ──────────────────────────────────────────────
// Trip
// ──────────────────────────────────────────────

@Serializable
data class TripResponse(
    val id: String,
    val title: String,
    val description: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val baseCurrency: String,
    val totalBudget: String,
    val destination: String,
    val imageUrl: String? = null,
    val status: String,
    val createdBy: String,
    val createdAt: String,
    val updatedAt: String,
    val version: Long,
    val deletedAt: String? = null
)

// ──────────────────────────────────────────────
// Participant
// ──────────────────────────────────────────────

@Serializable
data class ParticipantResponse(
    val tripId: String,
    val userId: String,
    val role: String,
    val joinedAt: String
)

@Serializable
data class ParticipantDetailResponse(
    val userId: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val role: String,
    val joinedAt: String
)

@Serializable
data class InvitationResponse(
    val id: String,
    val tripId: String,
    val email: String,
    val role: String,
    val status: String,
    val createdAt: String
)

// ──────────────────────────────────────────────
// Itinerary
// ──────────────────────────────────────────────

@Serializable
data class ItineraryPointResponse(
    val id: String,
    val tripId: String,
    val title: String,
    val description: String? = null,
    val subtitle: String? = null,
    val type: String? = null,
    val date: String? = null,
    val dayIndex: Int,
    val startTime: String? = null,
    val endTime: String? = null,
    val duration: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val cost: Double? = null,
    val actualCost: Double? = null,
    val status: String,
    val participantIds: List<String>,
    val sortOrder: Int,
    val createdBy: String,
    val createdAt: String,
    val updatedAt: String,
    val version: Long,
    val deletedAt: String? = null
)

// ──────────────────────────────────────────────
// Expense
// ──────────────────────────────────────────────

@Serializable
data class ExpenseResponse(
    val id: String,
    val tripId: String,
    val payerUserId: String,
    val title: String,
    val description: String? = null,
    val amount: String,
    val currency: String,
    val category: String,
    val expenseDate: String,
    val splitType: String,
    val splits: List<ExpenseSplitResponse>,
    val createdBy: String,
    val createdAt: String,
    val updatedAt: String,
    val version: Long,
    val deletedAt: String? = null
)

@Serializable
data class ExpenseSplitResponse(
    val id: String,
    val participantUserId: String,
    val shareType: String,
    val value: String,
    val amountInExpenseCurrency: String
)

// ──────────────────────────────────────────────
// Analytics
// ──────────────────────────────────────────────

@Serializable
data class BalanceResponse(
    val userId: String,
    val totalPaid: String,
    val totalOwed: String,
    val netBalance: String
)

@Serializable
data class SettlementResponse(
    val fromUserId: String,
    val toUserId: String,
    val amount: String,
    val currency: String
)

@Serializable
data class StatisticsResponse(
    val totalSpent: String,
    val currency: String,
    val spentByCategory: Map<String, String>,
    val spentByParticipant: Map<String, String>,
    val spentByDay: Map<String, String>
)

// ──────────────────────────────────────────────
// Attachment
// ──────────────────────────────────────────────

@Serializable
data class AttachmentResponse(
    val id: String,
    val tripId: String,
    val expenseId: String? = null,
    val uploadedBy: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val s3Key: String,
    val createdAt: String,
    val deletedAt: String? = null,
)

@Serializable
data class PresignedUploadResponse(
    val uploadUrl: String,
    val s3Key: String
)

@Serializable
data class PresignedDownloadResponse(
    val url: String,
    val expiresInSeconds: Long
)

// ──────────────────────────────────────────────
// Sync
// ──────────────────────────────────────────────

@Serializable
data class SnapshotResponse(
    val trip: TripResponse,
    val participants: List<ParticipantResponse>,
    val itineraryPoints: List<ItineraryPointResponse>,
    val expenses: List<ExpenseResponse>,
    val attachments: List<AttachmentResponse>,
    val cursor: String
)

@Serializable
data class DeltaResponse(
    val trips: List<TripResponse>,
    val participants: List<ParticipantResponse>,
    val itineraryPoints: List<ItineraryPointResponse>,
    val expenses: List<ExpenseResponse>,
    val attachments: List<AttachmentResponse>,
    val cursor: String
)

// ──────────────────────────────────────────────
// Health
// ──────────────────────────────────────────────

@Serializable
data class HealthResponse(
    val status: String,
    val timestamp: String
)
