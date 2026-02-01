package com.homebrain.agent.domain.planning

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.homebrain.agent.domain.conversation.FileProposal

/**
 * Represents a validation failure for a generated file.
 * 
 * When validateCode() finds errors, it adds ValidationFailure objects
 * to the blackboard. The codeIsInvalid condition checks for the presence
 * of these objects.
 * 
 * The fixInvalidCode() action consumes these failures and attempts to
 * fix the code based on the error messages.
 */
@JsonClassDescription("Validation errors for a generated file")
data class ValidationFailure(
    val file: FileProposal,
    val errors: List<String>
) {
    init {
        require(errors.isNotEmpty()) { "Must have at least one error" }
    }
    
    /**
     * Get errors as a formatted string for prompts.
     */
    fun formattedErrors(): String = errors.mapIndexed { index, error ->
        "${index + 1}. $error"
    }.joinToString("\n")
    
    /**
     * Get a brief summary of the failure.
     */
    fun summary(): String = "${file.filename}: ${errors.size} error(s)"
    
    companion object {
        /**
         * Create a validation failure from a file and error list.
         */
        fun of(file: FileProposal, errors: List<String>) = ValidationFailure(file, errors)
        
        /**
         * Create a validation failure from a file and single error.
         */
        fun of(file: FileProposal, error: String) = ValidationFailure(file, listOf(error))
    }
}

/**
 * Container for all validation failures in a code generation cycle.
 * 
 * Useful for tracking multiple files with errors.
 */
data class ValidationFailures(
    val failures: List<ValidationFailure>
) {
    /**
     * Check if there are any failures.
     */
    fun hasFailures(): Boolean = failures.isNotEmpty()
    
    /**
     * Check if all files passed validation.
     */
    fun allValid(): Boolean = failures.isEmpty()
    
    /**
     * Get the total number of errors across all files.
     */
    fun totalErrors(): Int = failures.sumOf { it.errors.size }
    
    /**
     * Get failures for a specific file.
     */
    fun forFile(filename: String): ValidationFailure? =
        failures.find { it.file.filename == filename }
    
    companion object {
        /**
         * Create an empty (all valid) result.
         */
        fun valid() = ValidationFailures(emptyList())
        
        /**
         * Create from a list of failures.
         */
        fun of(failures: List<ValidationFailure>) = ValidationFailures(failures)
    }
}
