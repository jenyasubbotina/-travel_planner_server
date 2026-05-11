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

        // Одноразовый recovery: схема уже как после V17, а в flyway_schema_history только baseline «1» или мусор —
        // см. README / комментарий в docker-compose.prod.yml (FLYWAY_RECOVERY_BASELINE_VERSION).
        val recoveryBaseline = System.getenv("FLYWAY_RECOVERY_BASELINE_VERSION")?.trim().orEmpty()
        if (recoveryBaseline.isNotEmpty()) {
            log.warn(
                "Flyway RECOVERY: baselineVersion={} — после успешного старта удалите FLYWAY_RECOVERY_BASELINE_VERSION",
                recoveryBaseline
            )
            cfg.baselineVersion(MigrationVersion.fromVersion(recoveryBaseline))
        }

        val flyway = cfg.load()

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

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
