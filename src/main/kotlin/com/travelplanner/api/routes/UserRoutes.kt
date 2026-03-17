package com.travelplanner.api.routes

import com.travelplanner.api.dto.request.RegisterDeviceRequest
import com.travelplanner.api.dto.response.DeviceResponse
import com.travelplanner.api.dto.response.UserResponse
import com.travelplanner.api.middleware.currentUserId
import com.travelplanner.api.middleware.uuidParam
import com.travelplanner.application.usecase.user.GetProfileUseCase
import com.travelplanner.application.usecase.user.RegisterDeviceUseCase
import com.travelplanner.application.usecase.user.RemoveDeviceUseCase
import com.travelplanner.domain.model.User
import com.travelplanner.domain.model.UserDevice
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Route.userRoutes() {
    val getProfileUseCase by inject<GetProfileUseCase>()
    val registerDeviceUseCase by inject<RegisterDeviceUseCase>()
    val removeDeviceUseCase by inject<RemoveDeviceUseCase>()

    authenticate("auth-jwt") {
        route("/api/v1/me") {
            get {
                val userId = currentUserId()
                val user = getProfileUseCase.execute(userId)
                call.respond(HttpStatusCode.OK, user.toResponse())
            }

            route("/devices") {
                post {
                    val userId = currentUserId()
                    val req = call.receive<RegisterDeviceRequest>()
                    val device = registerDeviceUseCase.execute(
                        RegisterDeviceUseCase.Input(
                            userId = userId,
                            fcmToken = req.fcmToken,
                            deviceName = req.deviceName
                        )
                    )
                    call.respond(HttpStatusCode.Created, device.toResponse())
                }

                delete("/{deviceId}") {
                    val userId = currentUserId()
                    val deviceId = uuidParam("deviceId")
                    removeDeviceUseCase.execute(
                        RemoveDeviceUseCase.Input(
                            deviceId = deviceId,
                            userId = userId
                        )
                    )
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}

private fun User.toResponse() = UserResponse(
    id = id.toString(),
    email = email,
    displayName = displayName,
    avatarUrl = avatarUrl,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString()
)

private fun UserDevice.toResponse() = DeviceResponse(
    id = id.toString(),
    fcmToken = fcmToken,
    deviceName = deviceName,
    createdAt = createdAt.toString()
)
