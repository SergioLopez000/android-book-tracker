package com.librosleidos.backend.data.jdbc

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Guard: no public statement may be added to [BookSql] without scoping it to the owner (private fragments are exempt). */
class BookSqlGuardTest {
    private val statements: Map<String, String> =
        BookSql::class.java.declaredFields
            .filter {
                Modifier.isPublic(it.modifiers) && Modifier.isStatic(it.modifiers) &&
                    it.type == String::class.java
            }
            .associate { it.name to (it.apply { isAccessible = true }.get(null) as String) }

    private fun verb(sql: String) = sql.trimStart().substringBefore(' ').uppercase()

    @Test
    fun `every statement is a known verb so none escapes the guard`() {
        assertEquals(5, statements.size, "update this guard when statements are added: $statements")
        statements.forEach { (name, sql) ->
            assertTrue(
                verb(sql) in setOf("SELECT", "INSERT", "UPDATE", "DELETE"),
                "$name has an unknown verb"
            )
        }
    }

    @Test
    fun `select update and delete filter by owner`() {
        statements
            .filterValues { verb(it) != "INSERT" }
            .forEach { (name, sql) ->
                assertTrue(sql.contains("owner_id = :owner"), "$name lacks owner_id = :owner")
            }
    }

    @Test
    fun `insert sets the owner`() {
        statements
            .filterValues { verb(it) == "INSERT" }
            .forEach { (name, sql) ->
                assertTrue(
                    sql.contains("owner_id") && sql.contains(":owner"),
                    "$name does not set owner_id"
                )
            }
    }

    @Test
    fun `statements by id also keep the owner filter next to the id`() {
        statements
            .filterValues { it.contains("id = :id") }
            .forEach { (name, sql) ->
                assertTrue(sql.contains("owner_id = :owner AND id = :id"), name)
            }
    }
}
