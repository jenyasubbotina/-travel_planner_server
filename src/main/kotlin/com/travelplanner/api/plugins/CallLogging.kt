package com.travelplanner.api.plugins

import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.path
import org.slf4j.event.Level
import java.util.UUID

private val ADMIN_POLLING_PATHS = setOf(
    "/admin/api/server-info",
    "/admin/api/metrics",
    "/admin/api/metrics/prometheus",
)

fun Application.configureCallLogging() {
    install(CallId) {
        header(HttpHeaders.XRequestId)
        generate { UUID.randomUUID().toString() }
    }
    install(CallLogging) {
        callIdMdc("requestId")
        level = Level.INFO

        filter { call ->
            if (call.request.path() !in ADMIN_POLLING_PATHS) return@filter true
            val status = call.response.status()?.value ?: 0
            status >= 400
        }
    }
}
