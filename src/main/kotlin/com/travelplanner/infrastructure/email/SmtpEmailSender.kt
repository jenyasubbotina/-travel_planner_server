package com.travelplanner.infrastructure.email

import com.travelplanner.infrastructure.config.SmtpConfig
import com.travelplanner.infrastructure.config.SmtpTlsMode
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.Properties

class SmtpEmailSender(
    private val config: SmtpConfig
) : EmailSender {

    private val log = LoggerFactory.getLogger(SmtpEmailSender::class.java)

    init {
        require(config.host.isNotBlank()) { "smtp.host must be set when SMTP is enabled" }
        require(config.username.isNotBlank()) { "smtp.username must be set when SMTP is enabled" }
        require(config.fromAddress.isNotBlank()) { "smtp.fromAddress must be set when SMTP is enabled" }
    }

    private val session: Session by lazy {
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.host", config.host)
            put("mail.smtp.port", config.port.toString())
            when (config.tlsMode) {
                SmtpTlsMode.STARTTLS -> {
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.smtp.starttls.required", "true")
                }
                SmtpTlsMode.SSL -> put("mail.smtp.ssl.enable", "true")
            }
        }
        Session.getInstance(
            props,
            object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication =
                    PasswordAuthentication(config.username, config.password)
            }
        )
    }

    override suspend fun sendHtml(to: String, subject: String, htmlBody: String) {
        withContext(Dispatchers.IO) {
            val msg = MimeMessage(session).apply {
                setFrom(InternetAddress(config.fromAddress))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                setSubject(subject, Charsets.UTF_8.name())
                setContent(htmlBody, "text/html; charset=UTF-8")
            }
            Transport.send(msg)
            log.debug("Sent email to {}", to)
        }
    }
}
