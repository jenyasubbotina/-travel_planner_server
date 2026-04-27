package com.travelplanner.domain.repository

import com.travelplanner.domain.model.ItineraryPoint
import com.travelplanner.domain.model.ItineraryPointComment
import com.travelplanner.domain.model.ItineraryPointLink
import java.time.Instant
import java.util.UUID

interface ItineraryRepository {
    suspend fun findByTrip(tripId: UUID): List<ItineraryPoint>
    suspend fun findById(id: UUID): ItineraryPoint?
    suspend fun create(point: ItineraryPoint): ItineraryPoint
    suspend fun update(point: ItineraryPoint): ItineraryPoint
    suspend fun softDelete(id: UUID, deletedAt: Instant): Boolean
    suspend fun updateSortOrders(updates: List<Pair<UUID, Int>>)
    suspend fun findModifiedAfter(tripId: UUID, after: Instant): List<ItineraryPoint>

    suspend fun findLinks(pointId: UUID): List<ItineraryPointLink>
    suspend fun findLink(linkId: UUID, pointId: UUID): ItineraryPointLink?
    suspend fun addLink(link: ItineraryPointLink): ItineraryPointLink
    suspend fun deleteLink(linkId: UUID, pointId: UUID): Boolean
    suspend fun nextLinkSortOrder(pointId: UUID): Int

    suspend fun findComments(pointId: UUID, limit: Int, offset: Int): List<ItineraryPointComment>
    suspend fun addComment(comment: ItineraryPointComment): ItineraryPointComment
}
