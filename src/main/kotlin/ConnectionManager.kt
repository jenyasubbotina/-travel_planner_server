package com.example


import com.example.models.TripEvent
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ConnectionManager {
    private val userSessions = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()

    fun onConnect(userId: String, session: WebSocketSession) {
        val sessions = userSessions.computeIfAbsent(userId) {
            Collections.synchronizedSet(LinkedHashSet())
        }
        sessions.add(session)
        println("User $userId connected. Sessions: ${sessions.size}. Total users online: ${userSessions.size}")
    }

    fun onDisconnect(userId: String, session: WebSocketSession) {
        val sessions = userSessions[userId] ?: return
        sessions.remove(session)
        if (sessions.isEmpty()) {
            userSessions.remove(userId)
            println("User $userId disconnected (last device). Total users online: ${userSessions.size}")
        } else {
            println("User $userId closed 1 device. Remaining: ${sessions.size}")
        }
    }

    suspend fun broadcastToTrip(tripId: Long, event: TripEvent, tripRepository: TripRepository) {
        val participantUserIds = tripRepository.getParticipantUserIdsForTrip(tripId)
        val jsonMessage = Json.encodeToString(event)
        participantUserIds.forEach { userId ->
            userSessions[userId]?.toList()?.forEach { session ->
                try {
                    session.send(Frame.Text(jsonMessage))
                } catch (e: Exception) {
                }
            }
        }
    }
}