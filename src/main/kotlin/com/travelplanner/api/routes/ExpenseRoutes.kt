package com.travelplanner.api.routes

import com.travelplanner.api.dto.request.CreateExpenseRequest
import com.travelplanner.api.dto.request.MergeExpenseRequest
import com.travelplanner.api.dto.request.UpdateExpenseRequest
import com.travelplanner.api.dto.response.ExpensePendingUpdateResponse
import com.travelplanner.api.dto.response.ExpenseResponse
import com.travelplanner.api.dto.response.ExpenseSplitResponse
import com.travelplanner.api.middleware.currentUserId
import com.travelplanner.api.middleware.requireTripParticipant
import com.travelplanner.api.middleware.tripIdParam
import com.travelplanner.api.middleware.uuidParam
import com.travelplanner.application.usecase.expense.CreateExpenseUseCase
import com.travelplanner.application.usecase.expense.DeleteExpenseUseCase
import com.travelplanner.application.usecase.expense.ListExpensesUseCase
import com.travelplanner.application.usecase.expense.ResolveExpenseConflictMergeUseCase
import com.travelplanner.application.usecase.expense.ResolveExpenseConflictRevertUseCase
import com.travelplanner.application.usecase.expense.ResolveExpenseConflictUseCase
import com.travelplanner.application.usecase.expense.UpdateExpenseUseCase
import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.Expense
import com.travelplanner.domain.model.ExpensePendingUpdate
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
    val resolveExpenseConflictUseCase by inject<ResolveExpenseConflictUseCase>()
    val resolveExpenseConflictMergeUseCase by inject<ResolveExpenseConflictMergeUseCase>()
    val resolveExpenseConflictRevertUseCase by inject<ResolveExpenseConflictRevertUseCase>()
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

                val pendingByExpense = expenseRepository
                    .findPendingUpdatesByTrip(tripId)
                    .associateBy { it.expenseId }
                val responses = expenses.map { expense ->
                    val splits = expenseRepository.findSplitsByExpense(expense.id)
                    val pending = pendingByExpense[expense.id]
                    val baseSnapshot = pending?.let {
                        expenseRepository.findHistoryAt(expense.id, it.baseVersion)?.snapshot
                    }
                    expense.toResponse(splits, pending, baseSnapshot)
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
                call.respond(HttpStatusCode.Created, result.expense.toResponse(result.splits, null))
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
                    val pending = expenseRepository.findPendingUpdate(expenseId)
                    val baseSnapshot = pending?.let {
                        expenseRepository.findHistoryAt(expenseId, it.baseVersion)?.snapshot
                    }
                    call.respond(HttpStatusCode.OK, expense.toResponse(splits, pending, baseSnapshot))
                }

                post("/resolve") {
                    val tripId = tripIdParam()
                    val userId = currentUserId()
                    val expenseId = uuidParam("expenseId")
                    val accept = call.request.queryParameters["accept"]?.toBoolean() ?: false
                    val result = resolveExpenseConflictUseCase.execute(
                        ResolveExpenseConflictUseCase.Input(
                            tripId = tripId,
                            expenseId = expenseId,
                            resolverUserId = userId,
                            accept = accept,
                        )
                    )
                    call.respond(HttpStatusCode.OK, result.expense.toResponse(result.splits, null))
                }

                post("/resolve-merge") {
                    val tripId = tripIdParam()
                    val userId = currentUserId()
                    val expenseId = uuidParam("expenseId")
                    val req = call.receive<MergeExpenseRequest>()
                    val result = resolveExpenseConflictMergeUseCase.execute(
                        ResolveExpenseConflictMergeUseCase.Input(
                            tripId = tripId,
                            expenseId = expenseId,
                            resolverUserId = userId,
                            title = req.title,
                            description = req.description,
                            amount = req.amount?.let { BigDecimal(it) },
                            currency = req.currency,
                            category = req.category,
                            payerUserId = req.payerUserId?.let { UUID.fromString(it) },
                            expenseDate = req.expenseDate?.let { LocalDate.parse(it) },
                            splitType = req.splitType?.let { SplitType.valueOf(it) },
                            splits = req.splits?.map { s ->
                                CreateExpenseUseCase.SplitInputData(
                                    participantUserId = UUID.fromString(s.participantUserId),
                                    value = BigDecimal(s.value),
                                )
                            },
                        )
                    )
                    call.respond(HttpStatusCode.OK, result.expense.toResponse(result.splits, null))
                }

                post("/resolve-revert") {
                    val tripId = tripIdParam()
                    val userId = currentUserId()
                    val expenseId = uuidParam("expenseId")
                    val result = resolveExpenseConflictRevertUseCase.execute(
                        ResolveExpenseConflictRevertUseCase.Input(
                            tripId = tripId,
                            expenseId = expenseId,
                            resolverUserId = userId,
                        )
                    )
                    call.respond(HttpStatusCode.OK, result.expense.toResponse(result.splits, null))
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
                    call.respond(HttpStatusCode.OK, result.expense.toResponse(result.splits, null))
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

private fun Expense.toResponse(
    splits: List<ExpenseSplit>,
    pending: ExpensePendingUpdate?,
    baseSnapshot: String? = null,
) = ExpenseResponse(
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
    deletedAt = deletedAt?.toString(),
    pendingUpdate = pending?.toResponse(baseSnapshot),
)

private fun ExpensePendingUpdate.toResponse(baseSnapshot: String? = null) = ExpensePendingUpdateResponse(
    expenseId = expenseId.toString(),
    proposedByUserId = proposedByUserId.toString(),
    proposedAt = proposedAt.toString(),
    baseVersion = baseVersion,
    payload = payload,
    baseSnapshot = baseSnapshot,
)

private fun ExpenseSplit.toResponse() = ExpenseSplitResponse(
    id = id.toString(),
    participantUserId = participantUserId.toString(),
    shareType = shareType.name,
    value = value.toPlainString(),
    amountInExpenseCurrency = amountInExpenseCurrency.toPlainString()
)
