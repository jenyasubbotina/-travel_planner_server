package com.travelplanner.domain.model

import java.time.Instant
import java.util.UUID

data class User(
    val id: UUID,
    val email: String,
    val displayName: String,
    val passwordHash: String,
    val avatarUrl: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class UserDevice(
    val id: UUID,
    val userId: UUID,
    val fcmToken: String,
    val deviceName: String? = null,
    val createdAt: Instant
)

data class RefreshToken(
    val id: UUID,
    val userId: UUID,
    val tokenHash: String,
    val expiresAt: Instant,
    val createdAt: Instant
)
