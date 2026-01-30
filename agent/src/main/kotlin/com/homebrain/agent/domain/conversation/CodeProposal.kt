package com.homebrain.agent.domain.conversation

import com.homebrain.agent.domain.automation.Automation
import com.homebrain.agent.domain.automation.AutomationId

/**
 * Value object representing a proposed automation that requires user confirmation.
 * 
 * When the LLM generates automation code, it's wrapped in a CodeProposal.
 * The user must confirm before it's deployed.
 */
data class CodeProposal(
    val code: String,
    val filename: String,
    val summary: String
) {
    init {
        require(code.isNotBlank()) { "Code cannot be blank" }
        require(filename.isNotBlank()) { "Filename cannot be blank" }
        require(summary.isNotBlank()) { "Summary cannot be blank" }
    }

    /**
     * Converts this proposal to an Automation entity.
     */
    fun toAutomation(): Automation = Automation.fromFilename(filename, code)

    /**
     * Gets the automation ID that would be created from this proposal.
     */
    fun automationId(): AutomationId = AutomationId.fromFilename(
        if (filename.endsWith(".star")) filename else "$filename.star"
    )
}
