package com.travelplanner.domain.model

import com.travelplanner.application.usecase.analytics.CalculateSettlementsUseCase
import com.travelplanner.domain.repository.ExpenseRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TripRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettlementCalculationTest {

    private val expenseRepo = mockk<ExpenseRepository>()
    private val participantRepo = mockk<ParticipantRepository>()
    private val tripRepo = mockk<TripRepository>()
    private val useCase = CalculateSettlementsUseCase(expenseRepo, participantRepo, tripRepo)

    private fun makeTrip(tripId: UUID, createdBy: UUID) = Trip(
        id = tripId,
        title = "Test Trip",
        baseCurrency = "USD",
        status = TripStatus.ACTIVE,
        createdBy = createdBy,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    private fun makeParticipant(tripId: UUID, userId: UUID, role: TripRole = TripRole.EDITOR) =
        TripParticipant(tripId = tripId, userId = userId, role = role, joinedAt = Instant.now())

    private fun makeExpense(
        id: UUID,
        tripId: UUID,
        payerId: UUID,
        amount: String,
        category: String = "FOOD"
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
        updatedAt = Instant.now()
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
    fun `simple equal split with one payer`() = runBlocking {
        val tripId = UUID.randomUUID()
        val alice = UUID.randomUUID()
        val bob = UUID.randomUUID()
        val charlie = UUID.randomUUID()

        coEvery { tripRepo.findById(tripId) } returns makeTrip(tripId, alice)
        coEvery { participantRepo.isParticipant(tripId, any()) } returns true
        coEvery { participantRepo.findByTrip(tripId) } returns listOf(
            makeParticipant(tripId, alice, TripRole.OWNER),
            makeParticipant(tripId, bob),
            makeParticipant(tripId, charlie)
        )

        val expenseId = UUID.randomUUID()
        coEvery { expenseRepo.findByTrip(tripId) } returns listOf(
            makeExpense(expenseId, tripId, alice, "300.00")
        )
        coEvery { expenseRepo.findSplitsByTrip(tripId) } returns listOf(
            makeSplit(expenseId, alice, "100.00"),
            makeSplit(expenseId, bob, "100.00"),
            makeSplit(expenseId, charlie, "100.00")
        )

        val settlements = useCase.execute(tripId, alice)

        // Alice paid 300, owes 100 -> net +200
        // Bob paid 0, owes 100 -> net -100
        // Charlie paid 0, owes 100 -> net -100
        // Settlements: Bob -> Alice 100, Charlie -> Alice 100
        assertEquals(2, settlements.size)
        val totalToAlice = settlements.filter { it.toUserId == alice }.sumOf { it.amount }
        assertEquals(0, BigDecimal("200.00").compareTo(totalToAlice))
    }

    @Test
    fun `multiple payers with complex splits`() = runBlocking {
        val tripId = UUID.randomUUID()
        val alice = UUID.randomUUID()
        val bob = UUID.randomUUID()

        coEvery { tripRepo.findById(tripId) } returns makeTrip(tripId, alice)
        coEvery { participantRepo.isParticipant(tripId, any()) } returns true
        coEvery { participantRepo.findByTrip(tripId) } returns listOf(
            makeParticipant(tripId, alice, TripRole.OWNER),
            makeParticipant(tripId, bob)
        )

        val exp1 = UUID.randomUUID()
        val exp2 = UUID.randomUUID()
        coEvery { expenseRepo.findByTrip(tripId) } returns listOf(
            makeExpense(exp1, tripId, alice, "100.00"),
            makeExpense(exp2, tripId, bob, "60.00", "TRANSPORT")
        )

        coEvery { expenseRepo.findSplitsByTrip(tripId) } returns listOf(
            makeSplit(exp1, alice, "50.00"),
            makeSplit(exp1, bob, "50.00"),
            makeSplit(exp2, alice, "30.00"),
            makeSplit(exp2, bob, "30.00")
        )

        val settlements = useCase.execute(tripId, alice)

        // Alice paid 100, owes (50 + 30) = 80 -> net +20
        // Bob paid 60, owes (50 + 30) = 80 -> net -20
        // Settlement: Bob -> Alice 20
        assertEquals(1, settlements.size)
        assertEquals(bob, settlements[0].fromUserId)
        assertEquals(alice, settlements[0].toUserId)
        assertEquals(0, BigDecimal("20.00").compareTo(settlements[0].amount))
    }

    @Test
    fun `no expenses means no settlements`() = runBlocking {
        val tripId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        coEvery { tripRepo.findById(tripId) } returns makeTrip(tripId, userId)
        coEvery { participantRepo.isParticipant(tripId, any()) } returns true
        coEvery { participantRepo.findByTrip(tripId) } returns listOf(
            makeParticipant(tripId, userId, TripRole.OWNER)
        )
        coEvery { expenseRepo.findByTrip(tripId) } returns emptyList()
        coEvery { expenseRepo.findSplitsByTrip(tripId) } returns emptyList()

        val settlements = useCase.execute(tripId, userId)
        assertTrue(settlements.isEmpty())
    }

    @Test
    fun `balanced expenses produce no settlements`() = runBlocking {
        val tripId = UUID.randomUUID()
        val alice = UUID.randomUUID()
        val bob = UUID.randomUUID()

        coEvery { tripRepo.findById(tripId) } returns makeTrip(tripId, alice)
        coEvery { participantRepo.isParticipant(tripId, any()) } returns true
        coEvery { participantRepo.findByTrip(tripId) } returns listOf(
            makeParticipant(tripId, alice, TripRole.OWNER),
            makeParticipant(tripId, bob)
        )

        val exp1 = UUID.randomUUID()
        val exp2 = UUID.randomUUID()
        // Alice pays 100, Bob pays 100, each split 50/50 -> net balances are 0
        coEvery { expenseRepo.findByTrip(tripId) } returns listOf(
            makeExpense(exp1, tripId, alice, "100.00"),
            makeExpense(exp2, tripId, bob, "100.00")
        )
        coEvery { expenseRepo.findSplitsByTrip(tripId) } returns listOf(
            makeSplit(exp1, alice, "50.00"),
            makeSplit(exp1, bob, "50.00"),
            makeSplit(exp2, alice, "50.00"),
            makeSplit(exp2, bob, "50.00")
        )

        val settlements = useCase.execute(tripId, alice)
        assertTrue(settlements.isEmpty())
    }

    @Test
    fun `three-way settlement minimization`() = runBlocking {
        val tripId = UUID.randomUUID()
        val alice = UUID.randomUUID()
        val bob = UUID.randomUUID()
        val charlie = UUID.randomUUID()

        coEvery { tripRepo.findById(tripId) } returns makeTrip(tripId, alice)
        coEvery { participantRepo.isParticipant(tripId, any()) } returns true
        coEvery { participantRepo.findByTrip(tripId) } returns listOf(
            makeParticipant(tripId, alice, TripRole.OWNER),
            makeParticipant(tripId, bob),
            makeParticipant(tripId, charlie)
        )

        // Alice pays 600, split equally (200 each)
        // Bob pays 0
        // Charlie pays 300, split equally (100 each)
        val exp1 = UUID.randomUUID()
        val exp2 = UUID.randomUUID()
        coEvery { expenseRepo.findByTrip(tripId) } returns listOf(
            makeExpense(exp1, tripId, alice, "600.00"),
            makeExpense(exp2, tripId, charlie, "300.00")
        )
        coEvery { expenseRepo.findSplitsByTrip(tripId) } returns listOf(
            makeSplit(exp1, alice, "200.00"),
            makeSplit(exp1, bob, "200.00"),
            makeSplit(exp1, charlie, "200.00"),
            makeSplit(exp2, alice, "100.00"),
            makeSplit(exp2, bob, "100.00"),
            makeSplit(exp2, charlie, "100.00")
        )

        val settlements = useCase.execute(tripId, alice)

        // Net balances:
        // Alice: paid 600, owes 300 -> net +300
        // Bob: paid 0, owes 300 -> net -300
        // Charlie: paid 300, owes 300 -> net 0
        // Settlement: Bob -> Alice 300
        assertEquals(1, settlements.size)
        assertEquals(bob, settlements[0].fromUserId)
        assertEquals(alice, settlements[0].toUserId)
        assertEquals(0, BigDecimal("300.00").compareTo(settlements[0].amount))
    }

    @Test
    fun `deleted expenses are excluded from settlements`() = runBlocking {
        val tripId = UUID.randomUUID()
        val alice = UUID.randomUUID()
        val bob = UUID.randomUUID()

        coEvery { tripRepo.findById(tripId) } returns makeTrip(tripId, alice)
        coEvery { participantRepo.isParticipant(tripId, any()) } returns true
        coEvery { participantRepo.findByTrip(tripId) } returns listOf(
            makeParticipant(tripId, alice, TripRole.OWNER),
            makeParticipant(tripId, bob)
        )

        val expActive = UUID.randomUUID()
        val expDeleted = UUID.randomUUID()
        coEvery { expenseRepo.findByTrip(tripId) } returns listOf(
            makeExpense(expActive, tripId, alice, "100.00"),
            // Deleted expense should be excluded
            Expense(
                id = expDeleted,
                tripId = tripId,
                payerUserId = alice,
                title = "Deleted Expense",
                amount = BigDecimal("500.00"),
                currency = "USD",
                category = "FOOD",
                expenseDate = LocalDate.now(),
                splitType = SplitType.EQUAL,
                createdBy = alice,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                deletedAt = Instant.now()
            )
        )
        coEvery { expenseRepo.findSplitsByTrip(tripId) } returns listOf(
            makeSplit(expActive, alice, "50.00"),
            makeSplit(expActive, bob, "50.00"),
            makeSplit(expDeleted, alice, "250.00"),
            makeSplit(expDeleted, bob, "250.00")
        )

        val settlements = useCase.execute(tripId, alice)

        // Only active expense: Alice paid 100, owes 50 -> net +50; Bob paid 0, owes 50 -> net -50
        assertEquals(1, settlements.size)
        assertEquals(0, BigDecimal("50.00").compareTo(settlements[0].amount))
    }

    @Test
    fun `settlement currency matches trip base currency`() = runBlocking {
        val tripId = UUID.randomUUID()
        val alice = UUID.randomUUID()
        val bob = UUID.randomUUID()

        val trip = Trip(
            id = tripId,
            title = "Euro Trip",
            baseCurrency = "EUR",
            status = TripStatus.ACTIVE,
            createdBy = alice,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        coEvery { tripRepo.findById(tripId) } returns trip
        coEvery { participantRepo.isParticipant(tripId, any()) } returns true
        coEvery { participantRepo.findByTrip(tripId) } returns listOf(
            makeParticipant(tripId, alice, TripRole.OWNER),
            makeParticipant(tripId, bob)
        )

        val expenseId = UUID.randomUUID()
        coEvery { expenseRepo.findByTrip(tripId) } returns listOf(
            makeExpense(expenseId, tripId, alice, "100.00")
        )
        coEvery { expenseRepo.findSplitsByTrip(tripId) } returns listOf(
            makeSplit(expenseId, alice, "50.00"),
            makeSplit(expenseId, bob, "50.00")
        )

        val settlements = useCase.execute(tripId, alice)
        assertEquals(1, settlements.size)
        assertEquals("EUR", settlements[0].currency)
    }
}
