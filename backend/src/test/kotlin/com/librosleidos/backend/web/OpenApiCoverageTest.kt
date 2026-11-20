package com.librosleidos.backend.web

import io.swagger.parser.OpenAPIParser
import io.swagger.v3.parser.core.models.ParseOptions
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

@SpringBootTest
class OpenApiCoverageTest {
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    lateinit var handlerMapping: RequestMappingHandlerMapping

    @Test
    fun `spec operations and controller routes match one to one`() {
        val spec =
            OpenAPIParser()
                .readLocation("openapi.yaml", null, ParseOptions().apply { isResolve = true })
                .openAPI
        assertNotNull(spec, "openapi.yaml must exist and parse")
        val routes =
            handlerMapping.handlerMethods
                .filterValues { it.beanType.packageName.startsWith("com.librosleidos.backend.web") }
                .flatMap { (info, _) ->
                    val patterns = info.pathPatternsCondition?.patternValues.orEmpty()
                    info.methodsCondition.methods.flatMap { method ->
                        patterns.map { "${method.name} $it" }
                    }
                }
                .toSet()

        val problems = OpenApiCoverage.mismatches(OpenApiCoverage.specOperations(spec), routes)

        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
        assertEquals(5, routes.size)
    }

    @Test
    fun `a spec operation without a route is reported by name`() {
        val problems = OpenApiCoverage.mismatches(
            setOf("GET /api/books", "POST /api/books"),
            setOf("GET /api/books")
        )
        assertEquals(listOf("spec operation without route: POST /api/books"), problems)
    }

    @Test
    fun `a route without a spec operation is reported by name`() {
        val problems = OpenApiCoverage.mismatches(
            setOf("GET /api/books"),
            setOf("GET /api/books", "DELETE /api/books/{id}")
        )
        assertEquals(listOf("route without spec operation: DELETE /api/books/{id}"), problems)
    }
}
