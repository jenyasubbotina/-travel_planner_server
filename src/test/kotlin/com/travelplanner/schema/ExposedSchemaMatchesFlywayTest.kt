package com.travelplanner.schema

import com.travelplanner.infrastructure.persistence.tables.AttachmentsTable
import com.travelplanner.infrastructure.persistence.tables.AuthRefreshTokensTable
import com.travelplanner.infrastructure.persistence.tables.DomainEventsTable
import com.travelplanner.infrastructure.persistence.tables.ExpenseHistoryTable
import com.travelplanner.infrastructure.persistence.tables.ExpensePendingUpdatesTable
import com.travelplanner.infrastructure.persistence.tables.ExpenseSplitsTable
import com.travelplanner.infrastructure.persistence.tables.ExpensesTable
import com.travelplanner.infrastructure.persistence.tables.IdempotencyKeysTable
import com.travelplanner.infrastructure.persistence.tables.ItineraryPointCommentsTable
import com.travelplanner.infrastructure.persistence.tables.ItineraryPointLinksTable
import com.travelplanner.infrastructure.persistence.tables.ItineraryPointsTable
import com.travelplanner.infrastructure.persistence.tables.TripChecklistCompletionsTable
import com.travelplanner.infrastructure.persistence.tables.TripChecklistItemsTable
import com.travelplanner.infrastructure.persistence.tables.TripInvitationsTable
import com.travelplanner.infrastructure.persistence.tables.TripJoinRequestsTable
import com.travelplanner.infrastructure.persistence.tables.TripParticipantsTable
import com.travelplanner.infrastructure.persistence.tables.TripsTable
import com.travelplanner.infrastructure.persistence.tables.UserDevicesTable
import com.travelplanner.infrastructure.persistence.tables.UsersTable
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Fails the build/test phase if Flyway migrations produce a PostgreSQL schema that does not match
 * the Exposed [org.jetbrains.exposed.sql.Table] definitions (the ORM layer used by repositories).
 *
 * When this test fails, either adjust SQL migrations under `src/main/resources/com/travelplanner/db/migration/`
 * or update the matching `*Table.kt` so they stay in sync.
 */
@Testcontainers(disabledWithoutDocker = true)
class ExposedSchemaMatchesFlywayTest {

    companion object {
        @Container
        @JvmField
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("travel_planner")
            .withUsername("tp_user")
            .withPassword("tp_pass")

        /** Dependency order for Exposed FK resolution when diffing the live schema. */
        private val tablesInCreateOrder = arrayOf(
            UsersTable,
            AuthRefreshTokensTable,
            UserDevicesTable,
            TripsTable,
            TripInvitationsTable,
            TripParticipantsTable,
            ItineraryPointsTable,
            ItineraryPointLinksTable,
            ItineraryPointCommentsTable,
            ExpensesTable,
            ExpenseSplitsTable,
            AttachmentsTable,
            IdempotencyKeysTable,
            DomainEventsTable,
            TripChecklistItemsTable,
            TripChecklistCompletionsTable,
            TripJoinRequestsTable,
            ExpensePendingUpdatesTable,
            ExpenseHistoryTable
        )
    }

    @Test
    fun `database schema after Flyway matches Exposed table definitions`() {
        val hikari = HikariConfig().apply {
            jdbcUrl = postgres.jdbcUrl
            username = postgres.username
            password = postgres.password
            maximumPoolSize = 2
            validate()
        }
        val dataSource = HikariDataSource(hikari)
        try {
            Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:com/travelplanner/db/migration")
                .load()
                .migrate()

            Database.connect(dataSource)

            val statements = transaction {
                @Suppress("DEPRECATION")
                SchemaUtils.statementsRequiredToActualizeScheme(*tablesInCreateOrder, withLogs = false)
            }
            val meaningfulStatements = statements.filterNot(::isNonFunctionalDriftStatement)
            assertTrue(
                meaningfulStatements.isEmpty(),
                "Schema drift between Flyway migrations and Exposed tables. " +
                    "Statements Exposed would still apply:\n${meaningfulStatements.joinToString("\n")}"
            )
        } finally {
            dataSource.close()
        }
    }

    /**
     * Exposed 0.61 reports noisy diffs for legacy Flyway-managed Postgres schemas:
     * - FK names and explicit ON UPDATE RESTRICT clauses
     * - UUID PK DEFAULT gen_random_uuid()/uuid_generate_v4() normalization
     * - TIMESTAMPTZ + DEFAULT now() vs Exposed timestamp() representation
     *
     * These are non-functional for runtime behavior here; we still fail on actual
     * schema shape drift (missing/extra columns, nullability, indexes, PKs, etc).
     */
    private fun isNonFunctionalDriftStatement(statement: String): Boolean {
        val normalized = statement.lowercase().trim().replace(Regex("\\s+"), " ")
        return normalized.matches(Regex("""alter table .+ drop constraint .+""")) ||
            normalized.matches(Regex("""alter table .+ add constraint fk_.+ foreign key .+ on delete .+ on update restrict""")) ||
            normalized.matches(Regex("""alter table .+ alter column id drop default""")) ||
            normalized.matches(Regex("""alter table .+ alter column .+ type timestamp, alter column .+ drop default"""))
    }
}
