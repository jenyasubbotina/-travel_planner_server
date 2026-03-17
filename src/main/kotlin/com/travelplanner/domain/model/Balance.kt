package com.travelplanner.domain.model

import java.math.BigDecimal
import java.util.UUID

data class ParticipantBalance(
    val userId: UUID,
    val totalPaid: BigDecimal,
    val totalOwed: BigDecimal,
    val netBalance: BigDecimal
)

data class Settlement(
    val fromUserId: UUID,
    val toUserId: UUID,
    val amount: BigDecimal,
    val currency: String
)

data class TripStatistics(
    val totalSpent: BigDecimal,
    val currency: String,
    val spentByCategory: Map<String, BigDecimal>,
    val spentByParticipant: Map<UUID, BigDecimal>,
    val spentByDay: Map<String, BigDecimal>
)
