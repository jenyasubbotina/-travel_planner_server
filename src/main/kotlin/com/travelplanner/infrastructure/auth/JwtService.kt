package com.travelplanner.infrastructure.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.travelplanner.infrastructure.config.JwtConfig
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID

class JwtService(private val config: JwtConfig) {

    private val algorithm: Algorithm = Algorithm.HMAC256(config.secret)

    val issuer: String get() = config.issuer
    val audience: String get() = config.audience
    val realm: String = "travel-planner"

    fun generateAccessToken(userId: UUID, email: String): String {
        return JWT.create()
            .withSubject(userId.toString())
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .withClaim("email", email)
            .withExpiresAt(
                Date.from(
                    Instant.now().plus(config.accessTokenExpiryMinutes, ChronoUnit.MINUTES)
                )
            )
            .sign(algorithm)
    }

    fun generateRefreshToken(): String = UUID.randomUUID().toString()

    fun getVerifier(): JWTVerifier =
        JWT.require(algorithm)
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .build()
}
