package com.homebrain.agent.application

import com.embabel.agent.api.invocation.AgentInvocation
import com.embabel.agent.core.AgentPlatform
import com.homebrain.agent.domain.conversation.ChatResponse
import com.homebrain.agent.domain.conversation.Message
import com.homebrain.agent.domain.planning.AutomationResponse
import com.homebrain.agent.domain.planning.UserInput
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

/**
 * Interface for invoking agents.
 * Allows mocking in tests without static mocking.
 */
interface AgentInvoker {
    fun <T : Any> invoke(platform: AgentPlatform, request: Any, responseType: Class<T>): T
}

/**
 * Default implementation using Embabel's AgentInvocation.
 */
class EmbabelAgentInvoker : AgentInvoker {
    override fun <T : Any> invoke(platform: AgentPlatform, request: Any, responseType: Class<T>): T {
        val invocation = AgentInvocation.create(platform, responseType)
        return invocation.invoke(request)
    }
}

/**
 * Use case for chat interactions.
 * 
 * Delegates to the GOAP-based HomebrainAgent for processing.
 * The agent uses Goal Oriented Action Planning to:
 * 1. Parse user intent
 * 2. Gather context (topics, similar code)
 * 3. Generate and validate code
 * 4. Respond with validated code or conversational answer
 * 
 * All orchestration logic (validation loops, code fixing) is now handled
 * by the GOAP planner, not this use case.
 */
@Service
class ChatUseCase(
    private val agentPlatform: AgentPlatform,
    private val agentInvoker: AgentInvoker = EmbabelAgentInvoker()
) {
    /**
     * Processes a user message and returns a response.
     * 
     * The GOAP planner automatically handles:
     * - Intent classification
     * - Context gathering
     * - Code generation and validation
     * - Retry loops for code fixing
     * - Conversational responses for questions
     * 
     * @param message The user's message
     * @param conversationHistory Previous messages in the conversation
     * @return The chat response, optionally including a validated code proposal
     */
    fun chat(message: String, conversationHistory: List<Message>? = null): ChatResponse {
        logger.info { "Processing chat message: ${message.take(50)}..." }

        // Create the GOAP entry point
        val userInput = UserInput(
            message = message,
            conversationHistory = conversationHistory
        )

        // Invoke the GOAP agent - all orchestration is handled by the planner
        val response = agentInvoker.invoke(
            agentPlatform,
            userInput,
            AutomationResponse::class.java
        )

        // Convert to the legacy ChatResponse for API compatibility
        return ChatResponse(
            message = response.message,
            codeProposal = response.codeProposal
        )
    }
}
