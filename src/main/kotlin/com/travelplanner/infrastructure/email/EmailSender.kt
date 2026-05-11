package com.travelplanner.infrastructure.email

import org.slf4j.LoggerFactory

/** Sends transactional email (HTML body, e.g. verification link button). */
fun interface EmailSender {
    suspend fun sendHtml(to: String, subject: String, htmlBody: String)
}

class NoOpEmailSender : EmailSender {
    private val log = LoggerFactory.getLogger(NoOpEmailSender::class.java)

    override suspend fun sendHtml(to: String, subject: String, htmlBody: String) {
        log.info("SMTP disabled; skip send to={} subject={}", to, subject)
    }
}
