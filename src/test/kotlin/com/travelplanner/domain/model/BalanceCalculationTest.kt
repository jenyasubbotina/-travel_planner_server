package com.travelplanner.domain.model

import com.travelplanner.application.usecase.analytics.CalculateBalancesUseCase
import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.repository.ExpenseRepository
import com.travelplanner.domain.repository.ParticipantRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BalanceCalculationTest {

    private val expenseRepo = mockk<ExpenseRepository>()
    private val participantRepo = mockk<ParticipantRepository>()
    private val useCase = CalculateBalancesUseCase(expenseRepo, participantRepo)

    private fun makeParticipant(tripId: UUID, userId: UUID, role: TripRole = TripRole.EDITOR) =
        TripParticipant(tripId = tripId, userId = userId, role = role, joinedAt = Instant.now())

    private fun makeExpense(
        id: UUID,
        tripId: UUID,
        payerId: UUID,
        amount: String,
        category: String = "FOOD",
        deletedAt: Instant? = null
    ) = Expense(
        id = id,
        tripId = tripId,
        payerUserId = payerId,
        title = "Expense",
        amount = BigDecimal(amount),
        currency = "USD",
        category = category,
        expenseDate = LocalDate.now(),
        splitType = SplitType.EQUAL,
        createdBy = payerId,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        deletedAt = deletedAt
    )

    private fun makeSplit(
        expenseId: UUID,
        participantId: UUID,
        amount: String
    ) = ExpenseSplit(
        id = UUID.randomUUID(),
        expenseId = expenseId,
        participantUserId = participantId,
        shareType = SplitType.EQUAL,
        value = BigDecimal("1"),
        amountInExpenseCurrency = BigDecimal(amount)
    )

    @Test
    fun `calculates correct balances for simple scenario`() = runBlocking {
        val tripId = UUID.randomUUID()
        val alice = UUID.randomUUID()
        val bob = UUID.randomUUID()

        coEvery { participantRepo.isParticipant(tripId, any()) } returns true
        coEvery { participantRepo.findByTrip(tripId) } returns listOf(
            makeParticipant(tripId, alice, TripRole.OWNER),
            makeParticipant(tripId, bob)
        )

        val expId = UUID.randomUUID()
        coEvery { expenseRepo.findByTrip(tripId) } returns listOf(
            makeExpense(expId, tripId, alice, "200.00")
        )
        coEvery { expenseRepo.findSplitsByTrip(tripId) } returns listOf(
            makeSplit(expId, alice, "100.00"),
            makeSplit(expId, bob, "100.00")
        )

        val balances = useCase.execute(tripId, alice)

        assertEquals(2, balances.size)
        val aliceBalance = balances.first { it.userId == alice }
        val bobBalance = balances.first { it.userId == bob }

        // Alice paid 200, owes 100 -> net +100
        assertEquals(0, BigDecimal("200.00").compareTo(aliceBalance.totalPaid))
        assertEquals(0, BigDecimal("100.00").compareTo(aliceBalance.totalOwed))
        assertEquals(0, BigDecimal("100.00").compareTo(aliceBalance.netBalance))

        // Bob paid 0, owes 100 -> net -100
        assertEquals(0, BigDecimal.ZERO.compareTo(bobBalance.totalPaid))
        assertEquals(0, BigDecimal("100.00").compareTo(bobBalance.totalOwed))
        assertEquals(0, BigDecimal("-100.00").compareTo(bobBalance.netBalance))
    }

    @Test
    fun `balances with multiple expenses from different payers`() = runBlocking {
        val tripId = UUID.randomUUID()
        val alice = UUID.randomUUID()
        val bob = UUID.randomUUID()
        val charlie = UUID.randomUUID()

        coEvery { participantRepo.isParticipant(tripId, any()) } returns true
        coEvery { participantRepo.findByTrip(tripId) } returns listOf(
            makeParticipant(tripId, alice, TripRole.OWNER),
            makeParticipant(tripId, bob),
            makeParticipant(tripId, charlie)
        )

        val exp1 = UUID.randomUUID()
        val exp2 = UUID.randomUUID()
        coEvery { expenseRepo.findByTrip(tripId) } returns listOf(
            makeExpense(exp1, tripId, alice, "300.00"),
            makeExpense(exp2, tripId, bob, "150.00")
        )
        coEvery { expenseRepo.findSplitsByTrip(tripId) } returns listOf(
            makeSplit(exp1, alice, "100.00"),
            makeSplit(exp1, bob, "100.00"),
            makeSplit(exp1, charlie, "100.00"),
            makeSplit(exp2, alice, "50.00"),
            makeSplit(exp2, bob, "50.00"),
            makeSplit(exp2, charlie, "50.00")
        )

        val balances = useCase.execute(tripId, alice)

        assertEquals(3, balances.size)

        val aliceBalance = balances.first { it.userId == alice }
        // Alice paid 300, owes (100 + 50) = 150 -> net +150
        assertEquals(0, BigDecimal("300.00").compareTo(aliceBalance.totalPaid))
        assertEquals(0, BigDecimal("150.00").compareTo(aliceBalance.totalOwed))
        assertEquals(0, BigDecimal("150.00").compareTo(aliceBalance.netBalance))

        val bobBalance = balances.first { it.userId == bob }
        // Bob paid 150, owes (100 + 50) = 150 -> net 0
        assertEquals(0, BigDecimal("150.00").compareTo(bobBalance.totalPaid))
        assertEquals(0, BigDecimal("150.00").compareTo(bobBalance.totalOwed))
        assertEquals(0, BigDecimal.ZERO.compareTo(bobBalance.netBalance))

        val charlieBalance = balances.first { it.userId == charlie }
        // Charlie paid 0, owes (100 + 50) = 150 -> net -150
        assertEquals(0, BigDecimal.ZERO.compareTo(charlieBalance.totalPaid))
        assertEquals(0, BigDecimal("150.00").compareTo(charlieBalance.totalOwed))
        assertEquals(0, BigDecimal("-150.00").compareTo(charlieBalance.netBalance))
    }

    @Test
    fun `no expenses produces zero balances`() = runBlocking {
        val tripId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        coEvery { participantRepo.isParticipant(tripId, any()) } returns true
        coEvery { participantRepo.findByTrip(tripId) } returns listOf(
            makeParticipant(tripId, userId, TripRole.OWNER)
        )
        coEvery { expenseRepo.findByTrip(tripId) } returns emptyList()
        coEvery { expenseRepo.findSplitsByTrip(tripId) } returns emptyList()

        val balances = useCase.execute(tripId, userId)

        assertEquals(1, balances.size)
        val balance = balances[0]
        assertEquals(0, BigDecimal.ZERO.compareTo(balance.totalPaid))
        assertEquals(0, BigDecimal.ZERO.compareTo(balance.totalOwed))
        assertEquals(0, BigDecimal.ZERO.compareTo(balance.netBalance))
    }

    @Test
    fun `deleted expenses are excluded from balances`() = runBlocking {
        val tripId = UUID.randomUUID()
        val alice = UUID.randomUUID()
        val bob = UUID.randomUUID()

        coEvery { participantRepo.isParticipant(tripId, any()) } returns true
        coEvery { participantRepo.findByTrip(tripId) } returns listOf(
            makeParticipant(tripId, alice, TripRole.OWNER),
            makeParticipant(tripId, bob)
        )

        val expActive = UUID.randomUUID()
        val expDeleted = UUID.randomUUID()
        coEvery { expenseRepo.findByTrip(tripId) } returns listOf(
            makeExpense(expActive, tripId, alice, "100.00"),
            makeExpense(expDeleted, tripId, alice, "500.00", deletedAt = Instant.now())
        )
        coEvery { expenseRepo.findSplitsByTrip(tripId) } returns listOf(
            makeSplit(expActive, alice, "50.00"),
            makeSplit(expActive, bob, "50.00"),
            makeSplit(expDeleted, alice, "250.00"),
            makeSplit(expDeleted, bob, "250.00")
        )

        val balances = useCase.execute(tripId, alice)

        val aliceBalance = balances.first { it.userId == alice }
        // Only active expense: Alice paid 100, owes 50 -> net +50
        assertEquals(0, BigDecimal("100.00").compareTo(aliceBalance.totalPaid))
        assertEquals(0, BigDecimal("50.00").compareTo(aliceBalance.totalOwed))
        assertEquals(0, BigDecimal("50.00").compareTo(aliceBalance.netBalance))
    }

    @Test
    fun `non-participant cannot access balances`() = runBlocking {
        val tripId = UUID.randomUUID()
        val outsider = UUID.randomUUID()

        coEvery { participantRepo.isParticipant(tripId, outsider) } returns false

        assertThrows<DomainException.AccessDenied> {
            useCase.execute(tripId, outsider)
        }
    }

    @Test
    fun `all balances net to zero`() = runBlocking {
        val tripId = UUID.randomUUID()
        val alice = UUID.randomUUID()
        val bob = UUID.randomUUID()
        val charlie = UUID.randomUUID()

        coEvery { participantRepo.isParticipant(tripId, any()) } returns true
        coEvery { participantRepo.findByTrip(tripId) } returns listOf(
            makeParticipant(tripId, alice, TripRole.OWNER),
            makeParticipant(tripId, bob),
            makeParticipant(tripId, charlie)
        )

        val exp1 = UUID.randomUUID()
        val exp2 = UUID.randomUUID()
        coEvery { expenseRepo.findByTrip(tripId) } returns listOf(
            makeExpense(exp1, tripId, alice, "120.00"),
            makeExpense(exp2, tripId, bob, "60.00")
        )
        coEvery { expenseRepo.findSplitsByTrip(tripId) } returns listOf(
            makeSplit(exp1, alice, "40.00"),
            makeSplit(exp1, bob, "40.00"),
            makeSplit(exp1, charlie, "40.00"),
            makeSplit(exp2, alice, "20.00"),
            makeSplit(exp2, bob, "20.00"),
            makeSplit(exp2, charlie, "20.00")
        )

        val balances = useCase.execute(tripId, alice)

        // Sum of all net balances should be zero (conservation of money)
        val totalNet = balances.fold(BigDecimal.ZERO) { acc, b -> acc + b.netBalance }
        assertTrue(BigDecimal.ZERO.compareTo(totalNet) == 0, "Sum of all net balances should be zero")
    }
}
