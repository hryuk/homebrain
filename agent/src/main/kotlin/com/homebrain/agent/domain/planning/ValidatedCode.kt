package com.homebrain.agent.domain.planning

import com.homebrain.agent.domain.conversation.FileProposal

/**
 * Wrapper for code after successful validation.
 * 
 * This intermediate type forces GOAP to sequence validateCode before
 * respondWithAutomation, since respondWithAutomation requires ValidatedCode
 * which only validateCode produces.
 * 
 * Type chain:
 * - generateCode() → GeneratedCode
 * - extractToLibrary() → ExtractedCode
 * - validateCode() → ValidatedCode
 * - respondWithAutomation() requires ValidatedCode
 */
data class ValidatedCode(
    val files: List<FileProposal>,
    val summary: String,
    val attempt: Int
) {
    init {
        require(files.isNotEmpty()) { "At least one file must be present" }
        require(summary.isNotBlank()) { "Summary cannot be blank" }
        require(attempt >= 1) { "Attempt must be at least 1" }
    }

    /**
     * Convert to GeneratedCode if needed for other operations.
     */
    fun toGeneratedCode(): GeneratedCode = GeneratedCode(
        files = files,
        summary = summary,
        attempt = attempt
    )

    /**
     * Convert to ExtractedCode for re-validation after fixing.
     */
    fun toExtractedCode(): ExtractedCode = ExtractedCode(
        files = files,
        summary = summary,
        attempt = attempt,
        extractionPerformed = false,
        extractionSummary = "From validated code"
    )

    companion object {
        /**
         * Create from ExtractedCode after successful validation.
         */
        fun fromExtractedCode(code: ExtractedCode) = ValidatedCode(
            files = code.files,
            summary = code.summary,
            attempt = code.attempt
        )
    }
}
