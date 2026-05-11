package com.travelplanner.application.usecase.auth

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.User
import com.travelplanner.domain.repository.UserRepository
import com.travelplanner.domain.validation.TripValidator
import com.travelplanner.infrastructure.auth.PasswordHasher
import com.travelplanner.infrastructure.auth.RefreshTokenHasher
import com.travelplanner.infrastructure.config.AppLinksConfig
import com.travelplanner.infrastructure.email.EmailSender
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

class RegisterUseCase(
    private val userRepository: UserRepository,
    private val emailSender: EmailSender,
    private val refreshTokenHasher: RefreshTokenHasher,
    private val appLinks: AppLinksConfig
) {

    data class Input(val email: String, val displayName: String, val password: String)
    data class Output(val message: String, val user: User)

    suspend fun execute(input: Input): Output {
        TripValidator.validateEmail(input.email)
        if (input.password.length < 8) {
            throw DomainException.ValidationError("Password must be at least 8 characters")
        }
        if (input.displayName.isBlank()) {
            throw DomainException.ValidationError("Display name is required")
        }

        val baseUrl = appLinks.publicApiBaseUrl.trim().removeSuffix("/")
        if (baseUrl.isEmpty()) {
            throw DomainException.ValidationError("PUBLIC_API_BASE_URL is not configured")
        }

        val existing = userRepository.findByEmail(input.email.lowercase().trim())
        if (existing != null) {
            throw DomainException.EmailAlreadyExists(input.email)
        }

        val now = Instant.now()
        val user = User(
            id = UUID.randomUUID(),
            email = input.email.lowercase().trim(),
            displayName = input.displayName.trim(),
            passwordHash = PasswordHasher.hash(input.password),
            emailVerifiedAt = null,
            emailVerificationExpiresAt = null,
            createdAt = now,
            updatedAt = now
        )
        val created = userRepository.create(user)

        val rawToken = generateRawToken()
        val tokenHash = refreshTokenHasher.hash(rawToken)
        val expiresAt = now.plus(VERIFY_TOKEN_TTL_HOURS, ChronoUnit.HOURS)
        userRepository.setEmailVerificationToken(created.id, tokenHash, expiresAt)

        val verifyPath = "/api/v1/auth/verify-email?token=" +
            URLEncoder.encode(rawToken, StandardCharsets.UTF_8)
        val verifyUrl = baseUrl + verifyPath

        val subject = "Подтвердите email"
        val html = """
            <p>Здравствуйте, ${escapeHtml(created.displayName)}!</p>
            <p>Нажмите кнопку, чтобы подтвердить адрес:</p>
            <p><a href="$verifyUrl" style="display:inline-block;padding:10px 16px;background:#2563eb;color:#fff;text-decoration:none;border-radius:6px;">Подтвердить email</a></p>
            <p>Или откройте ссылку: <a href="$verifyUrl">$verifyUrl</a></p>
        """.trimIndent()

        emailSender.sendHtml(to = created.email, subject = subject, htmlBody = html)

        return Output(
            message = "Check your email to verify your address before signing in.",
            user = created
        )
    }

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun generateRawToken(): String {
        val bytes = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        private const val VERIFY_TOKEN_TTL_HOURS = 48L
    }
}
