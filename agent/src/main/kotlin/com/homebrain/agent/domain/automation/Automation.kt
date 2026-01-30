package com.homebrain.agent.domain.automation

import com.homebrain.agent.domain.commit.Commit

/**
 * Aggregate root representing an automation in the Homebrain system.
 * 
 * An automation is a Starlark script that subscribes to MQTT topics
 * and executes logic when messages are received or on a schedule.
 */
data class Automation(
    val id: AutomationId,
    val code: AutomationCode,
    val commit: Commit? = null
) {
    /**
     * Returns the filename for this automation.
     */
    fun toFilename(): String = id.toFilename()

    /**
     * Creates an updated version of this automation with new code.
     */
    fun updateCode(newCode: AutomationCode): Automation = copy(code = newCode)

    /**
     * Creates a version of this automation with an associated commit.
     */
    fun withCommit(commit: Commit): Automation = copy(commit = commit)

    companion object {
        /**
         * Creates a new Automation from a filename (with or without .star extension) and code content.
         */
        fun fromFilename(filename: String, code: String): Automation {
            return Automation(
                id = AutomationId.fromFilename(filename),
                code = AutomationCode(code)
            )
        }

        /**
         * Creates a new Automation from an id string and code content.
         */
        fun fromId(id: String, code: String): Automation {
            return Automation(
                id = AutomationId(id),
                code = AutomationCode(code)
            )
        }
    }
}
