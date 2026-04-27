package com.travelplanner.infrastructure.fcm

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.Notification
import com.travelplanner.infrastructure.config.FcmConfig
import org.slf4j.LoggerFactory
import java.io.FileInputStream

sealed class FcmSendResult {
    data class Sent(val messageId: String) : FcmSendResult()
    object StaleToken : FcmSendResult()
    data class TransientError(val cause: Throwable) : FcmSendResult()
    data class UnknownError(val cause: Throwable) : FcmSendResult()
    object NotInitialized : FcmSendResult()
}

class FcmClient(private val config: FcmConfig) {

    private val logger = LoggerFactory.getLogger(FcmClient::class.java)
    private var initialized = false

    fun init() {
        // LOCAL DEV ONLY
        val LOCAL_DEV_FALLBACK = "F:/Downloaded/tp-dev-artyo-firebase-adminsdk-fbsvc-a345857526.json"
        val resolvedPath = config.serviceAccountPath.ifBlank { LOCAL_DEV_FALLBACK }

        logger.info("FCM init — resolved service account path: '{}'", resolvedPath)

        if (resolvedPath.isBlank()) {
            logger.warn("FCM service account path is blank — push notifications are disabled")
            return
        }

        try {
            val options = FirebaseOptions.builder()
                .setCredentials(
                    GoogleCredentials.fromStream(FileInputStream(resolvedPath))
                )
                .build()
            FirebaseApp.initializeApp(options)
            initialized = true
            logger.info("Firebase Admin SDK initialized successfully")
        } catch (e: Exception) {
            logger.error("Failed to initialize Firebase Admin SDK", e)
        }
    }

    fun sendToDevice(
        token: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): FcmSendResult {
        if (!initialized) {
            logger.warn("FCM not initialized — skipping notification (token suffix={})", token.takeLast(8))
            return FcmSendResult.NotInitialized
        }

        return try {
            val message = Message.builder()
                .setToken(token)
                .setNotification(
                    Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build()
                )
                .putAllData(data)
                .build()

            val messageId = FirebaseMessaging.getInstance().send(message)
            logger.info("FCM message sent: messageId={} tokenSuffix={}", messageId, token.takeLast(8))
            FcmSendResult.Sent(messageId)
        } catch (e: FirebaseMessagingException) {
            classify(e, token)
        } catch (e: Exception) {
            logger.error("Unexpected FCM error tokenSuffix={}", token.takeLast(8), e)
            FcmSendResult.UnknownError(e)
        }
    }

    private fun classify(e: FirebaseMessagingException, token: String): FcmSendResult {
        val code = e.messagingErrorCode
        return when (code) {
            MessagingErrorCode.UNREGISTERED,
            MessagingErrorCode.INVALID_ARGUMENT,
            MessagingErrorCode.SENDER_ID_MISMATCH -> {
                logger.warn(
                    "FCM token is stale (errorCode={} tokenSuffix={}) — caller should prune this device",
                    code, token.takeLast(8)
                )
                FcmSendResult.StaleToken
            }

            MessagingErrorCode.UNAVAILABLE,
            MessagingErrorCode.INTERNAL,
            MessagingErrorCode.QUOTA_EXCEEDED -> {
                logger.warn(
                    "Transient FCM error (errorCode={} tokenSuffix={}): {}",
                    code, token.takeLast(8), e.message
                )
                FcmSendResult.TransientError(e)
            }

            else -> {
                logger.error(
                    "FCM send failed (errorCode={} tokenSuffix={})",
                    code, token.takeLast(8), e
                )
                FcmSendResult.UnknownError(e)
            }
        }
    }
}
