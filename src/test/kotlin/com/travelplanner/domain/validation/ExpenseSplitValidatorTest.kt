package com.travelplanner.domain.validation

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.SplitType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals

class ExpenseSplitValidatorTest {

    private val participant1 = UUID.randomUUID()
    private val participant2 = UUID.randomUUID()
    private val participant3 = UUID.randomUUID()
    private val allParticipants = setOf(participant1, participant2, participant3)

    // ───────────────────── EQUAL split ─────────────────────

    @Test
    fun `EQUAL split divides evenly among participants`() {
        val splits = listOf(
            SplitInput(participant1, SplitType.EQUAL, BigDecimal.ZERO),
            SplitInput(participant2, SplitType.EQUAL, BigDecimal.ZERO),
            SplitInput(participant3, SplitType.EQUAL, BigDecimal.ZERO)
        )
        val result = ExpenseSplitValidator.validate(BigDecimal("300.00"), SplitType.EQUAL, splits, allParticipants)
        assertEquals(3, result.size)
        assertEquals(BigDecimal("100.00").setScale(2), result[0].second.setScale(2))
        assertEquals(BigDecimal("100.00").setScale(2), result[1].second.setScale(2))
        assertEquals(BigDecimal("100.00").setScale(2), result[2].second.setScale(2))
    }

    @Test
    fun `EQUAL split handles remainder correctly`() {
        val splits = listOf(
            SplitInput(participant1, SplitType.EQUAL, BigDecimal.ZERO),
            SplitInput(participant2, SplitType.EQUAL, BigDecimal.ZERO),
            SplitInput(participant3, SplitType.EQUAL, BigDecimal.ZERO)
        )
        val result = ExpenseSplitValidator.validate(BigDecimal("100.00"), SplitType.EQUAL, splits, allParticipants)
        // 100 / 3 = 33.33 each, with rounding remainder assigned to first participant
        val total = result.sumOf { it.second }
        assertEquals(0, BigDecimal("100.00").compareTo(total))
    }

    @Test
    fun `EQUAL split with two participants divides correctly`() {
        val twoParticipants = setOf(participant1, participant2)
        val splits = listOf(
            SplitInput(participant1, SplitType.EQUAL, BigDecimal.ZERO),
            SplitInput(participant2, SplitType.EQUAL, BigDecimal.ZERO)
        )
        val result = ExpenseSplitValidator.validate(BigDecimal("100.00"), SplitType.EQUAL, splits, twoParticipants)
        assertEquals(2, result.size)
        assertEquals(0, BigDecimal("50.00").compareTo(result[0].second))
        assertEquals(0, BigDecimal("50.00").compareTo(result[1].second))
    }

    // ───────────────────── PERCENTAGE split ─────────────────────

    @Test
    fun `PERCENTAGE split must sum to 100`() {
        val splits = listOf(
            SplitInput(participant1, SplitType.PERCENTAGE, BigDecimal("50")),
            SplitInput(participant2, SplitType.PERCENTAGE, BigDecimal("30"))
        )
        assertThrows<DomainException.InvalidSplitSum> {
            ExpenseSplitValidator.validate(BigDecimal("100.00"), SplitType.PERCENTAGE, splits, allParticipants)
        }
    }

    @Test
    fun `PERCENTAGE split calculates amounts correctly`() {
        val splits = listOf(
            SplitInput(participant1, SplitType.PERCENTAGE, BigDecimal("50")),
            SplitInput(participant2, SplitType.PERCENTAGE, BigDecimal("30")),
            SplitInput(participant3, SplitType.PERCENTAGE, BigDecimal("20"))
        )
        val result = ExpenseSplitValidator.validate(BigDecimal("200.00"), SplitType.PERCENTAGE, splits, allParticipants)
        assertEquals(0, BigDecimal("100.00").compareTo(result[0].second))
        assertEquals(0, BigDecimal("60.00").compareTo(result[1].second))
        assertEquals(0, BigDecimal("40.00").compareTo(result[2].second))
    }

    @Test
    fun `PERCENTAGE split with 100 percent to one participant`() {
        val splits = listOf(
            SplitInput(participant1, SplitType.PERCENTAGE, BigDecimal("100"))
        )
        val result = ExpenseSplitValidator.validate(BigDecimal("500.00"), SplitType.PERCENTAGE, splits, allParticipants)
        assertEquals(1, result.size)
        assertEquals(0, BigDecimal("500.00").compareTo(result[0].second))
    }

    // ───────────────────── SHARES split ─────────────────────

    @Test
    fun `SHARES split distributes proportionally`() {
        val splits = listOf(
            SplitInput(participant1, SplitType.SHARES, BigDecimal("2")),
            SplitInput(participant2, SplitType.SHARES, BigDecimal("1"))
        )
        val result = ExpenseSplitValidator.validate(BigDecimal("300.00"), SplitType.SHARES, splits, allParticipants)
        assertEquals(0, BigDecimal("200.00").compareTo(result[0].second))
        assertEquals(0, BigDecimal("100.00").compareTo(result[1].second))
    }

