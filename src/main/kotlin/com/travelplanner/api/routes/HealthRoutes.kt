package com.travelplanner.api.routes

import com.travelplanner.api.dto.response.HealthResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import java.time.Instant

fun Route.healthRoutes() {
    route("/health") {
        get("/live") {
            call.respond(
                HttpStatusCode.OK,
                HealthResponse(
                    status = "UP",
                    timestamp = Instant.now().toString()
                )
            )
        }

        get("/ready") {
            call.respond(
                HttpStatusCode.OK,
                HealthResponse(
                    status = "UP",
                    timestamp = Instant.now().toString()
                )
            )
        }
    }
}
