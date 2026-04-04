package com.travelplanner.infrastructure.persistence

import com.travelplanner.infrastructure.config.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

object DatabaseFactory {

    fun init(config: DatabaseConfig) {
        // Отдельный пул с autoCommit=true для Flyway: иначе на некоторых драйверах/настройках
        // миграции могут не закоммититься, а приложение стартует без части таблиц (например domain_events).
        val flywayDs = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.url
                username = config.user
                password = config.password
                maximumPoolSize = 2
                isAutoCommit = true
                poolName = "flyway"
                validate()
            }
        )
        try {
            Flyway.configure()
                .dataSource(flywayDs)
                .locations("classpath:db/migration")
                .load()
                .migrate()
        } finally {
            flywayDs.close()
        }

        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.url
            username = config.user
            password = config.password
            maximumPoolSize = config.maxPoolSize
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
        val dataSource = HikariDataSource(hikariConfig)

        Database.connect(dataSource)
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
