package com.homebrain.agent.application

import com.embabel.agent.api.invocation.AgentInvocation
import com.embabel.agent.core.AgentPlatform
import com.homebrain.agent.domain.conversation.ChatResponse
import com.homebrain.agent.domain.conversation.Message
import com.homebrain.agent.infrastructure.ai.ChatAgentRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

/**
 * Use case for chat interactions.
 * 
 * Orchestrates the conversation flow, using the Embabel agent platform
 * to process user messages and generate responses.
 */
@Service
class ChatUseCase(
    private val agentPlatform: AgentPlatform
) {

    /**
     * Processes a user message and returns a response.
     * 
     * The response may include a code proposal if the user requested
     * an automation to be created.
     * 
     * @param message The user's message
     * @param conversationHistory Previous messages in the conversation
     * @return The chat response, optionally including a code proposal
     */
    fun chat(message: String, conversationHistory: List<Message>? = null): ChatResponse {
        logger.info { "Processing chat message: ${message.take(50)}..." }

        val request = ChatAgentRequest(
            message = message,
            conversationHistory = conversationHistory
        )

        val invocation = AgentInvocation.create(
            agentPlatform,
            ChatResponse::class.java
        )

        return invocation.invoke(request)
    }
}
