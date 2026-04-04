package com.travelplanner.infrastructure.persistence.tables

import org.jetbrains.exposed.sql.CustomFunction
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.UUIDColumnType
/** Matches PostgreSQL `DEFAULT gen_random_uuid()` used in Flyway migrations. */
fun Table.pgGeneratedUuid(name: String) =
    uuid(name).defaultExpression(CustomFunction("gen_random_uuid", UUIDColumnType()))
