package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class PendingExpenseUpdateDto(
    val editorUserId: String,
    val editorName: String,
    val timestamp: Long,
    val proposedExpense: CreateExpenseRequest
)

@Serializable
data class ExpenseDto(
    val id: String,
    val tripId: Long,
    val title: String,
    val amount: Double,
    val category: String,
    val payerUserId: String,
    val date: Long,
    val splits: List<SplitDto>,
    val creatorUserId: String,
    val pendingUpdate: PendingExpenseUpdateDto? = null,
    val imageUrl: String? = null
)

@Serializable
data class CreateExpenseRequest(
    val title: String,
    val amount: Double,
    val category: String,
    val payerUserId: String,
    val date: Long,
    val splits: List<SplitDto>,
    val imageUrl: String? = null
)

@Serializable
data class SplitDto(
    val userId: String,
    val amount: Double
)

@Serializable
data class TripDto(
    val id: Long,
    val title: String,
    val destination: String,
    val startDate: Long,
    val endDate: Long,
    val totalBudget: Double,
    val description: String?,
    val ownerUserId: String,
    val joinCode: String,
    val status: String = "ACTIVE",
    val currency: String = "¥",
    val imageUrl: String? = null,
    val filesJson: String = "[]",
)

@Serializable
data class CreateTripRequest(
    val title: String,
    val destination: String,
    val startDate: Long,
    val endDate: Long,
    val totalBudget: Double,
    val description: String?,
    val ownerUserId: String,
    val ownerName: String,
    val ownerEmail: String,
    val currency: String = "¥",
    val imageUrl: String? = null,
)

@Serializable
data class UserDto(
    val id: String,
    val name: String,
    val email: String
)

@Serializable
data class JoinTripRequest(
    val userId: String,
    val userName: String
)

@Serializable
data class JoinByCodeRequest(
    val code: String,
    val userId: String,
    val userName: String
)

@Serializable
data class EventLinkDto(val title: String, val url: String)

@Serializable
data class EventCommentDto(val userId: String, val userName: String, val text: String, val timestamp: Long)

@Serializable
data class EventFileDto(
    val name: String,
    val url: String,
    val type: String = "DOCUMENT"
)

@Serializable
data class EventDto(
    val id: String,
    val tripId: Long,
    val dayIndex: Int,
    val time: String,
    val title: String,
    val subtitle: String,
    val description: String?,
    val duration: String?,
    val cost: Double,
    val actualCost: Double? = 0.0,
    val status: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val links: List<EventLinkDto> = emptyList(),
    val comments: List<EventCommentDto> = emptyList(),
    val files: List<EventFileDto> = emptyList(),
    val participantIds: List<String> = emptyList()
)

@Serializable
data class UpdateBudgetRequest(
    val totalBudget: Double
)

@Serializable
data class ChecklistItemDto(
    val id: String,
    val tripId: Long,
    val title: String,
    val isGroup: Boolean,
    val ownerUserId: String,
    val completedBy: List<String>
)

@Serializable
data class CreateChecklistItemRequest(
    val title: String,
    val isGroup: Boolean,
)
