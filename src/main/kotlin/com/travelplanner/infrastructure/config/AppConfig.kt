package com.travelplanner.infrastructure.config

import io.ktor.server.config.ApplicationConfig

data class AppConfig(
    val database: DatabaseConfig,
    val jwt: JwtConfig,
    val redis: RedisConfig,
    val s3: S3Config,
    val fcm: FcmConfig,
    val admin: AdminConfig,
    val smtp: SmtpConfig,
    val appLinks: AppLinksConfig
) {
    companion object {
        fun load(config: ApplicationConfig): AppConfig {
            return AppConfig(
                database = DatabaseConfig(
                    url = config.propertyOrNull("database.url")?.getString()
                        ?: "jdbc:postgresql://localhost:5433/travel_planner",
                    user = config.propertyOrNull("database.user")?.getString()
                        ?: "tp_user",
                    password = config.propertyOrNull("database.password")?.getString()
                        ?: "tp_pass",
                    maxPoolSize = config.propertyOrNull("database.maxPoolSize")
                        ?.getString()?.toIntOrNull() ?: 10
                ),
                jwt = JwtConfig(
                    secret = config.propertyOrNull("jwt.secret")?.getString()
                        ?: "change-me-to-a-secure-random-string-at-least-32-chars",
                    issuer = config.propertyOrNull("jwt.issuer")?.getString()
                        ?: "travel-planner",
                    audience = config.propertyOrNull("jwt.audience")?.getString()
                        ?: "travel-planner-client",
                    accessTokenExpiryMinutes = config.propertyOrNull("jwt.accessTokenExpiryMinutes")
                        ?.getString()?.toLongOrNull() ?: 30L,
                    refreshTokenExpiryDays = config.propertyOrNull("jwt.refreshTokenExpiryDays")
                        ?.getString()?.toLongOrNull() ?: 30L
                ),
                redis = RedisConfig(
                    host = config.propertyOrNull("redis.host")?.getString()
                        ?: "localhost",
                    port = config.propertyOrNull("redis.port")
                        ?.getString()?.toIntOrNull() ?: 6379
                ),
                s3 = S3Config(
                    endpoint = config.propertyOrNull("s3.endpoint")?.getString()
                        ?: "http://localhost:9000",
                    accessKey = config.propertyOrNull("s3.accessKey")?.getString()
                        ?: "minioadmin",
                    secretKey = config.propertyOrNull("s3.secretKey")?.getString()
                        ?: "minioadmin",
                    bucket = config.propertyOrNull("s3.bucket")?.getString()
                        ?: "travel-planner",
                    region = config.propertyOrNull("s3.region")?.getString()
                        ?: "us-east-1",
                    publicEndpoint = config.propertyOrNull("s3.publicEndpoint")?.getString()
                        ?.takeIf { it.isNotBlank() }
                ),
                fcm = FcmConfig(
                    serviceAccountPath = config.propertyOrNull("fcm.serviceAccountPath")
                        ?.getString() ?: ""
                ),
                admin = AdminConfig(
                    enabled = System.getenv("DEBUG_ADMIN_ENABLED")?.toBoolean()
                        ?: runCatching { config.propertyOrNull("admin.enabled")?.getString()?.toBoolean() }.getOrNull()
                        ?: false,
                    bindLocalhostOnly = System.getenv("DEBUG_ADMIN_LOCALHOST_ONLY")?.toBoolean()
                        ?: runCatching {
                            config.propertyOrNull("admin.bindLocalhostOnly")?.getString()?.toBoolean()
                        }.getOrNull()
                        ?: true
                ),
                smtp = SmtpConfig(
                    enabled = config.propertyOrNull("smtp.enabled")?.getString()?.toBoolean() ?: false,
                    host = config.propertyOrNull("smtp.host")?.getString()?.trim().orEmpty(),
                    port = config.propertyOrNull("smtp.port")?.getString()?.toIntOrNull() ?: 587,
                    username = config.propertyOrNull("smtp.username")?.getString()?.trim().orEmpty(),
                    password = config.propertyOrNull("smtp.password")?.getString().orEmpty(),
                    fromAddress = config.propertyOrNull("smtp.fromAddress")?.getString()?.trim().orEmpty(),
                    tlsMode = when (
                        config.propertyOrNull("smtp.tlsMode")?.getString()?.trim()?.lowercase() ?: "starttls"
                    ) {
                        "ssl", "smtps" -> SmtpTlsMode.SSL
                        else -> SmtpTlsMode.STARTTLS
                    }
                ),
                appLinks = AppLinksConfig(
                    publicApiBaseUrl = config.propertyOrNull("app.publicApiBaseUrl")?.getString()?.trim().orEmpty(),
                    emailVerifySuccessRedirectUrl = config.propertyOrNull("app.emailVerifySuccessRedirectUrl")
                        ?.getString()?.trim()?.takeIf { it.isNotEmpty() },
                    emailVerifyFailureRedirectUrl = config.propertyOrNull("app.emailVerifyFailureRedirectUrl")
                        ?.getString()?.trim()?.takeIf { it.isNotEmpty() }
                )
            )
        }
    }
}

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
    val maxPoolSize: Int = 10
)

data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val accessTokenExpiryMinutes: Long = 30,
    val refreshTokenExpiryDays: Long = 30
)

data class RedisConfig(
    val host: String,
    val port: Int = 6379
)

data class S3Config(
    val endpoint: String,
    val accessKey: String,
    val secretKey: String,
    val bucket: String,
    val region: String,
    val publicEndpoint: String? = null
)

data class FcmConfig(
    val serviceAccountPath: String
)

data class AdminConfig(
    val enabled: Boolean = false,
    val bindLocalhostOnly: Boolean = true
)

data class AppLinksConfig(
    val publicApiBaseUrl: String = "",
    val emailVerifySuccessRedirectUrl: String? = null,
    val emailVerifyFailureRedirectUrl: String? = null
)

enum class SmtpTlsMode {
    STARTTLS,
    SSL
}

data class SmtpConfig(
    val enabled: Boolean = false,
    val host: String = "",
    val port: Int = 587,
    val username: String = "",
    val password: String = "",
    val fromAddress: String = "",
    val tlsMode: SmtpTlsMode = SmtpTlsMode.STARTTLS
)
