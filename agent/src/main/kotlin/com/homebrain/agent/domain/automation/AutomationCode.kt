package com.homebrain.agent.domain.automation

/**
 * Value object representing Starlark automation code.
 * Contains the raw code content that will be executed by the engine.
 */
@JvmInline
value class AutomationCode(val value: String) {
    init {
        require(value.isNotBlank()) { "Automation code cannot be blank" }
    }

    /**
     * Checks if the code appears to have a valid config block.
     * This is a basic syntactic check, not full validation (engine handles that).
     */
    fun hasConfigBlock(): Boolean = value.contains("config")

    /**
     * Checks if the code has an on_message handler.
     */
    fun hasMessageHandler(): Boolean = value.contains("def on_message")

    /**
     * Checks if the code has an on_schedule handler.
     */
    fun hasScheduleHandler(): Boolean = value.contains("def on_schedule")

    override fun toString(): String = value
}
