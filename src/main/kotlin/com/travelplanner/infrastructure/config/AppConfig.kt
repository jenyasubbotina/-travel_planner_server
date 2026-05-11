package com.travelplanner.infrastructure.config

import io.ktor.server.config.ApplicationConfig
import java.net.URI
import java.net.URISyntaxException

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
            val deploymentPort = config.propertyOrNull("ktor.deployment.port")
                ?.getString()?.toIntOrNull() ?: 8080
            val rawPublicApiBaseUrl = config.propertyOrNull("app.publicApiBaseUrl")?.getString()?.trim().orEmpty()
            val publicApiBaseUrl = normalizePublicApiBaseUrl(rawPublicApiBaseUrl, deploymentPort)

            val smtp = SmtpConfig(
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
            )

            val appLinks = AppLinksConfig(
                publicApiBaseUrl = publicApiBaseUrl,
                emailVerifySuccessRedirectUrl = config.propertyOrNull("app.emailVerifySuccessRedirectUrl")
                    ?.getString()?.trim()?.takeIf { it.isNotEmpty() },
                emailVerifyFailureRedirectUrl = config.propertyOrNull("app.emailVerifyFailureRedirectUrl")
                    ?.getString()?.trim()?.takeIf { it.isNotEmpty() }
            )

            validatePublicApiBaseUrlForStartup(smtp, appLinks)

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
                smtp = smtp,
                appLinks = appLinks
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

internal fun normalizePublicApiBaseUrl(baseUrl: String, deploymentPort: Int): String {
    val trimmed = baseUrl.trim().removeSuffix("/")
    if (trimmed.isEmpty()) return ""
    val uri = try {
        URI(trimmed)
    } catch (_: Exception) {
        return baseUrl.trim()
    }
    if (!uri.scheme.equals("http", ignoreCase = true)) return trimmed
    if (uri.port > 0) return trimmed
    val host = uri.host ?: return trimmed
    if (!shouldInferHttpPortForHost(host)) return trimmed
    if (deploymentPort == 80) return trimmed
    return buildString {
        append("http://")
        uri.userInfo?.let { append(it).append('@') }
        append(host).append(':').append(deploymentPort)
        append(uri.rawPath.orEmpty())
        uri.rawQuery?.let { append('?').append(it) }
        uri.rawFragment?.let { append('#').append(it) }
    }.removeSuffix("/")
}

private fun shouldInferHttpPortForHost(host: String): Boolean {
    if (host.equals("localhost", ignoreCase = true)) return true
    if (host == "127.0.0.1" || host == "::1") return true
    return host.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$"))
}

internal fun validatePublicApiBaseUrlForStartup(smtp: SmtpConfig, appLinks: AppLinksConfig) {
    val base = appLinks.publicApiBaseUrl.trim()
    if (smtp.enabled && base.isEmpty()) {
        throw IllegalStateException(
            "SMTP is enabled but PUBLIC_API_BASE_URL is empty. Set the full public API URL " +
                "(scheme, host, and port if not 80/443), e.g. http://203.0.113.1:8080 or https://api.example.com"
        )
    }
    if (base.isEmpty()) return
    val uri = try {
        URI(base)
    } catch (e: URISyntaxException) {
        throw IllegalStateException("PUBLIC_API_BASE_URL is not a valid URL: ${e.reason ?: e.message}", e)
    }
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") {
        throw IllegalStateException(
            "PUBLIC_API_BASE_URL must use http or https scheme, got: ${uri.scheme ?: "(missing)"}"
        )
    }
    if (uri.host.isNullOrBlank()) {
        throw IllegalStateException("PUBLIC_API_BASE_URL must include a host (e.g. https://api.example.com)")
    }
    if (smtp.enabled && scheme == "http" && uri.port < 0 && !shouldInferHttpPortForHost(uri.host)) {
        throw IllegalStateException(
            "PUBLIC_API_BASE_URL uses http:// without an explicit port for hostname \"${uri.host}\". " +
                "Browsers default to port 80. If the API is not on 80, set the port explicitly " +
                "(e.g. http://${uri.host}:8080) or use https behind a reverse proxy."
        )
    }
}
