package com.homebrain.agent.exception

import com.homebrain.agent.application.AutomationNotFoundException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.nio.file.NoSuchFileException

private val logger = KotlinLogging.logger {}

data class ErrorResponse(
    val error: String,
    val message: String
)

@RestControllerAdvice
class HomebrainExceptionHandler {

    @ExceptionHandler(AutomationNotFoundException::class)
    fun handleAutomationNotFoundException(e: AutomationNotFoundException): ResponseEntity<ErrorResponse> {
        logger.warn { e.message }
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse("not_found", e.message ?: "Automation not found"))
    }

    @ExceptionHandler(NoSuchFileException::class)
    fun handleNoSuchFileException(e: NoSuchFileException): ResponseEntity<ErrorResponse> {
        logger.warn { "File not found: ${e.file}" }
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse("not_found", "File not found: ${e.file}"))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(e: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        logger.warn { "Bad request: ${e.message}" }
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("bad_request", e.message ?: "Invalid request"))
    }

    @ExceptionHandler(RuntimeException::class)
    fun handleRuntimeException(e: RuntimeException): ResponseEntity<ErrorResponse> {
        logger.error(e) { "Internal error: ${e.message}" }
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse("internal_error", e.message ?: "Internal server error"))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ErrorResponse> {
        logger.error(e) { "Unexpected error: ${e.message}" }
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse("internal_error", "An unexpected error occurred"))
    }
}
