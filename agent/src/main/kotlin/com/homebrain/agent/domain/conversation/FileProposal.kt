package com.homebrain.agent.domain.conversation

/**
 * Value object representing a single file in a code proposal.
 * 
 * A file can be either an automation (.star) or a library module (.lib.star).
 * This allows the LLM to propose multiple files together, such as a reusable
 * library function and the automation that uses it.
 */
data class FileProposal(
    val code: String,
    val filename: String,
    val type: FileType
) {
    init {
        require(code.isNotBlank()) { "Code cannot be blank" }
        require(filename.isNotBlank()) { "Filename cannot be blank" }
    }

    /**
     * Returns true if this is a library file.
     */
    fun isLibrary(): Boolean = type == FileType.LIBRARY

    /**
     * Returns true if this is an automation file.
     */
    fun isAutomation(): Boolean = type == FileType.AUTOMATION

    /**
     * The type of file being proposed.
     */
    enum class FileType {
        AUTOMATION,
        LIBRARY;

        /**
         * Returns lowercase string representation for API serialization.
         */
        fun toApiString(): String = name.lowercase()

        companion object {
            /**
             * Parses a file type from a string (case-insensitive).
             */
            fun fromString(value: String): FileType {
                return when (value.lowercase()) {
                    "automation" -> AUTOMATION
                    "library" -> LIBRARY
                    else -> throw IllegalArgumentException("Unknown file type: $value")
                }
            }
        }
    }

    companion object {
        /**
         * Creates an automation file proposal.
         */
        fun automation(code: String, filename: String): FileProposal =
            FileProposal(code, filename, FileType.AUTOMATION)

        /**
         * Creates a library file proposal.
         */
        fun library(code: String, filename: String): FileProposal =
            FileProposal(code, filename, FileType.LIBRARY)
    }
}
