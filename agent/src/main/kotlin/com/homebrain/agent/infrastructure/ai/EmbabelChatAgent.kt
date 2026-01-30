package com.homebrain.agent.infrastructure.ai

import com.embabel.agent.api.annotation.Action
import com.embabel.agent.api.annotation.AchievesGoal
import com.embabel.agent.api.annotation.Agent
import com.embabel.agent.api.common.Ai
import com.homebrain.agent.domain.conversation.ChatResponse
import com.homebrain.agent.domain.conversation.CodeProposal
import com.homebrain.agent.domain.conversation.FileProposal
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

/**
 * Code proposal from the LLM supporting multiple files.
 * When the LLM creates reusable library functions, it proposes both
 * the library and the automation together.
 */
data class EmbabelCodeProposal(
    val summary: String,
    val files: List<EmbabelFileProposal>
)

/**
 * A single file in a code proposal.
 */
data class EmbabelFileProposal(
    val code: String,
    val filename: String,
    val type: String  // "automation" or "library"
)

/**
 * Embabel-powered conversational chat agent.
 * 
 * This agent handles chat interactions about the smart home,
 * using LLM tools to query topics and automations, and optionally
 * generating automation code proposals.
 * 
 * The system prompt is loaded from resources/prompts/chat-system-prompt.md
 * to keep prompts separate from code for easier editing and version control.
 */
@Component
@Agent(description = "Conversational smart home assistant that can answer questions and generate automations")
class EmbabelChatAgent(
    private val mqttLlmTools: MqttLlmTools,
    private val promptLoader: PromptLoader
) {
    
    private val systemPrompt: String by lazy {
        promptLoader.load("chat-system-prompt.md")
    }

    @AchievesGoal(description = "Process user message and respond conversationally, optionally proposing automation code")
    @Action(description = "Chat with the user about their smart home or generate automation code")
    fun chat(request: ChatAgentRequest, ai: Ai): ChatResponse {
        logger.info { "Processing chat: ${request.message.take(50)}..." }

        val userPrompt = buildUserPrompt(request)

        // Use createObject to let Embabel handle tool loops and JSON extraction
        val embabelResponse = ai.withDefaultLlm()
            .withSystemPrompt(systemPrompt)
            .withToolObject(mqttLlmTools)
            .createObject(userPrompt, EmbabelChatResponse::class.java)

        // Convert to domain model
        return ChatResponse(
            message = embabelResponse.message,
            codeProposal = embabelResponse.codeProposal?.let { proposal ->
                CodeProposal(
                    summary = proposal.summary,
                    files = proposal.files.map { file ->
                        FileProposal(
                            code = file.code,
                            filename = file.filename,
                            type = FileProposal.FileType.fromString(file.type)
                        )
                    }
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
}
