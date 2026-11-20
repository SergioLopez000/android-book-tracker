package com.librosleidos.backend.data.jdbc

import com.librosleidos.backend.domain.model.Book
import com.librosleidos.backend.domain.model.BookDraft
import com.librosleidos.backend.domain.model.BookId
import com.librosleidos.backend.domain.model.OwnerId
import com.librosleidos.backend.domain.model.Rating
import com.librosleidos.backend.domain.port.BookRepository
import java.sql.ResultSet
import java.time.LocalDate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec

class JdbcBookRepository(private val jdbc: JdbcClient) : BookRepository {
    override fun create(owner: OwnerId, draft: BookDraft): Book =
        jdbc.sql(BookSql.INSERT).owned(owner).drafted(draft).query(ROW).single()

    override fun findById(owner: OwnerId, id: BookId): Book? = jdbc.sql(
        BookSql.SELECT_BY_ID
    ).owned(owner).param("id", id.value).query(ROW).optional().orElse(null)

    override fun findAll(owner: OwnerId): List<Book> =
        jdbc.sql(BookSql.SELECT_ALL).owned(owner).query(ROW).list()

    override fun update(owner: OwnerId, id: BookId, draft: BookDraft): Book? = jdbc.sql(
        BookSql.UPDATE
    ).owned(owner).param("id", id.value).drafted(draft).query(ROW).optional().orElse(null)

    override fun delete(owner: OwnerId, id: BookId): Boolean =
        jdbc.sql(BookSql.DELETE).owned(owner).param("id", id.value).update() > 0

    private fun StatementSpec.owned(owner: OwnerId) = param("owner", owner.value)

    private fun StatementSpec.drafted(draft: BookDraft) = param("title", draft.title)
        .param("author", draft.author)
        .param("coverUrl", draft.coverUrl)
        .param("rating", draft.rating.value)
        .param("review", draft.review)
        .param("dateFinished", draft.dateFinished)

    private companion object {
        val ROW = RowMapper { rs: ResultSet, _: Int ->
            Book(
                id = BookId(rs.getLong("id")),
                title = rs.getString("title"),
                author = rs.getString("author"),
                coverUrl = rs.getString("cover_url"),
                rating = Rating(rs.getFloat("rating")),
                review = rs.getString("review"),
                dateFinished = rs.getObject("date_finished", LocalDate::class.java)
            )
        }
    }
}
