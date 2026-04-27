package com.travelplanner.infrastructure.persistence

import com.travelplanner.domain.repository.TransactionRunner
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedTransactionRunner : TransactionRunner {
    override suspend fun <T> runInTransaction(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
