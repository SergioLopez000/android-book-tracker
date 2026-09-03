package com.librosleidos.domain.model

/**
 * Three mutually-exclusive buckets over [com.librosleidos.domain.model.Book.dateFinished]
 * (spec: "Filter by Date Range"). `ALL_TIME` ("histórico") is the default
 * and applies no lower bound; `LAST_MONTH`/`LAST_YEAR` resolve to an
 * epoch-day lower bound in [com.librosleidos.domain.usecase.GetBooksUseCase]
 * (design D8).
 */
enum class DateRangeFilter {
    LAST_MONTH,
    LAST_YEAR,
    ALL_TIME,
}
