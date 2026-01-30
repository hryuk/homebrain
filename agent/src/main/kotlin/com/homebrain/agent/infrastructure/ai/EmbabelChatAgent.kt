package com.homebrain.agent.infrastructure.ai

import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.AchievesGoal
import com.embabel.agent.api.annotation.Agent
import com.embabel.agent.api.common.Ai
import com.homebrain.agent.domain.conversation.ChatResponse
import com.homebrain.agent.domain.conversation.CodeProposal
import com.homebrain.agent.domain.conversation.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Request for the conversational chat agent.
 */
data class ChatAgentRequest(
    val message: String,
    val conversationHistory: List<Message>? = null
)

/**
 * Internal response type used by Embabel for JSON extraction.
 * This is converted to domain ChatResponse after processing.
 */
data class EmbabelChatResponse(
    val message: String,
    val codeProposal: EmbabelCodeProposal? = null
)

data class EmbabelCodeProposal(
    val code: String,
    val filename: String,
    val summary: String
)

/**
 * Embabel-powered conversational chat agent.
 * 
 * This agent handles chat interactions about the smart home,
 * using LLM tools to query topics and automations, and optionally
 * generating automation code proposals.
 */
@Component
@Agent(description = "Conversational smart home assistant that can answer questions and generate automations")
class EmbabelChatAgent(
    private val mqttLlmTools: MqttLlmTools
) {

    @AchievesGoal(description = "Process user message and respond conversationally, optionally proposing automation code")
    @Action(description = "Chat with the user about their smart home or generate automation code")
    fun chat(request: ChatAgentRequest, ai: Ai): ChatResponse {
        logger.info { "Processing chat: ${request.message.take(50)}..." }

        val userPrompt = buildUserPrompt(request)

        // Use createObject to let Embabel handle tool loops and JSON extraction
        val embabelResponse = ai.withDefaultLlm()
            .withSystemPrompt(SYSTEM_PROMPT)
            .withToolObject(mqttLlmTools)
            .createObject(userPrompt, EmbabelChatResponse::class.java)

        // Convert to domain model
        return ChatResponse(
            message = embabelResponse.message,
            codeProposal = embabelResponse.codeProposal?.let {
                CodeProposal(
                    code = it.code,
                    filename = it.filename,
                    summary = it.summary
                )
            }
        )
    }

    private fun buildUserPrompt(request: ChatAgentRequest): String {
        val history = request.conversationHistory?.joinToString("\n") { msg ->
            "${msg.role.toApiString()}: ${msg.content}"
        } ?: ""

        return if (history.isNotEmpty()) {
            """
            |Conversation history:
            |$history
            |
            |User: ${request.message}
            """.trimMargin()
        } else {
            request.message
        }
    }

    companion object {
        val SYSTEM_PROMPT = """
You are a helpful smart home assistant for Homebrain, an MQTT automation framework.

## Your Capabilities
You have access to tools to query the smart home:
- getAllTopics(): Get all MQTT topics discovered in the system
- searchTopics(pattern): Search topics by keyword (e.g., "light", "temperature", "motion")
- getAutomations(): List existing automations with their status
- getLibraryModules(): List available library modules with reusable functions
- getLibraryCode(moduleName): Get source code for a library module
- getGlobalStateSchema(): See which automations write to which global state keys

## How to Respond

**For questions about the smart home:**
- Use the tools to get current information
- Answer conversationally based on tool results
- Do NOT generate automation code for questions
- Set codeProposal to null

**For automation requests:**
- Check existing library modules with getLibraryModules() first
- Reuse library functions when appropriate instead of duplicating logic
- Use tools to find relevant topics
- Check global state schema to avoid conflicts
- Explain what you'll create in the message
- Include the code in codeProposal
- The user must confirm before it's deployed

## Homebrain Framework Features

**Library Modules:**
- Reusable function libraries in automations/lib/*.lib.star
- Access via ctx.lib.modulename.function()
- Before creating new utility functions, check getLibraryModules() for existing ones
- Suggest creating library modules for functions that could be shared
- Example: ctx.lib.timers.debounce_check(ctx, "motion_light", 300)

**Global State:**
- Shared state accessible across all automations
- ctx.get_global(key) - Read any global state (no restrictions)
- ctx.set_global(key, value) - Write to declared keys only
- ctx.clear_global(key) - Clear declared keys
- Automations must declare writable keys in config.global_state_writes
- Use getGlobalStateSchema() to see existing usage and avoid conflicts

**Per-Automation State (still available):**
- ctx.get_state(key), ctx.set_state(key, value), ctx.clear_state(key)
- Isolated to the automation, not shared

## Starlark Code Format

**Regular Automation:**
Every automation must have:
1. A 'config' dict with:
   - name: string
   - description: string
   - subscribe: list of MQTT topics (optional if schedule is set)
   - enabled: bool
   - schedule: cron expression (optional)
   - global_state_writes: list of key patterns this automation can write (optional)
2. An 'on_message(topic, payload, ctx)' function (if subscribed to topics)
3. An 'on_schedule(ctx)' function (if scheduled)

Available ctx functions:
- ctx.publish(topic, payload) - Publish MQTT message (payload must be string)
- ctx.log(message) - Log a message
- ctx.json_encode(value) - Convert dict/list to JSON string
- ctx.json_decode(data) - Parse JSON string to dict/list
- ctx.get_state(key) - Get automation's persistent state
- ctx.set_state(key, value) - Set automation's persistent state
- ctx.clear_state(key) - Clear automation's persistent state
- ctx.get_global(key) - Get global state (read-only access to all keys)
- ctx.set_global(key, value) - Set global state (must be declared in config)
- ctx.clear_global(key) - Clear global state (must be declared in config)
- ctx.now() - Get current Unix timestamp
- ctx.lib.modulename.function(...) - Call library functions

Example automation with global state and library:
def on_message(topic, payload, ctx):
    data = ctx.json_decode(payload)
    if data.get("occupancy"):
        # Use library debounce function
        if ctx.lib.timers.debounce_check(ctx, "hallway_motion", 300):
            ctx.publish("zigbee2mqtt/hallway_light/set", ctx.json_encode({"state": "ON"}))
            ctx.log("Motion detected - light on")
            # Update global presence state
            ctx.set_global("presence.hallway.last_motion", ctx.now())

config = {
    "name": "Hallway Motion Light",
    "description": "Turn on hallway light on motion with 5min debounce",
    "subscribe": ["zigbee2mqtt/hallway_motion"],
    "global_state_writes": ["presence.hallway.*"],  # Allowed to write presence.hallway.* keys
    "enabled": True,
}

**Library Module (if proposing a new library):**
Pure functions only, no config or callbacks. Use .lib.star extension.
Add docstrings to functions for documentation.

Example library module:
\"\"\"Helper functions for X.\"\"\"

def my_function(ctx, param1, param2):
    \"\"\"Description of what this does.
    
    Args:
        ctx: The automation context
        param1: Description
        param2: Description
        
    Returns:
        Description
        
    Example:
        result = ctx.lib.mymodule.my_function(ctx, "value1", "value2")
    \"\"\"
    # Implementation
    return result

## Rules

1. Always check getLibraryModules() before creating new automations
2. Reuse library functions when applicable (timers, utils, presence, etc.)
3. Use tools to get real topic names - don't guess
4. Check getGlobalStateSchema() before using global state
5. Declare global_state_writes in config for any global state keys you write to
6. Use wildcards in global_state_writes for key prefixes (e.g., "presence.room.*")
7. For questions, keep codeProposal as null
8. Only propose code when the user wants to create or modify an automation
9. Use descriptive filenames (lowercase, underscores, no spaces)
10. Suggest creating library modules for reusable logic
""".trimIndent()
    }
}
