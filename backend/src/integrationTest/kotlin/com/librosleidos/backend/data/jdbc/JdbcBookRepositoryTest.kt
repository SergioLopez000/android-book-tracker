package com.librosleidos.backend.data.jdbc

import com.librosleidos.backend.domain.model.BookDraft
import com.librosleidos.backend.domain.model.BookId
import com.librosleidos.backend.domain.model.OwnerId
import com.librosleidos.backend.domain.model.Rating
import com.librosleidos.backend.domain.port.BookRepository
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.JdbcClient

@SpringBootTest
@Import(PostgresContainerConfig::class)
class JdbcBookRepositoryTest {
    @Autowired lateinit var repository: BookRepository

    @Autowired lateinit var jdbc: JdbcClient

    private val alice = OwnerId("alice")
    private val bob = OwnerId("bob")

    private fun draft(title: String = "Dune", cover: String? = "https://example.test/c.png") =
        BookDraft(title, "Frank Herbert", cover, Rating(4.5f), "great", LocalDate.of(2026, 1, 2))

    @Test
    fun `flyway applied V1 to an empty database`() {
        val applied =
            jdbc.sql(
                "SELECT success FROM flyway_schema_history WHERE version = '1'"
            ).query(Boolean::class.java).single()
        assertTrue(applied)
    }

    @Test
    fun `owner_id is NOT NULL so a raw insert without it is rejected`() {
        assertThrows<DataIntegrityViolationException> {
            jdbc
                .sql(
                    "INSERT INTO books (title, author, rating, review, date_finished) " +
                        "VALUES ('t', 'a', 3, '', DATE '2026-01-01')"
                )
                .update()
        }
    }

    @Test
    fun `create then read, list, update and delete round-trip for the owner`() {
        val created = repository.create(alice, draft())
        assertEquals("Dune", created.title)
        assertEquals(4.5f, created.rating.value)
        assertEquals(created, repository.findById(alice, created.id))
        assertTrue(created in repository.findAll(alice))

        val updated =
            assertNotNull(repository.update(alice, created.id, draft("Messiah", cover = null)))
        assertEquals(created.id, updated.id)
        assertEquals("Messiah", updated.title)
        assertNull(updated.coverUrl)
        assertEquals(updated, repository.findById(alice, created.id))

        assertTrue(repository.delete(alice, created.id))
        assertNull(repository.findById(alice, created.id))
        assertFalse(repository.delete(alice, created.id))
    }

    @Test
    fun `another owner cannot read update or delete and nothing changes`() {
        val book = repository.create(alice, draft())

        assertNull(repository.findById(bob, book.id))
        assertNull(repository.update(bob, book.id, draft("Hijack")))
        assertFalse(repository.delete(bob, book.id))

        assertEquals(book, repository.findById(alice, book.id))
    }

    @Test
    fun `unknown id is not found`() {
        assertNull(repository.findById(alice, BookId(Long.MAX_VALUE)))
        assertNull(repository.update(alice, BookId(Long.MAX_VALUE), draft()))
        assertFalse(repository.delete(alice, BookId(Long.MAX_VALUE)))
    }

    @Test
    fun `listing returns only the caller's books`() {
        val owner = OwnerId("list-owner")
        val other = OwnerId("list-other")
        val mine = repository.create(owner, draft("Mine"))
        val theirs = repository.create(other, draft("Theirs"))

        assertEquals(listOf(mine), repository.findAll(owner))
        assertEquals(listOf(theirs), repository.findAll(other))
        assertEquals(emptyList(), repository.findAll(OwnerId("nobody")))
    }
}
