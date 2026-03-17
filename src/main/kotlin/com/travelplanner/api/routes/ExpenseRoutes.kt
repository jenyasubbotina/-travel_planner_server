package com.travelplanner.api.routes

import com.travelplanner.api.dto.request.CreateExpenseRequest
import com.travelplanner.api.dto.request.UpdateExpenseRequest
import com.travelplanner.api.dto.response.ExpenseResponse
import com.travelplanner.api.dto.response.ExpenseSplitResponse
import com.travelplanner.api.middleware.currentUserId
import com.travelplanner.api.middleware.requireTripParticipant
import com.travelplanner.api.middleware.tripIdParam
import com.travelplanner.api.middleware.uuidParam
import com.travelplanner.application.usecase.expense.CreateExpenseUseCase
import com.travelplanner.application.usecase.expense.DeleteExpenseUseCase
import com.travelplanner.application.usecase.expense.ListExpensesUseCase
import com.travelplanner.application.usecase.expense.UpdateExpenseUseCase
import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.Expense
import com.travelplanner.domain.model.ExpenseSplit
import com.travelplanner.domain.model.SplitType
import com.travelplanner.domain.repository.ExpenseRepository
import com.travelplanner.domain.repository.ParticipantRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

fun Route.expenseRoutes() {
    val createExpenseUseCase by inject<CreateExpenseUseCase>()
    val updateExpenseUseCase by inject<UpdateExpenseUseCase>()
    val listExpensesUseCase by inject<ListExpensesUseCase>()
    val deleteExpenseUseCase by inject<DeleteExpenseUseCase>()
    val expenseRepository by inject<ExpenseRepository>()
    val participantRepository by inject<ParticipantRepository>()

    authenticate("auth-jwt") {
        route("/api/v1/trips/{tripId}/expenses") {
            get {
                val tripId = tripIdParam()
                val userId = currentUserId()

                val category = call.request.queryParameters["category"]
                val payerUserId = call.request.queryParameters["payerUserId"]?.let { UUID.fromString(it) }
                val dateFrom = call.request.queryParameters["dateFrom"]?.let { LocalDate.parse(it) }
                val dateTo = call.request.queryParameters["dateTo"]?.let { LocalDate.parse(it) }

                val expenses = listExpensesUseCase.execute(
                    ListExpensesUseCase.Input(
                        tripId = tripId,
                        userId = userId,
                        category = category,
                        payerUserId = payerUserId,
                        dateFrom = dateFrom,
                        dateTo = dateTo
                    )
                )

                val responses = expenses.map { expense ->
                    val splits = expenseRepository.findSplitsByExpense(expense.id)
                    expense.toResponse(splits)
                }
                call.respond(HttpStatusCode.OK, responses)
            }

            post {
                val tripId = tripIdParam()
                val userId = currentUserId()
                val req = call.receive<CreateExpenseRequest>()
                val result = createExpenseUseCase.execute(
                    CreateExpenseUseCase.Input(
                        tripId = tripId,
                        userId = userId,
                        title = req.title,
                        description = req.description,
                        amount = BigDecimal(req.amount),
                        currency = req.currency,
                        category = req.category,
                        expenseDate = LocalDate.parse(req.expenseDate),
                        splitType = SplitType.valueOf(req.splitType),
                        payerUserId = UUID.fromString(req.payerUserId),
                        splits = req.splits.map { s ->
                            CreateExpenseUseCase.SplitInputData(
                                participantUserId = UUID.fromString(s.participantUserId),
                                value = BigDecimal(s.value)
                            )
                        }
                    )
                )
                call.respond(HttpStatusCode.Created, result.expense.toResponse(result.splits))
            }

            route("/{expenseId}") {
                get {
                    val tripId = tripIdParam()
                    val userId = currentUserId()
                    val expenseId = uuidParam("expenseId")
                    requireTripParticipant(participantRepository, tripId, userId)
                    val expense = expenseRepository.findById(expenseId)
                        ?: throw DomainException.ExpenseNotFound(expenseId)
                    val splits = expenseRepository.findSplitsByExpense(expenseId)
                    call.respond(HttpStatusCode.OK, expense.toResponse(splits))
                }

                patch {
                    val tripId = tripIdParam()
                    val userId = currentUserId()
                    val expenseId = uuidParam("expenseId")
                    val req = call.receive<UpdateExpenseRequest>()
                    val result = updateExpenseUseCase.execute(
                        UpdateExpenseUseCase.Input(
                            expenseId = expenseId,
                            userId = userId,
                            title = req.title,
                            description = req.description,
                            amount = req.amount?.let { BigDecimal(it) },
                            currency = req.currency,
                            category = req.category,
                            expenseDate = req.expenseDate?.let { LocalDate.parse(it) },
                            splitType = req.splitType?.let { SplitType.valueOf(it) },
                            payerUserId = req.payerUserId?.let { UUID.fromString(it) },
                            splits = req.splits?.map { s ->
                                CreateExpenseUseCase.SplitInputData(
                                    participantUserId = UUID.fromString(s.participantUserId),
                                    value = BigDecimal(s.value)
                                )
                            },
                            expectedVersion = req.expectedVersion ?: 0L
                        )
                    )
                    call.respond(HttpStatusCode.OK, result.expense.toResponse(result.splits))
                }

                delete {
                    val tripId = tripIdParam()
                    val userId = currentUserId()
                    val expenseId = uuidParam("expenseId")
                    deleteExpenseUseCase.execute(
                        DeleteExpenseUseCase.Input(
                            expenseId = expenseId,
                            tripId = tripId,
                            userId = userId
                        )
                    )
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}

private fun Expense.toResponse(splits: List<ExpenseSplit>) = ExpenseResponse(
    id = id.toString(),
    tripId = tripId.toString(),
    payerUserId = payerUserId.toString(),
    title = title,
    description = description,
    amount = amount.toPlainString(),
    currency = currency,
    category = category,
    expenseDate = expenseDate.toString(),
    splitType = splitType.name,
    splits = splits.map { it.toResponse() },
    createdBy = createdBy.toString(),
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    version = version,
    deletedAt = deletedAt?.toString()
)

private fun ExpenseSplit.toResponse() = ExpenseSplitResponse(
    id = id.toString(),
    participantUserId = participantUserId.toString(),
    shareType = shareType.name,
    value = value.toPlainString(),
    amountInExpenseCurrency = amountInExpenseCurrency.toPlainString()
)
