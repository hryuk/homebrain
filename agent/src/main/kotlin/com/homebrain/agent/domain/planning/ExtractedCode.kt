package com.homebrain.agent.domain.planning

import com.homebrain.agent.domain.conversation.FileProposal

/**
 * Wrapper for GeneratedCode after the extraction phase.
 * 
 * This intermediate type forces GOAP to sequence extractToLibrary 
 * before validateCode, since validateCode requires ExtractedCode 
 * which only extractToLibrary produces.
 */
data class ExtractedCode(
    val files: List<FileProposal>,
    val summary: String,
    val attempt: Int,
    val extractionPerformed: Boolean,
    val extractionSummary: String
) {
    init {
        require(files.isNotEmpty()) { "At least one file must be present" }
        require(summary.isNotBlank()) { "Summary cannot be blank" }
        require(attempt >= 1) { "Attempt must be at least 1" }
    }

    /**
     * Convert to GeneratedCode for validation and response.
     */
    fun toGeneratedCode(): GeneratedCode = GeneratedCode(
        files = files,
        summary = summary,
        attempt = attempt
    )

    companion object {
        /**
         * Create from GeneratedCode when no extraction was performed.
         */
        fun noExtraction(code: GeneratedCode, reason: String) = ExtractedCode(
            files = code.files,
            summary = code.summary,
            attempt = code.attempt,
            extractionPerformed = false,
            extractionSummary = reason
        )

        /**
         * Create from extraction result.
         */
        fun withExtraction(
            files: List<FileProposal>,
            originalSummary: String,
            attempt: Int,
            extractionSummary: String
        ) = ExtractedCode(
            files = files,
            summary = originalSummary,
            attempt = attempt,
            extractionPerformed = true,
            extractionSummary = extractionSummary
        )
    }
}
