package com.librosleidos.backend.contract

import io.swagger.v3.oas.models.OpenAPI

/** Maps every `operationId` of the spec to the contract test class that must exist for it. */
object ContractCoverage {
    const val PACKAGE = "com.librosleidos.backend.contract.books"

    fun operationIds(spec: OpenAPI): Set<String> = spec.paths.orEmpty().values
        .flatMap { it.readOperations() }
        .map { it.operationId }
        .toSet()

    fun expectedClass(operationId: String): String = "$PACKAGE.${operationId.replaceFirstChar {
        it.uppercase()
    }}Test"

    fun untested(operationIds: Set<String>, existingClasses: Set<String>): List<String> =
        operationIds
            .filter { expectedClass(it) !in existingClasses }
            .sorted()
            .map { "operation without contract test: $it (expected ${expectedClass(it)})" }
}
