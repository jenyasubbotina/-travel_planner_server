package com.travelplanner.infrastructure.fcm

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import com.travelplanner.infrastructure.config.FcmConfig
import org.slf4j.LoggerFactory
import java.io.FileInputStream

class FcmClient(private val config: FcmConfig) {

    private val logger = LoggerFactory.getLogger(FcmClient::class.java)
    private var initialized = false

    fun init() {
        if (config.serviceAccountPath.isBlank()) {
            logger.warn("FCM service account path is blank — push notifications are disabled")
            return
        }

        try {
            val options = FirebaseOptions.builder()
                .setCredentials(
                    GoogleCredentials.fromStream(FileInputStream(config.serviceAccountPath))
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
    ) {
        if (!initialized) {
            logger.debug("FCM not initialized — skipping notification to token={}", token)
            return
        }

        try {
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
            logger.debug("FCM message sent: messageId={}, token={}", messageId, token)
        } catch (e: Exception) {
            logger.error("Failed to send FCM message to token={}", token, e)
        }
    }
}
