package com.librosleidos.backend.web

import com.atlassian.oai.validator.springmvc.InvalidRequestException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * Every error leaves the API as a `ProblemDetail` whose `title`, `status` and `detail` are constants
 * per status. Nothing from the exception (message, class, SQL, paths, stack) is ever copied into it.
 */
@RestControllerAdvice
class ApiErrorHandler : ResponseEntityExceptionHandler() {
    @ExceptionHandler(InvalidRequestException::class, InvalidBookRequestException::class)
    fun badRequest(): ResponseEntity<Any> = problem(HttpStatus.BAD_REQUEST)

    @ExceptionHandler(BookNotFoundException::class)
    fun notFound(): ResponseEntity<Any> = problem(HttpStatus.NOT_FOUND)

    @ExceptionHandler(Exception::class)
    fun unexpected(e: Exception): ResponseEntity<Any> {
        // The only place the cause is recorded: the server log, never the response.
        log.error("Unhandled error while serving a request", e)
        return problem(HttpStatus.INTERNAL_SERVER_ERROR)
    }

    // Spring MVC's own exceptions (malformed JSON, wrong type, 404, 405, 415, ...) keep their status
    // but lose their messages.
    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any>? = problem(HttpStatus.valueOf(statusCode.value()), headers)

    private fun problem(
        status: HttpStatus,
        headers: HttpHeaders = HttpHeaders()
    ): ResponseEntity<Any> {
        val detail = DETAILS[status] ?: "Request failed"
        val body = ProblemDetail.forStatusAndDetail(status, detail).apply {
            title =
                status.reasonPhrase
        }
        return ResponseEntity.status(status).headers(headers).body(body)
    }

    private companion object {
        val log = LoggerFactory.getLogger(ApiErrorHandler::class.java)
        val DETAILS =
            mapOf(
                HttpStatus.BAD_REQUEST to "Request does not match the API contract",
                HttpStatus.NOT_FOUND to "Resource not found",
                HttpStatus.METHOD_NOT_ALLOWED to "Method not allowed",
                HttpStatus.NOT_ACCEPTABLE to "Not acceptable",
                HttpStatus.UNSUPPORTED_MEDIA_TYPE to "Unsupported media type",
                HttpStatus.INTERNAL_SERVER_ERROR to "Unexpected error"
            )
    }
}
