package com.travelplanner.domain.model

import java.time.Instant

data class SyncCursor(
    val timestamp: Instant,
    val entityVersions: Map<String, Long> = emptyMap()
)

data class JoinRequestWithUser(
    val request: TripJoinRequest,
    val displayName: String,
    val email: String,
)

data class ItineraryPointCommentWithAuthor(
    val comment: ItineraryPointComment,
    val authorDisplayName: String,
)

data class SyncDelta(
    val trips: List<Trip> = emptyList(),
    val participants: List<TripParticipant> = emptyList(),
    val itineraryPoints: List<ItineraryPoint> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val expenseSplits: List<ExpenseSplit> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val checklistItems: List<ChecklistItem> = emptyList(),
    val pendingJoinRequests: List<JoinRequestWithUser> = emptyList(),
    val historyEntries: List<DomainEvent> = emptyList(),
    val pointLinks: List<ItineraryPointLink> = emptyList(),
    val pointComments: List<ItineraryPointCommentWithAuthor> = emptyList(),
    val cursor: SyncCursor,
)

data class TripSnapshot(
    val trip: Trip,
    val participants: List<TripParticipant>,
    val itineraryPoints: List<ItineraryPoint>,
    val expenses: List<Expense>,
    val expenseSplits: List<ExpenseSplit>,
    val attachments: List<Attachment>,
    val checklistItems: List<ChecklistItem> = emptyList(),
    val pendingJoinRequests: List<JoinRequestWithUser> = emptyList(),
    val historyEntries: List<DomainEvent> = emptyList(),
    val pointLinks: List<ItineraryPointLink> = emptyList(),
    val pointComments: List<ItineraryPointCommentWithAuthor> = emptyList(),
    val cursor: SyncCursor,
)
