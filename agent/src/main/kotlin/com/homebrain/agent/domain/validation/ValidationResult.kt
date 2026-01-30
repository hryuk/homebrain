package com.homebrain.agent.domain.validation

/**
 * Value object representing the result of Starlark code validation.
 * 
 * Returned by the engine's validation endpoint to indicate whether
 * code is syntactically valid and structurally correct.
 */
data class ValidationResult(
    val valid: Boolean,
    val errors: List<String> = emptyList()
) {
    /**
     * Returns true if the code is valid.
     * Alias for [valid] property for more fluent API.
     */
    fun isValid(): Boolean = valid

    /**
     * Returns true if there are validation errors.
     */
    fun hasErrors(): Boolean = errors.isNotEmpty()

    /**
     * Returns a summary of all errors joined by semicolon.
     */
    fun errorSummary(): String = errors.joinToString("; ")

    companion object {
        /**
         * Creates a successful validation result.
         */
        fun success(): ValidationResult = ValidationResult(valid = true, errors = emptyList())

        /**
         * Creates a failed validation result with the given errors.
         */
        fun failure(errors: List<String>): ValidationResult = ValidationResult(valid = false, errors = errors)

        /**
         * Creates a failed validation result with a single error.
         */
        fun failure(error: String): ValidationResult = failure(listOf(error))
    }
}
