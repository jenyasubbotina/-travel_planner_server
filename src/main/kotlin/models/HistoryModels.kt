package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class HistoryLogDto(
    val id: String,
    val tripId: Long,
    val userId: String,
    val actionType: String,
    val entityType: String,
    val entityId: String,
    val details: String,
    val timestamp: Long
)