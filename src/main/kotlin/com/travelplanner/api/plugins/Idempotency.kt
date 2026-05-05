package com.travelplanner.api.plugins

import com.travelplanner.domain.repository.IdempotencyRecord
import com.travelplanner.domain.repository.IdempotencyRepository
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.plugins.doublereceive.DoubleReceive
import io.ktor.server.request.contentType
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.util.AttributeKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import org.slf4j.LoggerFactory
import java.util.UUID

private val log = LoggerFactory.getLogger("Idempotency")

private val IdempotencyAttr = AttributeKey<IdempotencyContext>("idempotency.context")

private data class IdempotencyContext(
    val storageKey: String,
    val userId: UUID,
)

class IdempotencyConfig {
    lateinit var repository: IdempotencyRepository
    var json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }
}


val Idempotency = createRouteScopedPlugin(
    name = "Idempotency",
    createConfiguration = ::IdempotencyConfig,
) {
    val repo = pluginConfig.repository
    val json = pluginConfig.json

    onCall { call ->
        val method = call.request.httpMethod
        if (method != HttpMethod.Post && method != HttpMethod.Patch && method != HttpMethod.Delete) {
            return@onCall
        }

        val userId = call.principal<JWTPrincipal>()?.subject?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        } ?: return@onCall

        val rawKey = call.request.headers["Idempotency-Key"]
            ?: tryReadKeyFromBody(call, json)
            ?: return@onCall

        if (rawKey.isBlank() || rawKey.length > 200) return@onCall

        val storageKey = "$userId:$rawKey"
        val cached = try {
            repo.find(storageKey, userId)
        } catch (e: Exception) {
            log.warn("idempotency lookup failed for key={}: {}", rawKey, e.message)
            null
        }

        if (cached != null) {
            replayCachedResponse(call, cached)
            return@onCall
        }

        call.attributes.put(IdempotencyAttr, IdempotencyContext(storageKey, userId))
    }

    onCallRespond { call, body ->
        val ctx = call.attributes.getOrNull(IdempotencyAttr) ?: return@onCallRespond
        val status = call.response.status() ?: HttpStatusCode.OK
        if (status.value !in 200..299) return@onCallRespond

        val responseBody = encodeResponseBody(body, json)
        try {
            repo.save(
                IdempotencyRecord(
                    key = ctx.storageKey,
                    userId = ctx.userId,
                    responseStatus = status.value,
                    responseBody = responseBody,
                )
            )
        } catch (e: Exception) {
            log.warn("idempotency save failed for key={}: {}", ctx.storageKey, e.message)
        }
    }

    on(ResponseSent) { _ ->
    }
}

private suspend fun tryReadKeyFromBody(call: ApplicationCall, json: Json): String? {
    val contentType = call.request.contentType()
    if (!contentType.match(ContentType.Application.Json)) return null
    return try {
        val element = call.receive<JsonObject>()
        (element["clientMutationId"] as? JsonPrimitive)?.contentOrNull
            ?: (element["mutationId"] as? JsonPrimitive)?.contentOrNull
    } catch (e: Exception) {
        null
    }
}

private suspend fun replayCachedResponse(call: ApplicationCall, record: IdempotencyRecord) {
    val status = HttpStatusCode.fromValue(record.responseStatus)
    val body = record.responseBody
    if (body.isNullOrEmpty() || status == HttpStatusCode.NoContent) {
        call.respond(status)
    } else {
        call.respondText(text = body, contentType = ContentType.Application.Json, status = status)
    }
}

@OptIn(kotlinx.serialization.InternalSerializationApi::class, kotlinx.serialization.ExperimentalSerializationApi::class)
private fun encodeResponseBody(body: Any, json: Json): String? {
    if (body is Unit) return null
    return try {
        @Suppress("UNCHECKED_CAST")
        val serializer = body::class.serializer() as kotlinx.serialization.KSerializer<Any>
        json.encodeToString(serializer, body)
    } catch (e: Throwable) {
        log.debug("could not encode response body of type {}: {}", body::class.qualifiedName, e.message)
        null
    }
}
