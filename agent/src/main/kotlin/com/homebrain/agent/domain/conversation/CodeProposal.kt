package com.homebrain.agent.domain.conversation

import com.homebrain.agent.domain.automation.Automation
import com.homebrain.agent.domain.automation.AutomationId

/**
 * Value object representing a proposed code change that requires user confirmation.
 * 
 * A CodeProposal can contain one or more files:
 * - Single automation file (traditional case)
 * - Library file + automation file (when creating reusable functions)
 * - Multiple library files (when refactoring shared logic)
 * 
 * When the LLM generates code that should be reusable, it proposes both the library
 * module and the automation that uses it together, allowing atomic deployment.
 */
data class CodeProposal(
    val summary: String,
    val files: List<FileProposal>
) {
    init {
        require(summary.isNotBlank()) { "Summary cannot be blank" }
        require(files.isNotEmpty()) { "At least one file is required" }
    }

    /**
     * Returns all library files in this proposal.
     */
    fun getLibraries(): List<FileProposal> = files.filter { it.isLibrary() }

    /**
     * Returns all automation files in this proposal.
     */
    fun getAutomations(): List<FileProposal> = files.filter { it.isAutomation() }

    /**
     * Returns true if this proposal includes at least one library file.
     */
    fun hasLibrary(): Boolean = files.any { it.isLibrary() }

    /**
     * Returns true if this proposal contains only a single file.
     */
    fun isSingleFile(): Boolean = files.size == 1

    /**
     * Returns the primary file (the main automation, or first file if no automation).
     * Used for backwards compatibility and display purposes.
     */
    fun primaryFile(): FileProposal =
        getAutomations().firstOrNull() ?: files.first()

    /**
     * Backwards compatibility: returns the code of the primary file.
     */
    val code: String
        get() = primaryFile().code

    /**
     * Backwards compatibility: returns the filename of the primary file.
     */
    val filename: String
        get() = primaryFile().filename

    /**
     * Converts the primary automation to an Automation entity.
     * For backwards compatibility with single-file proposals.
     */
    fun toAutomation(): Automation {
        val automation = getAutomations().firstOrNull()
            ?: throw IllegalStateException("No automation file in proposal")
        return Automation.fromFilename(automation.filename, automation.code)
    }

    /**
     * Gets the automation ID that would be created from the primary automation.
     */
    fun automationId(): AutomationId {
        val automation = getAutomations().firstOrNull()
            ?: throw IllegalStateException("No automation file in proposal")
        val filename = if (automation.filename.endsWith(".star")) {
            automation.filename
        } else {
            "${automation.filename}.star"
        }
        return AutomationId.fromFilename(filename)
    }

    companion object {
        /**
         * Creates a proposal with a single automation file.
         * This is the traditional format for backwards compatibility.
         */
        fun singleAutomation(code: String, filename: String, summary: String): CodeProposal =
            CodeProposal(
                summary = summary,
                files = listOf(FileProposal.automation(code, filename))
            )

        /**
         * Creates a proposal with both a library and automation file.
         * Use this when creating reusable library functions.
         */
        fun withLibrary(
            libraryCode: String,
            libraryFilename: String,
            automationCode: String,
            automationFilename: String,
            summary: String
        ): CodeProposal = CodeProposal(
            summary = summary,
            files = listOf(
                FileProposal.library(libraryCode, libraryFilename),
                FileProposal.automation(automationCode, automationFilename)
            )
        )
    }
}
