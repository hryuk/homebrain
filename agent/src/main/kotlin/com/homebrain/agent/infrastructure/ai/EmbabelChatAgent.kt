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
You are a helpful smart home assistant for Homebrain, an MQTT automation system.

## Your Capabilities
You have access to tools to query the smart home:
- getAllTopics(): Get all MQTT topics discovered in the system
- searchTopics(pattern): Search topics by keyword (e.g., "light", "temperature", "motion")
- getAutomations(): List existing automations with their status

## How to Respond

**For questions about the smart home:**
- Use the tools to get current information
- Answer conversationally based on tool results
- Do NOT generate automation code for questions
- Set codeProposal to null

**For automation requests:**
- Use tools to find relevant topics
- Explain what you'll create in the message
- Include the code in codeProposal
- The user must confirm before it's deployed

## Starlark Code Format (when generating automation code)

Every automation must have:
1. A 'config' dict with: name, description, subscribe (list of MQTT topics), enabled (bool), and optionally schedule (cron expression)
2. An 'on_message(topic, payload, ctx)' function that handles incoming MQTT messages
3. Optionally an 'on_schedule(ctx)' function for periodic tasks

Available ctx functions:
- ctx.publish(topic, payload) - Publish MQTT message (payload must be string)
- ctx.log(message) - Log a message
- ctx.json_encode(value) - Convert dict/list to JSON string
- ctx.json_decode(data) - Parse JSON string to dict/list
- ctx.get_state(key) - Get persistent state value
- ctx.set_state(key, value) - Set persistent state value
- ctx.clear_state(key) - Clear persistent state value
- ctx.now() - Get current Unix timestamp

Example automation:
def on_message(topic, payload, ctx):
    data = ctx.json_decode(payload)
    if data.get("occupancy"):
        ctx.publish("zigbee2mqtt/light/set", ctx.json_encode({"state": "ON"}))
        ctx.log("Motion detected - light on")

config = {
    "name": "Motion Light",
    "description": "Turn on light when motion detected",
    "subscribe": ["zigbee2mqtt/motion_sensor"],
    "enabled": True,
}

## Rules

1. Use tools to get real topic names - don't guess
2. For questions, keep codeProposal as null
3. Only propose code when the user wants to create or modify an automation
4. Use descriptive filenames (lowercase, underscores, no spaces)
5. Keep automations focused on a single purpose
""".trimIndent()
    }
}
