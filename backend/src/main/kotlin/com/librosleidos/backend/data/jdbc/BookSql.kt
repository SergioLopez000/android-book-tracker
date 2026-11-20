package com.librosleidos.backend.data.jdbc

/**
 * Every statement is scoped by `owner_id = :owner`; `BookSqlGuardTest` fails if one is not.
 * Statements by id keep the owner filter next to the id so a foreign id matches no row.
 */
internal object BookSql {
    private const val COLUMNS = "id, title, author, cover_url, rating, review, date_finished"

    const val INSERT =
        "INSERT INTO books (owner_id, title, author, cover_url, rating, review, date_finished) " +
            "VALUES (:owner, :title, :author, :coverUrl, :rating, :review, :dateFinished) RETURNING $COLUMNS"

    const val SELECT_BY_ID = "SELECT $COLUMNS FROM books WHERE owner_id = :owner AND id = :id"

    const val SELECT_ALL =
        "SELECT $COLUMNS FROM books WHERE owner_id = :owner " +
            "ORDER BY date_finished DESC, id DESC"

    const val UPDATE =
        "UPDATE books SET title = :title, author = :author, cover_url = :coverUrl, " +
            "rating = :rating, review = :review, date_finished = :dateFinished " +
            "WHERE owner_id = :owner AND id = :id RETURNING $COLUMNS"

    const val DELETE = "DELETE FROM books WHERE owner_id = :owner AND id = :id"
}
