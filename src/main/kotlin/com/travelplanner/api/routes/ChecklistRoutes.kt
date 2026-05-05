package com.travelplanner.api.routes

import com.travelplanner.api.dto.request.CreateChecklistItemRequest
import com.travelplanner.api.dto.response.ChecklistItemResponse
import com.travelplanner.api.middleware.currentUserId
import com.travelplanner.api.middleware.tripIdParam
import com.travelplanner.api.middleware.uuidParam
import com.travelplanner.application.usecase.checklist.CreateChecklistItemUseCase
import com.travelplanner.application.usecase.checklist.DeleteChecklistItemUseCase
import com.travelplanner.application.usecase.checklist.ListChecklistItemsUseCase
import com.travelplanner.application.usecase.checklist.ToggleChecklistCompletionUseCase
import com.travelplanner.domain.model.ChecklistItem
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject
import java.util.UUID

fun Route.checklistRoutes() {
    val listChecklistItemsUseCase by inject<ListChecklistItemsUseCase>()
    val createChecklistItemUseCase by inject<CreateChecklistItemUseCase>()
    val deleteChecklistItemUseCase by inject<DeleteChecklistItemUseCase>()
    val toggleChecklistCompletionUseCase by inject<ToggleChecklistCompletionUseCase>()

    authenticate("auth-jwt") {
        route("/api/v1/trips/{tripId}/checklist") {
            get {
                val tripId = tripIdParam()
                val userId = currentUserId()
                val items = listChecklistItemsUseCase.execute(
                    ListChecklistItemsUseCase.Input(tripId = tripId, userId = userId)
                )
                call.respond(HttpStatusCode.OK, items.map { it.toResponse() })
            }

            post {
                val tripId = tripIdParam()
                val userId = currentUserId()
                val req = call.receive<CreateChecklistItemRequest>()
                val item = createChecklistItemUseCase.execute(
                    CreateChecklistItemUseCase.Input(
                        tripId = tripId,
                        userId = userId,
                        title = req.title,
                        isGroup = req.isGroup,
                        id = req.id?.let(UUID::fromString),
                    )
                )
                call.respond(HttpStatusCode.Created, item.toResponse())
            }

            put("/{itemId}/toggle") {
                val tripId = tripIdParam()
                val userId = currentUserId()
                val itemId = uuidParam("itemId")
                val item = toggleChecklistCompletionUseCase.execute(
                    ToggleChecklistCompletionUseCase.Input(
                        itemId = itemId,
                        tripId = tripId,
                        userId = userId,
                    )
                )
                call.respond(HttpStatusCode.OK, item.toResponse())
            }

            delete("/{itemId}") {
                val tripId = tripIdParam()
                val userId = currentUserId()
                val itemId = uuidParam("itemId")
                deleteChecklistItemUseCase.execute(
                    DeleteChecklistItemUseCase.Input(
                        itemId = itemId,
                        tripId = tripId,
                        userId = userId,
                    )
                )
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun ChecklistItem.toResponse() = ChecklistItemResponse(
    id = id.toString(),
    tripId = tripId.toString(),
    title = title,
    isGroup = isGroup,
    ownerUserId = ownerUserId.toString(),
    completedBy = completedBy.map { it.toString() },
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
)
