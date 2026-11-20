package com.librosleidos.backend.contract

import com.atlassian.oai.validator.OpenApiInteractionValidator
import com.atlassian.oai.validator.report.LevelResolver
import com.atlassian.oai.validator.report.ValidationReport
import com.atlassian.oai.validator.restassured.OpenApiValidationFilter
import com.librosleidos.backend.domain.model.Book
import com.librosleidos.backend.domain.model.BookDraft
import com.librosleidos.backend.domain.model.OwnerId
import com.librosleidos.backend.domain.model.Rating
import com.librosleidos.backend.domain.port.BookRepository
import com.librosleidos.testsupport.ContractPostgresConfig
import com.librosleidos.testsupport.ContractTestAuthentication
import io.restassured.RestAssured
import io.restassured.http.ContentType
import io.restassured.specification.RequestSpecification
import java.time.LocalDate
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient

/**
 * Real application on a random port with a real PostgreSQL. Every request and
 * response is checked against `openapi.yaml`; assertions use JSON paths only.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ContractTestAuthentication::class, ContractPostgresConfig::class)
abstract class ContractTestBase {
    @LocalServerPort private var port: Int = 0

    @Autowired private lateinit var jdbc: JdbcClient

    @Autowired protected lateinit var repository: BookRepository

    @BeforeEach
    fun cleanDatabase() {
        jdbc.sql("DELETE FROM books").update()
    }

    /** Validates request and response. Unauthenticated requests carry no credentials. */
    protected fun api(authenticated: Boolean = true): RequestSpecification = base(authenticated)
        .filter(OpenApiValidationFilter(SPEC))

    /**
     * For requests that deliberately break the contract (400 cases): the request
     * is not validated, the response still is.
     */
    protected fun invalidApi(): RequestSpecification = base(true).filter(
        OpenApiValidationFilter(
            OpenApiInteractionValidator.createFor(SPEC)
                .withLevelResolver(
                    LevelResolver.create().withLevel(
                        "validation.request",
                        ValidationReport.Level.IGNORE
                    ).build()
                )
                .build()
        )
    )

    protected fun bookJson(title: String = "Dune", extra: String = ""): String =
        """{"title":"$title","author":"Frank Herbert","rating":4.5,"review":"Great","dateFinished":"2026-01-02"$extra}"""

    protected fun ownBook(title: String = "Dune"): Book =
        repository.create(owner(ContractTestAuthentication.OWNER), draft(title))

    protected fun foreignBook(): Book = repository.create(owner(FOREIGN_OWNER), draft("Foreign"))

    private fun owner(name: String) = OwnerId(name)

    private fun draft(title: String) =
        BookDraft(title, "Frank Herbert", null, Rating(4.5f), "Great", LocalDate.of(2026, 1, 2))

    private fun base(authenticated: Boolean): RequestSpecification {
        val spec =
            RestAssured.given().baseUri("http://localhost").port(port).contentType(ContentType.JSON)
        return if (authenticated) {
            spec.auth().preemptive().basic(
                ContractTestAuthentication.OWNER,
                ContractTestAuthentication.PASSWORD
            )
        } else {
            spec
        }
    }

    companion object {
        const val FOREIGN_OWNER = "another-owner"
        const val SPEC = "openapi.yaml"
    }
}
