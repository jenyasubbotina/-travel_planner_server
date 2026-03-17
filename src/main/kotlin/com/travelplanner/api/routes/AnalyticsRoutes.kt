package com.travelplanner.api.routes

import com.travelplanner.api.dto.response.BalanceResponse
import com.travelplanner.api.dto.response.SettlementResponse
import com.travelplanner.api.dto.response.StatisticsResponse
import com.travelplanner.api.middleware.currentUserId
import com.travelplanner.api.middleware.tripIdParam
import com.travelplanner.application.usecase.analytics.CalculateBalancesUseCase
import com.travelplanner.application.usecase.analytics.CalculateSettlementsUseCase
import com.travelplanner.application.usecase.analytics.GetStatisticsUseCase
import com.travelplanner.domain.model.ParticipantBalance
import com.travelplanner.domain.model.Settlement
import com.travelplanner.domain.model.TripStatistics
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Route.analyticsRoutes() {
    val calculateBalancesUseCase by inject<CalculateBalancesUseCase>()
    val calculateSettlementsUseCase by inject<CalculateSettlementsUseCase>()
    val getStatisticsUseCase by inject<GetStatisticsUseCase>()

    authenticate("auth-jwt") {
        route("/api/v1/trips/{tripId}") {
            get("/balances") {
                val tripId = tripIdParam()
                val userId = currentUserId()
                val balances = calculateBalancesUseCase.execute(tripId, userId)
                call.respond(HttpStatusCode.OK, balances.map { it.toResponse() })
            }

            get("/settlements") {
                val tripId = tripIdParam()
                val userId = currentUserId()
                val settlements = calculateSettlementsUseCase.execute(tripId, userId)
                call.respond(HttpStatusCode.OK, settlements.map { it.toResponse() })
            }

            get("/statistics") {
                val tripId = tripIdParam()
                val userId = currentUserId()
                val statistics = getStatisticsUseCase.execute(tripId, userId)
                call.respond(HttpStatusCode.OK, statistics.toResponse())
            }
        }
    }
}

private fun ParticipantBalance.toResponse() = BalanceResponse(
    userId = userId.toString(),
    totalPaid = totalPaid.toPlainString(),
    totalOwed = totalOwed.toPlainString(),
    netBalance = netBalance.toPlainString()
)

private fun Settlement.toResponse() = SettlementResponse(
    fromUserId = fromUserId.toString(),
    toUserId = toUserId.toString(),
    amount = amount.toPlainString(),
    currency = currency
)

private fun TripStatistics.toResponse() = StatisticsResponse(
    totalSpent = totalSpent.toPlainString(),
    currency = currency,
    spentByCategory = spentByCategory.mapValues { (_, v) -> v.toPlainString() },
    spentByParticipant = spentByParticipant.map { (k, v) -> k.toString() to v.toPlainString() }.toMap(),
    spentByDay = spentByDay.mapValues { (_, v) -> v.toPlainString() }
)
