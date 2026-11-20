package com.librosleidos.backend.web

import io.swagger.v3.oas.models.OpenAPI

/** Compares the operations declared in `openapi.yaml` with the routes the controllers map. */
object OpenApiCoverage {
    fun specOperations(spec: OpenAPI): Set<String> = spec.paths.orEmpty().flatMap { (path, item) ->
        item.readOperationsMap().keys.map { "$it $path" }
    }.toSet()

    fun mismatches(specOperations: Set<String>, routes: Set<String>): List<String> =
        (specOperations - routes).sorted().map { "spec operation without route: $it" } +
            (routes - specOperations).sorted().map { "route without spec operation: $it" }
}