    @Test
    fun `SHARES split with equal shares`() {
        val splits = listOf(
            SplitInput(participant1, SplitType.SHARES, BigDecimal("1")),
            SplitInput(participant2, SplitType.SHARES, BigDecimal("1")),
            SplitInput(participant3, SplitType.SHARES, BigDecimal("1"))
        )
        val result = ExpenseSplitValidator.validate(BigDecimal("300.00"), SplitType.SHARES, splits, allParticipants)
        assertEquals(0, BigDecimal("100.00").compareTo(result[0].second))
        assertEquals(0, BigDecimal("100.00").compareTo(result[1].second))
        assertEquals(0, BigDecimal("100.00").compareTo(result[2].second))
    }

    @Test
    fun `SHARES split rejects zero total shares`() {
        val splits = listOf(
            SplitInput(participant1, SplitType.SHARES, BigDecimal("0")),
            SplitInput(participant2, SplitType.SHARES, BigDecimal("0"))
        )
        assertThrows<DomainException.ValidationError> {
            ExpenseSplitValidator.validate(BigDecimal("100.00"), SplitType.SHARES, splits, allParticipants)
        }
    }

    // ───────────────────── EXACT_AMOUNT split ─────────────────────

    @Test
    fun `EXACT_AMOUNT must sum to total`() {
        val splits = listOf(
            SplitInput(participant1, SplitType.EXACT_AMOUNT, BigDecimal("60.00")),
            SplitInput(participant2, SplitType.EXACT_AMOUNT, BigDecimal("30.00"))
        )
        assertThrows<DomainException.InvalidSplitSum> {
            ExpenseSplitValidator.validate(BigDecimal("100.00"), SplitType.EXACT_AMOUNT, splits, allParticipants)
        }
    }

    @Test
    fun `EXACT_AMOUNT accepts correct sum`() {
        val splits = listOf(
            SplitInput(participant1, SplitType.EXACT_AMOUNT, BigDecimal("70.00")),
            SplitInput(participant2, SplitType.EXACT_AMOUNT, BigDecimal("30.00"))
        )
        val result = ExpenseSplitValidator.validate(BigDecimal("100.00"), SplitType.EXACT_AMOUNT, splits, allParticipants)
        assertEquals(0, BigDecimal("70.00").compareTo(result[0].second))
        assertEquals(0, BigDecimal("30.00").compareTo(result[1].second))
    }

    @Test
    fun `EXACT_AMOUNT with three participants summing correctly`() {
        val splits = listOf(
            SplitInput(participant1, SplitType.EXACT_AMOUNT, BigDecimal("50.00")),
            SplitInput(participant2, SplitType.EXACT_AMOUNT, BigDecimal("30.00")),
            SplitInput(participant3, SplitType.EXACT_AMOUNT, BigDecimal("20.00"))
        )
        val result = ExpenseSplitValidator.validate(BigDecimal("100.00"), SplitType.EXACT_AMOUNT, splits, allParticipants)
        assertEquals(3, result.size)
        val total = result.sumOf { it.second }
        assertEquals(0, BigDecimal("100.00").compareTo(total))
    }

    // ───────────────────── Validation edge cases ─────────────────────

    @Test
    fun `rejects negative split values`() {
        val splits = listOf(
            SplitInput(participant1, SplitType.EXACT_AMOUNT, BigDecimal("-10.00"))
        )
        assertThrows<DomainException.ValidationError> {
            ExpenseSplitValidator.validate(BigDecimal("100.00"), SplitType.EXACT_AMOUNT, splits, allParticipants)
        }
    }

    @Test
    fun `rejects participants not in trip`() {
        val outsider = UUID.randomUUID()
        val splits = listOf(SplitInput(outsider, SplitType.EQUAL, BigDecimal.ZERO))
        assertThrows<DomainException.ParticipantNotInTrip> {
            ExpenseSplitValidator.validate(BigDecimal("100.00"), SplitType.EQUAL, splits, allParticipants)
        }
    }

    @Test
    fun `rejects empty splits`() {
        assertThrows<DomainException.ValidationError> {
            ExpenseSplitValidator.validate(BigDecimal("100.00"), SplitType.EQUAL, emptyList(), allParticipants)
        }
    }

    @Test
    fun `rejects duplicate participants`() {
        val splits = listOf(
            SplitInput(participant1, SplitType.EQUAL, BigDecimal.ZERO),
            SplitInput(participant1, SplitType.EQUAL, BigDecimal.ZERO)
        )
        assertThrows<DomainException.ValidationError> {
            ExpenseSplitValidator.validate(BigDecimal("100.00"), SplitType.EQUAL, splits, allParticipants)
        }
    }

    @Test
    fun `rejects zero total amount`() {
        val splits = listOf(SplitInput(participant1, SplitType.EQUAL, BigDecimal.ZERO))
        assertThrows<DomainException.ValidationError> {
            ExpenseSplitValidator.validate(BigDecimal.ZERO, SplitType.EQUAL, splits, allParticipants)
        }
    }

    @Test
    fun `rejects negative total amount`() {
        val splits = listOf(SplitInput(participant1, SplitType.EQUAL, BigDecimal.ZERO))
        assertThrows<DomainException.ValidationError> {
            ExpenseSplitValidator.validate(BigDecimal("-50.00"), SplitType.EQUAL, splits, allParticipants)
        }
    }
}
