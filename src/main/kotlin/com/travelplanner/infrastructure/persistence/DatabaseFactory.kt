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

        // Если история Flyway и фактическая схема разъехались (таблица не создана, а outbox уже крутится).
        ensureDomainEventsTable(dataSource)

        Database.connect(dataSource)
    }

    private fun ensureDomainEventsTable(dataSource: HikariDataSource) {
        dataSource.connection.use { conn ->
            conn.autoCommit = true
            conn.createStatement().use { st ->
                st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS domain_events (
                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        event_type VARCHAR(100) NOT NULL,
                        aggregate_type VARCHAR(50) NOT NULL,
                        aggregate_id UUID NOT NULL,
                        payload TEXT NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        processed_at TIMESTAMP,
                        retry_count INT NOT NULL DEFAULT 0
                    );
                    """.trimIndent()
                )
                st.execute(
                    "CREATE INDEX IF NOT EXISTS idx_domain_events_unprocessed ON domain_events (created_at) WHERE processed_at IS NULL;"
                )
                st.execute(
                    "CREATE INDEX IF NOT EXISTS idx_domain_events_aggregate ON domain_events (aggregate_type, aggregate_id);"
                )
            }
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
