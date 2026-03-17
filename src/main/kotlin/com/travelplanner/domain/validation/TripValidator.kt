package com.travelplanner.domain.validation

import com.travelplanner.domain.exception.DomainException
import java.time.LocalDate

object TripValidator {

    fun validateTitle(title: String) {
        if (title.isBlank()) {
            throw DomainException.ValidationError("Trip title cannot be blank")
        }
        if (title.length > 255) {
            throw DomainException.ValidationError("Trip title cannot exceed 255 characters")
        }
    }

    fun validateDates(startDate: LocalDate?, endDate: LocalDate?) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw DomainException.ValidationError("End date cannot be before start date")
        }
    }

    fun validateCurrency(currency: String) {
        if (currency.isBlank() || currency.length > 10) {
            throw DomainException.ValidationError("Currency must be 1-10 characters")
        }
    }

    fun validateEmail(email: String) {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
        if (!emailRegex.matches(email)) {
            throw DomainException.ValidationError("Invalid email format: $email")
        }
    }
}
