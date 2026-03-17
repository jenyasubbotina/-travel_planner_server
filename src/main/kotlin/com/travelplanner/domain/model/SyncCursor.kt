package com.travelplanner.domain.model

import java.time.Instant

data class SyncCursor(
    val timestamp: Instant,
    val entityVersions: Map<String, Long> = emptyMap()
)

data class SyncDelta(
    val trips: List<Trip> = emptyList(),
    val participants: List<TripParticipant> = emptyList(),
    val itineraryPoints: List<ItineraryPoint> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val expenseSplits: List<ExpenseSplit> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val cursor: SyncCursor
)

data class TripSnapshot(
    val trip: Trip,
    val participants: List<TripParticipant>,
    val itineraryPoints: List<ItineraryPoint>,
    val expenses: List<Expense>,
    val expenseSplits: List<ExpenseSplit>,
    val attachments: List<Attachment>,
    val cursor: SyncCursor
)
