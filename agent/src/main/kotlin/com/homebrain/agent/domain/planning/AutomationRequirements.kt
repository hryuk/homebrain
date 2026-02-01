package com.homebrain.agent.domain.planning

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonPropertyDescription

/**
 * Extracted requirements for the automation to be generated.
 * 
 * This captures what the user wants in a structured format that can be
 * used to search for similar code and generate the automation.
 */
@JsonClassDescription("Requirements for the automation to be generated")
data class AutomationRequirements(
    @get:JsonPropertyDescription("Natural language description of what the automation should do")
    val description: String,
    
    @get:JsonPropertyDescription("MQTT topics that should trigger this automation (e.g., 'zigbee2mqtt/motion_sensor')")
    val triggers: List<String>,
    
    @get:JsonPropertyDescription("Actions the automation should take (e.g., 'turn on kitchen light')")
    val actions: List<String>,
    
    @get:JsonPropertyDescription("Optional conditions for when the automation should run")
    val conditions: List<String>? = null,
    
    @get:JsonPropertyDescription("Suggested name for the automation file (snake_case, no extension)")
    val suggestedName: String,
    
    @get:JsonPropertyDescription("Whether this automation needs a cron schedule")
    val needsSchedule: Boolean = false,
    
    @get:JsonPropertyDescription("Cron expression if needed (e.g., '0 8 * * *' for 8am daily)")
    val schedule: String? = null,
    
    @get:JsonPropertyDescription("Global state keys this automation needs to write to")
    val globalStateWrites: List<String>? = null
) {
    
    init {
        require(description.isNotBlank()) { "Description cannot be blank" }
        require(triggers.isNotEmpty() || needsSchedule) { "Must have triggers or a schedule" }
        require(actions.isNotEmpty()) { "Must have at least one action" }
        require(suggestedName.isNotBlank()) { "Suggested name cannot be blank" }
    }
    
    /**
     * Convert to a prompt-friendly string.
     */
    fun toPromptString(): String = buildString {
        appendLine("Automation Requirements:")
        appendLine("- Description: $description")
        appendLine("- Triggers: ${triggers.joinToString(", ")}")
        appendLine("- Actions: ${actions.joinToString(", ")}")
        if (!conditions.isNullOrEmpty()) {
            appendLine("- Conditions: ${conditions.joinToString(", ")}")
        }
        if (needsSchedule && schedule != null) {
            appendLine("- Schedule: $schedule")
        }
        if (!globalStateWrites.isNullOrEmpty()) {
            appendLine("- Global state writes: ${globalStateWrites.joinToString(", ")}")
        }
    }
    
    companion object {
        /**
         * Create basic requirements for a trigger-action automation.
         */
        fun triggerAction(
            description: String,
            trigger: String,
            action: String,
            name: String
        ) = AutomationRequirements(
            description = description,
            triggers = listOf(trigger),
            actions = listOf(action),
            suggestedName = name
        )
        
        /**
         * Create requirements for a scheduled automation.
         */
        fun scheduled(
            description: String,
            schedule: String,
            actions: List<String>,
            name: String
        ) = AutomationRequirements(
            description = description,
            triggers = emptyList(),
            actions = actions,
            suggestedName = name,
            needsSchedule = true,
            schedule = schedule
        )
    }
}
