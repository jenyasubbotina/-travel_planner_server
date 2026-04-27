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
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExposedSchemaMatchesFlywayTest {

    private lateinit var embedded: EmbeddedPostgres

    @BeforeAll
    fun startPostgres() {
        embedded = EmbeddedPostgres.builder().start()
    }

    @AfterAll
    fun stopPostgres() {
        if (::embedded.isInitialized) {
            embedded.close()
        }
    }

    @Test
    fun `database schema after Flyway matches Exposed table definitions`() {
        val hikari = HikariConfig().apply {
            jdbcUrl = embedded.getJdbcUrl("postgres", "postgres")
            username = "postgres"
            password = "postgres"
            maximumPoolSize = 2
            validate()
        }
        val dataSource = HikariDataSource(hikari)
        try {
            Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate()

            Database.connect(dataSource)

            val indexStatements = transaction {
                @Suppress("DEPRECATION")
                SchemaUtils.checkMappingConsistence(*tablesInCreateOrder, withLogs = false)
            }
            assertTrue(
                indexStatements.isEmpty(),
                "Index drift between Flyway migrations and Exposed tables (uniqueIndex / mapped indices). " +
                    "Statements Exposed would still apply:\n${indexStatements.joinToString("\n")}"
            )
        } finally {
            dataSource.close()
        }
    }

    companion object {
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
}
