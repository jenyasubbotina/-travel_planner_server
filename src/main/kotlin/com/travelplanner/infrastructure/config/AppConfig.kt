package com.travelplanner.infrastructure.config

import io.ktor.server.config.ApplicationConfig

data class AppConfig(
    val database: DatabaseConfig,
    val jwt: JwtConfig,
    val redis: RedisConfig,
    val s3: S3Config,
    val fcm: FcmConfig
) {
    companion object {
        fun load(config: ApplicationConfig): AppConfig {
            return AppConfig(
                database = DatabaseConfig(
                    url = envOrConfig(config, "DATABASE_URL", "database.url")
                        ?: "jdbc:postgresql://localhost:5433/travel_planner",
                    user = envOrConfig(config, "DATABASE_USER", "database.user") ?: "tp_user",
                    password = envOrConfig(config, "DATABASE_PASSWORD", "database.password")
                        ?: "tp_pass",
                    maxPoolSize = envIntOrConfig(config, "DATABASE_MAX_POOL_SIZE", "database.maxPoolSize")
                        ?: 10
                ),
                jwt = JwtConfig(
                    secret = envOrConfig(config, "JWT_SECRET", "jwt.secret")
                        ?: "change-me-to-a-secure-random-string-at-least-32-chars",
                    issuer = envOrConfig(config, "JWT_ISSUER", "jwt.issuer") ?: "travel-planner",
                    audience = envOrConfig(config, "JWT_AUDIENCE", "jwt.audience")
                        ?: "travel-planner-client",
                    accessTokenExpiryMinutes = envLongOrConfig(
                        config,
                        "JWT_ACCESS_TOKEN_EXPIRY_MINUTES",
                        "jwt.accessTokenExpiryMinutes"
                    ) ?: 30L,
                    refreshTokenExpiryDays = envLongOrConfig(
                        config,
                        "JWT_REFRESH_TOKEN_EXPIRY_DAYS",
                        "jwt.refreshTokenExpiryDays"
                    ) ?: 30L
                ),
                redis = RedisConfig(
                    host = envOrConfig(config, "REDIS_HOST", "redis.host") ?: "localhost",
                    port = envIntOrConfig(config, "REDIS_PORT", "redis.port") ?: 6379
                ),
                s3 = S3Config(
                    endpoint = envOrConfig(config, "S3_ENDPOINT", "s3.endpoint")
                        ?: "http://localhost:9000",
                    accessKey = envOrConfig(config, "S3_ACCESS_KEY", "s3.accessKey") ?: "minioadmin",
                    secretKey = envOrConfig(config, "S3_SECRET_KEY", "s3.secretKey") ?: "minioadmin",
                    bucket = envOrConfig(config, "S3_BUCKET", "s3.bucket") ?: "travel-planner",
                    region = envOrConfig(config, "S3_REGION", "s3.region") ?: "us-east-1"
                ),
                fcm = FcmConfig(
                    serviceAccountPath = envOrConfig(config, "FCM_SERVICE_ACCOUNT_PATH", "fcm.serviceAccountPath")
                        ?: ""
                )
            )
        }

        private fun envOrConfig(config: ApplicationConfig, envName: String, path: String): String? {
            System.getenv(envName)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            return config.propertyOrNull(path)?.getString()
        }

        private fun envIntOrConfig(config: ApplicationConfig, envName: String, path: String): Int? {
            System.getenv(envName)?.trim()?.toIntOrNull()?.let { return it }
            return config.propertyOrNull(path)?.getString()?.toIntOrNull()
        }

        private fun envLongOrConfig(config: ApplicationConfig, envName: String, path: String): Long? {
            System.getenv(envName)?.trim()?.toLongOrNull()?.let { return it }
            return config.propertyOrNull(path)?.getString()?.toLongOrNull()
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
    val region: String
)

data class FcmConfig(
    val serviceAccountPath: String
)
