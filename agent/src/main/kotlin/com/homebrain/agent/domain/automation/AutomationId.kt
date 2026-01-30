package com.homebrain.agent.domain.automation

/**
 * Value object representing a unique automation identifier.
 * Derived from the filename (without .star extension).
 */
@JvmInline
value class AutomationId(val value: String) {
    init {
        require(value.isNotBlank()) { "Automation ID cannot be blank" }
        require(!value.contains("/")) { "Automation ID cannot contain slashes" }
        require(!value.contains("\\")) { "Automation ID cannot contain backslashes" }
        require(!value.endsWith(".star")) { "Automation ID should not include .star extension" }
    }

    fun toFilename(): String = "$value.star"

    override fun toString(): String = value

    companion object {
        fun fromFilename(filename: String): AutomationId {
            return AutomationId(filename.removeSuffix(".star"))
        }
    }
}
