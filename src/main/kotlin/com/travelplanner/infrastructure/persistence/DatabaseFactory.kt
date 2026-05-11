package com.travelplanner.infrastructure.persistence

import com.travelplanner.infrastructure.config.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.sql.SQLException

object DatabaseFactory {

    private val log = LoggerFactory.getLogger(DatabaseFactory::class.java)

    fun init(config: DatabaseConfig) {
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

        val cfg = Flyway.configure()
            .dataSource(dataSource)
            // Уникальный путь: в fat-jar есть чужие .sql (например Logback ch/qos/.../postgresql.sql).
            // С classpath:db/migration Flyway валидирует лишние файлы; часть наших миграций может не применяться.
            .locations("classpath:com/travelplanner/db/migration")
            .baselineOnMigrate(true)
            .outOfOrder(true)

        // Одноразовый recovery: схема уже как после V17, а в flyway_schema_history только «1» / битые строки.
        // Только baselineVersion() недостаточно: при непустой истории Flyway не делает baseline заново и снова гоняет V2+.
        // Чистим историю — при непустой схеме сработает baselineOnMigrate + baselineVersion (пропуск до N, затем V18+).
        val recoveryBaseline = System.getenv("FLYWAY_RECOVERY_BASELINE_VERSION")?.trim().orEmpty()
        if (recoveryBaseline.isNotEmpty()) {
            log.warn(
                "Flyway RECOVERY: baselineVersion={}, очистка flyway_schema_history — после успешного migrate удалите FLYWAY_RECOVERY_BASELINE_VERSION",
                recoveryBaseline
            )
            cfg.baselineVersion(MigrationVersion.fromVersion(recoveryBaseline))
        }

        val flyway = cfg.load()

        if (recoveryBaseline.isNotEmpty()) {
            clearFlywayHistoryTable(dataSource)
        }

        val infoBefore = flyway.info()
        log.info(
            "Flyway: locations=classpath:com/travelplanner/db/migration, " +
                "currentVersion={}, pendingMigrations={}",
            infoBefore.current()?.version?.version,
            infoBefore.pending().size
        )

        flyway.repair()
        flyway.migrate()

        val infoAfter = flyway.info()
        log.info(
            "Flyway: after migrate currentVersion={}, allApplied={}",
            infoAfter.current()?.version?.version,
            infoAfter.pending().isEmpty()
        )

        Database.connect(dataSource)
    }

    /** Только для FLYWAY_RECOVERY_BASELINE_VERSION: пустая история + непустая схема → baseline на нужной версии. */
    private fun clearFlywayHistoryTable(dataSource: HikariDataSource) {
        try {
            dataSource.connection.use { conn ->
                conn.autoCommit = true
                conn.createStatement().use { st ->
                    st.execute("TRUNCATE TABLE flyway_schema_history")
                }
            }
            log.warn("Flyway RECOVERY: таблица flyway_schema_history очищена")
        } catch (e: SQLException) {
            val undefinedTable = e.sqlState == "42P01"
            if (!undefinedTable) throw e
            log.info("Flyway RECOVERY: flyway_schema_history ещё нет — пропуск TRUNCATE")
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
