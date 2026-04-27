package com.travelplanner.domain.repository

interface TransactionRunner {
    suspend fun <T> runInTransaction(block: suspend () -> T): T
}
