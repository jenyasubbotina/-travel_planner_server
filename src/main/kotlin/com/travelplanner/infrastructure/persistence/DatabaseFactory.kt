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

        // Одноразовый recovery: схема уже как после V17, история Flyway битая/отстаёт.
        // TRUNCATE оставляет пустую flyway_schema_history — baselineOnMigrate НЕ срабатывает (нужна отсутствующая таблица),
        // Flyway идёт с V1 → «already exists». Решение: DROP таблицы истории + явный baseline() на нужной версии.
        val recoveryBaseline = System.getenv("FLYWAY_RECOVERY_BASELINE_VERSION")?.trim().orEmpty()
        if (recoveryBaseline.isNotEmpty()) {
            log.warn(
                "Flyway RECOVERY: baselineVersion={}, DROP истории + baseline() — после успешного migrate удалите FLYWAY_RECOVERY_BASELINE_VERSION",
                recoveryBaseline
            )
            cfg.baselineVersion(MigrationVersion.fromVersion(recoveryBaseline))
        }

        val flyway = cfg.load()

        if (recoveryBaseline.isNotEmpty()) {
            dropFlywaySchemaHistoryTable(dataSource)
            flyway.baseline()
            log.warn("Flyway RECOVERY: baseline() зафиксирован на версии {}, далее migrate()", recoveryBaseline)
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

    /** Только для recovery: полное удаление таблицы истории, иначе Flyway не делает baseline и снова гоняет V1+. */
    private fun dropFlywaySchemaHistoryTable(dataSource: HikariDataSource) {
        try {
            dataSource.connection.use { conn ->
                conn.autoCommit = true
                conn.createStatement().use { st ->
                    st.execute("DROP TABLE IF EXISTS flyway_schema_history")
                }
            }
            log.warn("Flyway RECOVERY: flyway_schema_history удалена")
        } catch (e: SQLException) {
            if (e.sqlState != "42P01") throw e
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
